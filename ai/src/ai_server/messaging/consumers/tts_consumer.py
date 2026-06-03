from __future__ import annotations

import re

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.tts import GenerateTtsRequest, TtsCallbackPayload
from ai_server.storage.base import ObjectStorage
from ai_server.voice.tts.base import TtsError, TtsProvider

log = structlog.get_logger(__name__)


class TtsConsumer:
    """generate.tts consumer — 질문 텍스트를 TTS 합성 → S3 PUT → callback.tts 발행."""

    def __init__(
        self,
        *,
        tts: TtsProvider,
        storage: ObjectStorage,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
        voice: str,
        key_template: str,
    ) -> None:
        self._tts = tts
        self._storage = storage
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._voice = voice
        self._key_template = key_template

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[GenerateTtsRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "tts.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info("tts.idempotent.skip", message_id=envelope.message_id)
                return

            req = envelope.payload
            log.info(
                "tts.synthesize.start",
                message_id=envelope.message_id,
                session_id=req.session_id,
                interview_message_id=req.message_id,
                trace_id=envelope.trace_id,
            )

            try:
                result = await self._tts.synthesize(req.text, voice=self._voice)
            except TtsError as exc:
                log.error(
                    "tts.synthesize.failed",
                    code=exc.code,
                    error=exc.message,
                    session_id=req.session_id,
                )
                await self._publish(
                    envelope,
                    TtsCallbackPayload(
                        session_id=req.session_id,
                        message_id=req.message_id,
                        status="FAILED",
                        error_code=exc.code,
                    ),
                )
                return
            except Exception as exc:
                log.error(
                    "tts.synthesize.unexpected",
                    error=str(exc),
                    session_id=req.session_id,
                )
                await self._publish(
                    envelope,
                    TtsCallbackPayload(
                        session_id=req.session_id,
                        message_id=req.message_id,
                        status="FAILED",
                        error_code="TTS_FAILED",
                    ),
                )
                return

            key = self._build_key(req.session_id, req.message_id, result.content_type)
            try:
                await self._storage.put_bytes(
                    key, result.audio_bytes, content_type=result.content_type
                )
            except Exception as exc:
                log.error("tts.storage.failed", error=str(exc), key=key)
                await self._publish(
                    envelope,
                    TtsCallbackPayload(
                        session_id=req.session_id,
                        message_id=req.message_id,
                        status="FAILED",
                        error_code="TTS_STORAGE_FAILED",
                    ),
                )
                return

            await self._publish(
                envelope,
                TtsCallbackPayload(
                    session_id=req.session_id,
                    message_id=req.message_id,
                    status="SUCCEEDED",
                    audio_key=key,
                    duration_sec=result.duration_sec,
                ),
            )
            log.info(
                "tts.synthesize.done",
                session_id=req.session_id,
                interview_message_id=req.message_id,
                key=key,
            )

    _EXT_BY_CONTENT_TYPE = {
        "audio/wav": "wav",
        "audio/mpeg": "mp3",
        "audio/ogg": "ogg",
        "audio/mp4": "m4a",
    }

    def _build_key(self, session_id: int, message_id: int, content_type: str) -> str:
        # 템플릿의 확장자를 실제 오디오 포맷(content_type)에 맞춰 교체한다.
        # (Gemini=WAV, OpenAI=mp3 등 공급자마다 다름 → Core 프록시가 확장자로 content-type 판단)
        base = self._key_template.format(session_id=session_id, message_id=message_id)
        ext = self._EXT_BY_CONTENT_TYPE.get(content_type.split(";")[0].strip(), "mp3")
        return re.sub(r"\.[^./]+$", "", base) + "." + ext

    async def _publish(self, envelope, payload: TtsCallbackPayload) -> None:
        await self._publisher.publish(
            routing_key=self._callback_routing_key,
            message_type="callback.tts",
            payload=payload,
            trace_id=envelope.trace_id,
            correlation_id=envelope.message_id,
            context=envelope.context,
        )
