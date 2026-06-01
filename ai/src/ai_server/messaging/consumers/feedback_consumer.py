from __future__ import annotations

from typing import Protocol

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.feedback_generation_chain import FeedbackGenerator
from ai_server.core.client import CoreClient
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.feedback import (
    FeedbackCallbackPayload,
    FeedbackMessageItem,
    GenerateFeedbackRequest,
)
from ai_server.rag.embedder import EmbeddingProvider

log = structlog.get_logger(__name__)


class FeedbackConsumer:
    """generate.feedback consumer (US-24).

    흐름:
      1. envelope parse + 멱등 체크
      2. transcript 텍스트 빌드 (시퀀스 순)
      3. (옵션) RAG: 마지막 답변을 쿼리로 임베딩 → Core /api/internal/embeddings/search → topK 청크
      4. chain.generate → FeedbackResult
      5. callback.feedback 발행
    """

    def __init__(
        self,
        *,
        generator: FeedbackGenerator,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
        core_client: CoreClient,
        embedder: EmbeddingProvider | None = None,
        rag_top_k: int = 5,
    ) -> None:
        self._generator = generator
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._core = core_client
        self._embedder = embedder
        self._rag_top_k = rag_top_k

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[GenerateFeedbackRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "feedback.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "feedback.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

            req = envelope.payload
            log.info(
                "feedback.generate.start",
                message_id=envelope.message_id,
                session_id=req.session_id,
                msg_count=len(req.messages),
                ctx_count=len(req.context_document_ids),
                trace_id=envelope.trace_id,
            )

            transcript = _build_transcript(req.messages)
            rag_context = await self._build_rag_context(req)

            result = await self._generator.generate(
                job_category=req.job_category,
                mode=req.mode,
                total_question_count=req.total_question_count,
                end_reason=req.end_reason,
                transcript=transcript,
                rag_context=rag_context,
            )

            payload = FeedbackCallbackPayload(
                session_id=req.session_id,
                overall_score=result.overall_score,
                technical_accuracy=result.technical_accuracy,
                logic_score=result.logic_score,
                communication_score=result.communication_score,
                strengths_summary=result.strengths_summary,
                weaknesses_summary=result.weaknesses_summary,
                improvement_keywords=result.improvement_keywords,
                report_s3_key=None,
            )

            await self._publisher.publish(
                routing_key=self._callback_routing_key,
                message_type="callback.feedback",
                payload=payload,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )
            log.info(
                "feedback.generate.done",
                message_id=envelope.message_id,
                session_id=req.session_id,
                trace_id=envelope.trace_id,
            )

    async def _build_rag_context(self, req: GenerateFeedbackRequest) -> str:
        if not self._embedder or not req.context_document_ids:
            return "(none)"
        last_answer = next(
            (m.content for m in reversed(req.messages) if m.role == "INTERVIEWEE"),
            None,
        )
        if not last_answer:
            return "(none)"
        try:
            query_vec = (await self._embedder.embed([last_answer]))[0]
            hits = await self._core.search_embeddings(
                query_embedding=query_vec,
                document_ids=req.context_document_ids,
                top_k=self._rag_top_k,
            )
        except Exception as exc:
            log.warn("feedback.rag.failed", error=str(exc), session_id=req.session_id)
            return "(none)"
        if not hits:
            return "(none)"
        return "\n---\n".join(f"[doc#{h.document_id} chunk#{h.chunk_index}] {h.chunk_text}" for h in hits)


def _build_transcript(messages: list[FeedbackMessageItem]) -> str:
    if not messages:
        return "(empty)"
    lines: list[str] = []
    for m in messages:
        speaker = "면접관" if m.role == "INTERVIEWER" else ("지원자" if m.role == "INTERVIEWEE" else m.role)
        lines.append(f"[{m.sequence_number}] {speaker}: {m.content}")
    return "\n".join(lines)
