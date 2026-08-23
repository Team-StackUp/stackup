from __future__ import annotations

import asyncio

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.followup_generation_chain import (
    FollowupGenerator,
    FollowupResult,
    StreamingFollowupGenerator,
)
from ai_server.chain.sentence_split import next_sentences
from ai_server.core.client import CoreClient
from ai_server.messaging.consumers.failure_signal import (
    classify_failure,
    consume_with_failure_signal,
    format_error_message,
)
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.messaging.session_notify import SessionRealtimeNotifier
from ai_server.model.envelope import Envelope
from ai_server.model.messages.followup import (
    FollowupCallbackPayload,
    GenerateFollowupRequest,
)
from ai_server.rag.embedder import EmbeddingProvider

log = structlog.get_logger(__name__)

_EXT_BY_CONTENT_TYPE = {
    "audio/wav": "wav",
    "audio/mpeg": "mp3",
    "audio/ogg": "ogg",
    "audio/mp4": "m4a",
}


class FollowupConsumer:
    def __init__(
        self,
        *,
        generator: FollowupGenerator,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
        core_client: CoreClient | None = None,
        embedder: EmbeddingProvider | None = None,
        rag_top_k: int = 5,
        streaming_generator: StreamingFollowupGenerator | None = None,
        session_notifier: SessionRealtimeNotifier | None = None,
        tts=None,
        storage=None,
        tts_voice: str = "",
        rag_timeout_sec: float = 1.5,
    ) -> None:
        self._generator = generator
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._core = core_client
        self._embedder = embedder
        self._rag_top_k = rag_top_k
        self._streaming = streaming_generator
        self._notifier = session_notifier
        self._tts = tts
        self._storage = storage
        self._tts_voice = tts_voice
        self._rag_timeout_sec = rag_timeout_sec

    async def handle(self, message: AbstractIncomingMessage) -> None:
        await consume_with_failure_signal(
            message,
            domain="followup",
            envelope_type=Envelope[GenerateFollowupRequest],
            idempotency=self._idempotency,
            publisher=self._publisher,
            routing_key=self._callback_routing_key,
            message_type="callback.questions",
            process=self._process,
            failed_payload=self._failed_payload,
            done_fields=lambda p: {"status": p.status},
        )

    async def _process(
        self, envelope: Envelope[GenerateFollowupRequest]
    ) -> FollowupCallbackPayload:
        req = envelope.payload
        log.info(
            "followup.generate.start",
            message_id=envelope.message_id,
            session_id=req.session_id,
            parent=req.parent_message_id,
            trace_id=envelope.trace_id,
        )

        rag_context = await self._build_rag_context(req, envelope.context.user_id)
        if self._streaming is not None and self._notifier is not None:
            result = await self._stream_followup(req, rag_context, envelope.trace_id)
        else:
            result = await self._generator.generate(
                job_category=req.job_category,
                mode=req.mode,
                previous_question=req.previous_question,
                answer_text=req.answer_text,
                context=rag_context,
                parent_category=req.parent_category or "UNKNOWN",
                expected_signal=req.parent_expected_signal or "(none)",
                history=_format_history(req.history),
            )
        return FollowupCallbackPayload(
            session_id=req.session_id,
            kind="FOLLOWUP",
            parent_message_id=req.parent_message_id,
            answer_message_id=req.answer_message_id,
            followup_question=result.followup_question,
            answer_evaluation=result.answer_evaluation,
            answer_intent=result.answer_intent,
            followup_message_id=req.followup_message_id,
        )

    def _failed_payload(
        self, req: GenerateFollowupRequest, exc: Exception
    ) -> FollowupCallbackPayload:
        """Core 가 이미 선INSERT 한 placeholder 가 있으므로, 실패해도 콜백은 항상 나가야
        placeholder 가 영원히 '생성 중'으로 남지 않는다. 상관 필드 3종은 전부 req
        (envelope payload) 출처라 어느 단계에서 실패해도 조립 가능하다."""
        error_code, retriable = classify_failure(
            exc, unexpected_code="GENERATION_FAILED"
        )
        return FollowupCallbackPayload(
            session_id=req.session_id,
            kind="FOLLOWUP",
            parent_message_id=req.parent_message_id,
            answer_message_id=req.answer_message_id,
            followup_message_id=req.followup_message_id,
            status="FAILED",
            error_code=error_code,
            error_message=format_error_message(exc),
            retriable=retriable,
        )

    async def _stream_followup(
        self, req: GenerateFollowupRequest, rag_context: str, trace_id: str
    ) -> FollowupResult:
        seq = {"n": 0}
        audio_seq = {"n": 0}
        q_acc = {"text": "", "consumed": 0}
        synth_tasks: list = []

        def _spawn_segment(sentence: str) -> None:
            if not (self._tts and self._storage):
                return
            idx = audio_seq["n"]
            audio_seq["n"] += 1
            synth_tasks.append(
                asyncio.create_task(
                    self._synth_segment(
                        session_id=req.session_id,
                        message_id=req.followup_message_id,
                        seq=idx,
                        sentence=sentence,
                        trace_id=trace_id,
                    )
                )
            )

        async def _on_token(delta: str) -> None:
            await self._notifier.emit_delta(
                session_id=req.session_id,
                message_id=req.followup_message_id,
                seq=seq["n"],
                text=delta,
                trace_id=trace_id,
            )
            seq["n"] += 1
            q_acc["text"] += delta
            sents, q_acc["consumed"] = next_sentences(q_acc["text"], q_acc["consumed"])
            for s in sents:
                _spawn_segment(s)

        try:
            result = await self._streaming.stream(
                on_question_token=_on_token,
                job_category=req.job_category,
                mode=req.mode,
                previous_question=req.previous_question,
                answer_text=req.answer_text,
                context=rag_context,
                parent_category=req.parent_category or "UNKNOWN",
                expected_signal=req.parent_expected_signal or "(none)",
                history=_format_history(req.history),
            )
        finally:
            # 실패로 빠져도 이미 떠 있는 세그먼트 합성 task 는 수거(뒷정리, 예외는 무시).
            tail = q_acc["text"][q_acc["consumed"] :].strip()
            if tail:
                _spawn_segment(tail)
            if synth_tasks:
                await asyncio.gather(*synth_tasks, return_exceptions=True)
        return result

    async def _synth_segment(
        self,
        *,
        session_id: int,
        message_id: int,
        seq: int,
        sentence: str,
        trace_id: str,
    ) -> None:
        """문장 하나를 TTS 합성 → S3 세그먼트 PUT → SESSION_MESSAGE_AUDIO 발행.

        라이브 세그먼트는 휘발성 — 실패해도 텍스트 스트림/콜백을 막지 않는다(로그만).
        """
        try:
            res = await self._tts.synthesize(sentence, voice=self._tts_voice)
            ext = _EXT_BY_CONTENT_TYPE.get(
                res.content_type.split(";")[0].strip(), "mp3"
            )
            key = f"interview/tts/{session_id}/{message_id}/seg-{seq}.{ext}"
            await self._storage.put_bytes(
                key, res.audio_bytes, content_type=res.content_type
            )
            await self._notifier.emit_audio(
                session_id=session_id,
                message_id=message_id,
                seq=seq,
                ext=ext,
                duration_sec=res.duration_sec,
                trace_id=trace_id,
            )
        except Exception as exc:  # noqa: BLE001
            log.warning(
                "followup.segment.failed",
                message_id=message_id,
                seq=seq,
                error=str(exc),
            )

    async def _build_rag_context(
        self, req: GenerateFollowupRequest, user_id: int | None
    ) -> str:
        # user_id 는 Core 가 검색 범위를 소유 문서로 제한하는 데 쓴다 — 없으면 검색하지 않는다.
        if not self._core or not self._embedder or not req.context_document_ids:
            return "(none)"
        if user_id is None:
            log.warning("followup.rag.skipped_no_user", session_id=req.session_id)
            return "(none)"
        try:
            return await asyncio.wait_for(
                self._do_build_rag_context(req, user_id), timeout=self._rag_timeout_sec
            )
        except asyncio.TimeoutError:
            log.warning(
                "followup.rag.timeout",
                session_id=req.session_id,
                timeout_sec=self._rag_timeout_sec,
            )
            return "(none)"

    async def _do_build_rag_context(
        self, req: GenerateFollowupRequest, user_id: int
    ) -> str:
        query = f"{req.previous_question}\n\n{req.answer_text}"
        try:
            query_vec = (
                await self._embedder.embed([query], task_type="RETRIEVAL_QUERY")
            )[0]
            hits = await self._core.search_embeddings(
                user_id=user_id,
                query_embedding=query_vec,
                query_text=query,
                document_ids=req.context_document_ids,
                top_k=self._rag_top_k,
            )
        except Exception as exc:
            log.warn("followup.rag.failed", error=str(exc), session_id=req.session_id)
            return "(none)"

        if not hits:
            return "(none)"
        return "\n---\n".join(
            f"[doc#{h.document_id} chunk#{h.chunk_index}] {h.chunk_text}" for h in hits
        )


def _format_history(history: list) -> str:
    """대화 히스토리를 '화자: 내용' 라인으로. 비면 '(none)'."""
    if not history:
        return "(none)"
    speaker = {"INTERVIEWER": "면접관", "INTERVIEWEE": "지원자"}
    lines = [f"{speaker.get(h.role, h.role)}: {h.content}" for h in history]
    return "\n".join(lines)
