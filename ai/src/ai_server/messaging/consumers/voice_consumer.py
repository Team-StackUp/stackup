from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

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
    ) -> None:
        self._stt = stt
        self._storage = storage
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._filler_pattern = filler_pattern

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[AnalyzeVoiceRequest].model_validate_json(message.body)
            except Exception as exc:
                log.error("voice.parse.failed", error=str(exc), delivery_tag=message.delivery_tag)
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info("voice.idempotent.skip", message_id=envelope.message_id)
                return

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
                log.error("voice.storage.failed", error=str(exc), key=req.audio_s3_key)
                await self._publish_failed(envelope, req, code="AUDIO_FETCH_FAILED")
                return

            try:
                result = await self._stt.transcribe(
                    audio_bytes=audio_bytes,
                    content_type=req.content_type,
                    hint=req.previous_question_text,
                )
            except SttError as exc:
                log.error(
                    "voice.stt.failed",
                    error=str(exc),
                    code=exc.code,
                    session_id=req.session_id,
                )
                await self._publish_failed(envelope, req, code=exc.code)
                return
            except Exception as exc:
                log.error("voice.stt.unexpected", error=str(exc), session_id=req.session_id)
                await self._publish_failed(envelope, req, code="TRANSCRIPTION_FAILED")
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
            await self._publisher.publish(
                routing_key=self._callback_routing_key,
                message_type="callback.voice",
                payload=payload,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )
            log.info(
                "voice.analyze.done",
                message_id=envelope.message_id,
                session_id=req.session_id,
                interview_message_id=req.message_id,
                wpm=metrics.speaking_rate_wpm,
                trace_id=envelope.trace_id,
            )

    async def _publish_failed(self, envelope, req: AnalyzeVoiceRequest, *, code: str) -> None:
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
        await self._publisher.publish(
            routing_key=self._callback_routing_key,
            message_type="callback.voice",
            payload=payload,
            trace_id=envelope.trace_id,
            correlation_id=envelope.message_id,
            context=envelope.context,
        )
