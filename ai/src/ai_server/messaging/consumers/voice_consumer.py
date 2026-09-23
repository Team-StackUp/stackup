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
from ai_server.voice.stt.base import SttError, SttProvider, TranscriptionResult

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
        stt_max_attempts: int = 3,
        stt_retry_backoff_sec: float = 0.5,
    ) -> None:
        self._stt = stt
        self._storage = storage
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._filler_pattern = filler_pattern
        self._core_client = core_client
        self._stt_max_attempts = max(1, stt_max_attempts)
        self._stt_retry_backoff_sec = stt_retry_backoff_sec

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

                try:
                    result = await self._transcribe_with_retry(
                        req, envelope, audio_bytes
                    )
                except SttError as exc:
                    log.error(
                        "voice.stt.failed",
                        error=str(exc),
                        code=exc.code,
                        session_id=req.session_id,
                        attempts=getattr(exc, "attempts", 1),
                    )
                    await self._publish_failed(envelope, req, code=exc.code)
                    return
                except Exception as exc:
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

    async def _transcribe_with_retry(
        self, req: AnalyzeVoiceRequest, envelope, audio_bytes: bytes
    ) -> TranscriptionResult:
        """일시 장애(retriable)면 지수 백오프로 재시도.

        Deepgram 호출은 간헐적으로 응답 없이 멎는다 — 운영 실패 2건이 60.8s/61.5s 로
        read 한도에 정확히 걸렸고, 성공 호출은 p50 3.6초였다. 멎은 뒤 같은 오디오를
        재전송하면 대부분 즉시 전사되므로(실패 파일 6개 중 5개가 재전송에서 성공),
        한 번의 딸꾹질이 사용자에게 영구 실패로 보이지 않도록 여기서 흡수한다.

        비재시도(STT_AUTH_FAILED, STT_BAD_REQUEST)는 재전송해도 같은 결과라 즉시 올린다.
        예상 못 한 예외는 코드 버그일 가능성이 높아 재시도로 지연만 늘리므로 그대로 올린다.
        `ai_request_logs` 에는 시도마다 한 행씩 남는다 — 재시도율을 보려면 error_message
        앞의 `[n/N]` 을 본다.
        """
        for attempt in range(1, self._stt_max_attempts + 1):
            started = time.perf_counter()
            try:
                result = await self._stt.transcribe(
                    audio_bytes=audio_bytes,
                    content_type=req.content_type,
                    hint=req.previous_question_text,
                )
            except SttError as exc:
                self._record_stt_log(
                    req,
                    envelope,
                    latency_ms=_elapsed_ms(started),
                    status="FAILED",
                    error_message=f"[{attempt}/{self._stt_max_attempts}] {exc.message}",
                )
                if not exc.retriable or attempt == self._stt_max_attempts:
                    exc.attempts = attempt  # 최종 실패 로그가 실제 시도 횟수를 싣도록
                    raise
                backoff = self._stt_retry_backoff_sec * (2 ** (attempt - 1))
                log.warn(
                    "voice.stt.retry",
                    code=exc.code,
                    attempt=attempt,
                    max_attempts=self._stt_max_attempts,
                    backoff_sec=backoff,
                    session_id=req.session_id,
                )
                await asyncio.sleep(backoff)
                continue
            except Exception as exc:
                self._record_stt_log(
                    req,
                    envelope,
                    latency_ms=_elapsed_ms(started),
                    status="FAILED",
                    error_message=str(exc),
                )
                raise

            self._record_stt_log(
                req,
                envelope,
                latency_ms=_elapsed_ms(started),
                status="SUCCESS",
                error_message=None,
            )
            return result

        raise AssertionError("도달 불가 — 마지막 시도는 항상 raise 한다")

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
