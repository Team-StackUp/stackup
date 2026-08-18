from __future__ import annotations

import asyncio
import re

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.sentence_split import next_sentences
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.messaging.session_notify import SessionRealtimeNotifier
from ai_server.model.envelope import Envelope
from ai_server.model.messages.tts import GenerateTtsRequest, TtsCallbackPayload
from ai_server.storage.base import ObjectStorage
from ai_server.voice.tts.audio_join import join_audio
from ai_server.voice.tts.base import TtsError, TtsProvider, TtsResult

log = structlog.get_logger(__name__)

# 다음 문장(들)을 미리 합성해 발화 중간 공백을 줄인다. 게이트웨이 문장당 지연이
# 재생 길이와 비슷해 순차 합성 시 짧은 문장 뒤 무음이 생길 수 있어 소폭 선합성한다.
# 과도한 동시 호출(429)을 피해 보수적으로 2로 둔다(꼬리질문의 무제한 병렬보다 안전).
_TTS_LOOKAHEAD = 2


def _split_all_sentences(text: str) -> list[str]:
    """완성된 텍스트를 문장 단위로 모두 쪼갠다(스트리밍과 달리 마지막 문장도 포함)."""
    sents, consumed = next_sentences(text, 0)
    tail = text[consumed:].strip()
    if tail:
        sents.append(tail)
    cleaned = [s for s in sents if s.strip()]
    if cleaned:
        return cleaned
    stripped = text.strip()
    return [stripped] if stripped else []


class TtsConsumer:
    """generate.tts consumer — 질문 텍스트를 TTS 합성 → S3 PUT → callback.tts 발행.

    session_notifier 가 주입되면 문장 단위 세그먼트로 합성·발행해(SESSION_MESSAGE_AUDIO)
    첫 문장부터 즉시 재생되게 하고, 합성된 세그먼트를 이어붙여 전체 파일도 저장한다(다시 듣기용).
    notifier 가 없으면 기존처럼 전체 텍스트를 한 번에 합성한다.
    """

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
        session_notifier: SessionRealtimeNotifier | None = None,
    ) -> None:
        self._tts = tts
        self._storage = storage
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._voice = voice
        self._key_template = key_template
        self._notifier = session_notifier

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
                streaming=self._notifier is not None,
                trace_id=envelope.trace_id,
            )

            if self._notifier is not None:
                result = await self._synthesize_segmented(envelope, req)
            else:
                result = await self._synthesize_once(envelope, req)
            if result is None:
                return  # 실패 — 콜백은 내부에서 이미 발행

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

    async def _synthesize_once(
        self, envelope: Envelope[GenerateTtsRequest], req: GenerateTtsRequest
    ) -> TtsResult | None:
        """전체 텍스트를 한 번에 합성(레거시/notifier 미주입 경로). 실패 시 콜백 발행 후 None."""
        try:
            return await self._tts.synthesize(req.text, voice=self._voice)
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
            return None
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
            return None

    async def _synthesize_segmented(
        self, envelope: Envelope[GenerateTtsRequest], req: GenerateTtsRequest
    ) -> TtsResult | None:
        """문장 단위로 합성 → 세그먼트 PUT + SESSION_MESSAGE_AUDIO 즉시 발행.

        다음 문장을 미리 합성(_TTS_LOOKAHEAD 만큼 선합성)하되, 발행은 문장 순서대로 한다.
        seq 는 '성공한' 세그먼트만 0..n 연속 부여해 프론트 큐가 빈 seq 에서 멈추지 않게 한다.
        합성된 세그먼트를 이어붙여 전체 파일(다시 듣기용)을 반환한다. 전부 실패하면 콜백 발행 후 None.
        """
        sentences = _split_all_sentences(req.text)
        if not sentences:
            await self._publish(
                envelope,
                TtsCallbackPayload(
                    session_id=req.session_id,
                    message_id=req.message_id,
                    status="FAILED",
                    error_code="TTS_EMPTY_TEXT",
                ),
            )
            return None

        # 동시 합성을 _TTS_LOOKAHEAD 개로 제한하면서 모든 문장을 미리 시작한다.
        # 발행은 문장 순서대로 await 하므로 재생 순서는 읽기 순서와 동일하다.
        sem = asyncio.Semaphore(_TTS_LOOKAHEAD)

        async def _synth(sentence: str) -> TtsResult:
            async with sem:
                return await self._tts.synthesize(sentence, voice=self._voice)

        synth_tasks = [asyncio.create_task(_synth(s)) for s in sentences]

        parts: list[TtsResult] = []
        seq = 0
        for task in synth_tasks:
            try:
                res = await task
            except Exception as exc:  # noqa: BLE001 — 한 문장 실패가 전체를 막지 않음
                log.warning(
                    "tts.segment.failed",
                    session_id=req.session_id,
                    interview_message_id=req.message_id,
                    seq=seq,
                    error=str(exc),
                )
                continue
            ext = self._EXT_BY_CONTENT_TYPE.get(
                res.content_type.split(";")[0].strip(), "mp3"
            )
            seg_key = f"interview/tts/{req.session_id}/{req.message_id}/seg-{seq}.{ext}"
            try:
                await self._storage.put_bytes(
                    seg_key, res.audio_bytes, content_type=res.content_type
                )
            except Exception as exc:  # noqa: BLE001
                log.warning(
                    "tts.segment.storage_failed",
                    key=seg_key,
                    error=str(exc),
                )
                continue
            await self._notifier.emit_audio(
                session_id=req.session_id,
                message_id=req.message_id,
                seq=seq,
                ext=ext,
                duration_sec=res.duration_sec,
                trace_id=envelope.trace_id,
            )
            parts.append(res)
            seq += 1

        if not parts:
            await self._publish(
                envelope,
                TtsCallbackPayload(
                    session_id=req.session_id,
                    message_id=req.message_id,
                    status="FAILED",
                    error_code="TTS_FAILED",
                ),
            )
            return None
        return join_audio(parts)

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
