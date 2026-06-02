from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.followup_generation_chain import FollowupGenerator
from ai_server.core.client import CoreClient
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.followup import (
    FollowupCallbackPayload,
    GenerateFollowupRequest,
)
from ai_server.rag.embedder import EmbeddingProvider
from ai_server.rag.reranker import NoopReranker, Reranker, rerank_hits

log = structlog.get_logger(__name__)


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
        reranker: Reranker | None = None,
        candidate_k: int = 20,
    ) -> None:
        self._generator = generator
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._core = core_client
        self._embedder = embedder
        self._rag_top_k = rag_top_k
        self._reranker = reranker or NoopReranker()
        self._candidate_k = max(candidate_k, rag_top_k)

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[GenerateFollowupRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "followup.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "followup.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

            req = envelope.payload
            log.info(
                "followup.generate.start",
                message_id=envelope.message_id,
                session_id=req.session_id,
                parent=req.parent_message_id,
                trace_id=envelope.trace_id,
            )

            result = await self._generator.generate(
                job_category=req.job_category,
                mode=req.mode,
                previous_question=req.previous_question,
                answer_text=req.answer_text,
                context=await self._build_rag_context(req),
            )

            payload = FollowupCallbackPayload(
                session_id=req.session_id,
                kind="FOLLOWUP",
                parent_message_id=req.parent_message_id,
                followup_question=result.followup_question,
                answer_evaluation=result.answer_evaluation,
            )

            await self._publisher.publish(
                routing_key=self._callback_routing_key,
                message_type="callback.questions",
                payload=payload,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )
            log.info(
                "followup.generate.done",
                message_id=envelope.message_id,
                session_id=req.session_id,
                trace_id=envelope.trace_id,
            )

    async def _build_rag_context(self, req: GenerateFollowupRequest) -> str:
        if not self._core or not self._embedder or not req.context_document_ids:
            return "(none)"

        query = f"{req.previous_question}\n\n{req.answer_text}"
        try:
            query_vec = (
                await self._embedder.embed([query], task_type="RETRIEVAL_QUERY")
            )[0]
            hits = await self._core.search_embeddings(
                query_embedding=query_vec,
                query_text=query,
                document_ids=req.context_document_ids,
                top_k=self._candidate_k,
            )
        except Exception as exc:
            log.warn("followup.rag.failed", error=str(exc), session_id=req.session_id)
            return "(none)"

        if not hits:
            return "(none)"
        hits = await rerank_hits(
            self._reranker, query=query, hits=hits, top_k=self._rag_top_k
        )
        return "\n---\n".join(
            f"[doc#{h.document_id} chunk#{h.chunk_index}] {h.chunk_text}" for h in hits
        )
