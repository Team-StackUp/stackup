from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.question_generation_chain import QuestionGenerator
from ai_server.core.client import CoreClient
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.questions import (
    DocumentContext,
    GenerateQuestionsRequest,
    QuestionPoolCallbackPayload,
)
from ai_server.rag.embedder import EmbeddingProvider
from ai_server.rag.reranker import NoopReranker, Reranker, rerank_hits

log = structlog.get_logger(__name__)


class QuestionsConsumer:
    def __init__(
        self,
        *,
        generator: QuestionGenerator,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
        initial_pool_size: int = 1,
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
        # Core compatibility keeps callback.kind=POOL, but this is the initial
        # question result. maxQuestions remains the full session limit.
        self._initial_pool_size = max(1, initial_pool_size)
        self._core = core_client
        self._embedder = embedder
        self._rag_top_k = rag_top_k
        self._reranker = reranker or NoopReranker()
        self._candidate_k = max(candidate_k, rag_top_k)

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[GenerateQuestionsRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "questions.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "questions.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

            req = envelope.payload
            effective_pool_size = max(
                1,
                req.initial_question_count,
            )
            log.info(
                "questions.generate.start",
                message_id=envelope.message_id,
                session_id=req.session_id,
                doc_count=len(req.documents),
                max_questions=req.max_questions,
                pool_size=effective_pool_size,
                trace_id=envelope.trace_id,
            )

            context_text = await self._build_context(req)
            pool = await self._generator.generate(
                job_category=req.job_category,
                mode=req.mode,
                max_questions=effective_pool_size,
                context=context_text,
            )

            payload = QuestionPoolCallbackPayload(
                session_id=req.session_id,
                kind="POOL",
                questions=pool.questions,
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
                "questions.generate.done",
                message_id=envelope.message_id,
                session_id=req.session_id,
                question_count=len(pool.questions),
                trace_id=envelope.trace_id,
            )

    async def _build_context(self, req: GenerateQuestionsRequest) -> str:
        base_context = _build_context(req.documents)
        if not self._core or not self._embedder:
            return base_context

        document_ids = [d.document_id for d in req.documents]
        if not document_ids:
            return base_context

        query = _build_initial_rag_query(req)
        try:
            query_vec = (
                await self._embedder.embed([query], task_type="RETRIEVAL_QUERY")
            )[0]
            hits = await self._core.search_embeddings(
                query_embedding=query_vec,
                query_text=query,
                document_ids=document_ids,
                top_k=self._candidate_k,
            )
        except Exception as exc:
            log.warn("questions.rag.failed", error=str(exc), session_id=req.session_id)
            return base_context

        if not hits:
            return base_context
        hits = await rerank_hits(
            self._reranker, query=query, hits=hits, top_k=self._rag_top_k
        )
        rag_context = "\n---\n".join(
            f"[doc#{h.document_id} chunk#{h.chunk_index}] {h.chunk_text}" for h in hits
        )
        return f"{base_context}\n\n## Retrieved document chunks\n{rag_context}"


def _build_context(documents: list[DocumentContext]) -> str:
    parts: list[str] = []
    for d in documents:
        block = [f"## 문서 #{d.document_id} ({d.source_type})"]
        if d.summary:
            block.append(f"요약: {d.summary}")
        if d.tech_stack:
            block.append("기술 스택: " + ", ".join(d.tech_stack))
        if d.markdown:
            block.append("")
            block.append(d.markdown)
        parts.append("\n".join(block))
    return "\n\n".join(parts) if parts else "(no documents)"


def _build_initial_rag_query(req: GenerateQuestionsRequest) -> str:
    parts = [
        f"mode: {req.mode}",
        f"job category: {req.job_category}",
    ]
    for d in req.documents:
        doc_parts = [f"document #{d.document_id} {d.source_type}"]
        if d.summary:
            doc_parts.append(d.summary)
        if d.tech_stack:
            doc_parts.append("tech stack: " + ", ".join(d.tech_stack))
        if d.markdown:
            doc_parts.append(d.markdown[:1000])
        parts.append("\n".join(doc_parts))
    return "\n\n".join(parts)
