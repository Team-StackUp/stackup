from __future__ import annotations

import asyncio
import time

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.core.client import CoreClient
from ai_server.messaging.consumers.failure_signal import unmark_on_error
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.voice import (
    AnalyzeVoiceRequest,
    VoiceCallbackPayload,
)
from ai_server.storage.base import ObjectStorage
from ai_server.voice.analysis.metrics import analyze
from ai_server.voice.stt.base import SttError, SttProvider

log = structlog.get_logger(__name__)


class VoiceConsumer:
    """analyze.voice consumer (음성 답변 STT + 정량 분석).

    흐름:
      1. envelope parse + 멱등 체크
      2. S3 GET (audio_s3_key)
      3. STT → TranscriptionResult
      4. 음성 분석 → VoiceMetrics
      5. callback.voice 발행 (성공: transcript + metrics, 실패: error_code)
    """

    def __init__(
        self,
        *,
        stt: SttProvider,
        storage: ObjectStorage,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
        filler_pattern: str,
        core_client: CoreClient | None = None,
    ) -> None:
        self._stt = stt
        self._storage = storage
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._filler_pattern = filler_pattern
        self._core_client = core_client

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[AnalyzeVoiceRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "voice.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info("voice.idempotent.skip", message_id=envelope.message_id)
                return

            # 마킹 이후 어떤 예외든 unmark — 콜백 0건 DLQ 재주입 삼킴 방지 (F6).
            async with unmark_on_error(self._idempotency, envelope.message_id):
                req = envelope.payload
                log.info(
                    "voice.analyze.start",
                    message_id=envelope.message_id,
                    session_id=req.session_id,
                    interview_message_id=req.message_id,
                    key=req.audio_s3_key,
                    trace_id=envelope.trace_id,
                )

                try:
                    audio_bytes = await self._storage.get_bytes(req.audio_s3_key)
                except Exception as exc:
                    log.error(
                        "voice.storage.failed", error=str(exc), key=req.audio_s3_key
                    )
                    await self._publish_failed(envelope, req, code="AUDIO_FETCH_FAILED")
                    return

                started = None
                try:
                    started = time.perf_counter()
                    result = await self._stt.transcribe(
                        audio_bytes=audio_bytes,
                        content_type=req.content_type,
                        hint=req.previous_question_text,
                    )
                    self._record_stt_log(
                        req,
                        envelope,
                        latency_ms=_elapsed_ms(started),
                        status="SUCCESS",
                        error_message=None,
                    )
                except SttError as exc:
                    self._record_stt_log(
                        req,
                        envelope,
                        latency_ms=_elapsed_ms(started),
                        status="FAILED",
                        error_message=exc.message,
                    )
                    log.error(
                        "voice.stt.failed",
                        error=str(exc),
                        code=exc.code,
                        session_id=req.session_id,
                    )
                    await self._publish_failed(envelope, req, code=exc.code)
                    return
                except Exception as exc:
                    self._record_stt_log(
                        req,
                        envelope,
                        latency_ms=_elapsed_ms(started),
                        status="FAILED",
                        error_message=str(exc),
                    )
                    log.error(
                        "voice.stt.unexpected",
                        error=str(exc),
                        session_id=req.session_id,
                    )
                    await self._publish_failed(
                        envelope, req, code="TRANSCRIPTION_FAILED"
                    )
                    return

                metrics = analyze(result, filler_pattern=self._filler_pattern)

                payload = VoiceCallbackPayload(
                    session_id=req.session_id,
                    interview_message_id=req.message_id,
                    transcript=result.text,
                    speaking_rate_wpm=metrics.speaking_rate_wpm,
                    silence_duration_sec=metrics.silence_duration_sec,
                    filler_word_counts=metrics.filler_word_counts,
                    pronunciation_accuracy=metrics.pronunciation_accuracy,
                    error_code=None,
                )
                await self._publish_callback(envelope, payload)
                log.info(
                    "voice.analyze.done",
                    message_id=envelope.message_id,
                    session_id=req.session_id,
                    interview_message_id=req.message_id,
                    wpm=metrics.speaking_rate_wpm,
                    trace_id=envelope.trace_id,
                )

    async def _publish_callback(self, envelope, payload: VoiceCallbackPayload) -> None:
        await self._publisher.publish(
            routing_key=self._callback_routing_key,
            message_type="callback.voice",
            payload=payload,
            trace_id=envelope.trace_id,
            correlation_id=envelope.message_id,
            context=envelope.context,
        )

    async def _publish_failed(
        self, envelope, req: AnalyzeVoiceRequest, *, code: str
    ) -> None:
        payload = VoiceCallbackPayload(
            session_id=req.session_id,
            interview_message_id=req.message_id,
            transcript=None,
            speaking_rate_wpm=None,
            silence_duration_sec=None,
            filler_word_counts={},
            pronunciation_accuracy=None,
            error_code=code,
        )
        await self._publish_callback(envelope, payload)

    def _record_stt_log(
        self,
        req: AnalyzeVoiceRequest,
        envelope,
        *,
        latency_ms: int | None,
        status: str,
        error_message: str | None,
    ) -> None:
        if self._core_client is None:
            return

        async def _do() -> None:
            try:
                await self._core_client.record_ai_log(
                    request_type="stt.transcribe",
                    model_name=_provider_model_name(self._stt),
                    input_tokens=None,
                    output_tokens=None,
                    latency_ms=latency_ms,
                    status=status,
                    user_id=envelope.context.user_id,
                    session_id=req.session_id,
                    error_message=(error_message[:1000] if error_message else None),
                )
            except Exception as exc:
                log.warn(
                    "voice.ai_log.failed", error=str(exc), session_id=req.session_id
                )

        asyncio.create_task(_do())


def _elapsed_ms(started: float | None) -> int | None:
    if started is None:
        return None
    return int((time.perf_counter() - started) * 1000)


def _provider_model_name(provider) -> str | None:
    value = getattr(provider, "model_name", None)
    if isinstance(value, str):
        return value
    return None
