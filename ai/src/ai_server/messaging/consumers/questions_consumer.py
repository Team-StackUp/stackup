from __future__ import annotations

import asyncio

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
        rag_timeout_sec: float = 1.5,
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
        self._rag_timeout_sec = rag_timeout_sec

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
            payload = await self._generate_pool_payload(
                req, context_text, effective_pool_size, trace_id=envelope.trace_id
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
                status=payload.status,
                question_count=len(payload.questions),
                trace_id=envelope.trace_id,
            )

    async def _generate_pool_payload(
        self,
        req: GenerateQuestionsRequest,
        context_text: str,
        effective_pool_size: int,
        *,
        trace_id: str,
    ) -> QuestionPoolCallbackPayload:
        """질문 풀 생성. 실패해도 콜백은 항상 나가야 세션 시작이 무기한 멈추지 않는다."""
        try:
            pool = await self._generator.generate(
                job_categories=req.job_categories,
                mode=req.mode,
                max_questions=effective_pool_size,
                context=context_text,
                recent_questions=req.recent_questions,
                self_introduction=req.self_intro_answer,
                target_company_name=req.target_company_name,
                target_job_description=req.target_job_description,
                focus_areas=req.focus_areas,
            )
        except Exception as exc:  # noqa: BLE001
            log.exception(
                "questions.generate.failed",
                session_id=req.session_id,
                trace_id=trace_id,
            )
            return QuestionPoolCallbackPayload(
                session_id=req.session_id,
                kind="POOL",
                questions=[],
                status="FAILED",
                error_code=(
                    "GENERATION_SCHEMA_INVALID"
                    if isinstance(exc, TypeError)
                    else "GENERATION_FAILED"
                ),
                error_message=str(exc),
                retriable=not isinstance(exc, TypeError),
            )
        return QuestionPoolCallbackPayload(
            session_id=req.session_id,
            kind="POOL",
            questions=pool.questions,
        )

    async def _build_context(self, req: GenerateQuestionsRequest) -> str:
        base_context = _build_context(req.documents)
        if not self._core or not self._embedder:
            return base_context

        document_ids = [d.document_id for d in req.documents]
        # PLAN vs Retrieve: 문서가 1개면 markdown 전체가 이미 base_context 에 들어 있어
        # 검색이 중복이다 → 검색 생략(PLAN). 여러 문서일 때만 RAG 로 관련 청크를 추린다.
        if len(document_ids) <= 1:
            return base_context

        try:
            return await asyncio.wait_for(
                self._do_build_context_rag(req, document_ids, base_context),
                timeout=self._rag_timeout_sec,
            )
        except asyncio.TimeoutError:
            log.warning(
                "questions.rag.timeout",
                session_id=req.session_id,
                timeout_sec=self._rag_timeout_sec,
            )
            return base_context

    async def _do_build_context_rag(
        self,
        req: GenerateQuestionsRequest,
        document_ids: list[int],
        base_context: str,
    ) -> str:
        query = _build_initial_rag_query(req)
        try:
            query_vec = (
                await self._embedder.embed([query], task_type="RETRIEVAL_QUERY")
            )[0]
            hits = await self._core.search_embeddings(
                query_embedding=query_vec,
                query_text=query,
                document_ids=document_ids,
                top_k=self._rag_top_k,
            )
        except Exception as exc:
            log.warn("questions.rag.failed", error=str(exc), session_id=req.session_id)
            return base_context

        if not hits:
            return base_context
        rag_context = "\n---\n".join(
            f"[doc#{h.document_id} chunk#{h.chunk_index}] {h.chunk_text}" for h in hits
        )
        return f"{base_context}\n\n## Retrieved document chunks\n{rag_context}"


_SOURCE_LABEL = {
    "RESUME": "이력서",
    "REPOSITORY": "GitHub 레포",
    "COVER_LETTER": "자소서",
    "WEB": "웹 문서",
}


def _build_context(documents: list[DocumentContext]) -> str:
    parts: list[str] = []
    for d in documents:
        label = _SOURCE_LABEL.get(d.source_type, d.source_type)
        block = [f"## 문서 #{d.document_id} ({label})"]
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
        f"job categories: {', '.join(req.job_categories)}",
    ]
    # 자기소개·JD 를 쿼리에 포함해 검색이 지원자 발화/직무 요구로 steering 되게 한다.
    intro = (req.self_intro_answer or "").strip()
    if intro:
        parts.append(f"self-introduction: {intro[:600]}")
    jd = (req.target_job_description or "").strip()
    if jd:
        company = (req.target_company_name or "").strip()
        head = f"target role at {company}" if company else "target role (JD)"
        parts.append(f"{head}: {jd[:800]}")
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
