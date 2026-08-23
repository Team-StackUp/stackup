from __future__ import annotations

import asyncio

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.question_generation_chain import QuestionGenerator
from ai_server.core.client import CoreClient
from ai_server.messaging.consumers.failure_signal import (
    classify_failure,
    consume_with_failure_signal,
    format_error_message,
)
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.messaging.session_notify import (
    QUESTION_POOL_PROGRESS_EVENT,
    SessionRealtimeNotifier,
)
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
        session_notifier: SessionRealtimeNotifier | None = None,
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
        self._session_notifier = session_notifier

    async def handle(self, message: AbstractIncomingMessage) -> None:
        await consume_with_failure_signal(
            message,
            domain="questions",
            envelope_type=Envelope[GenerateQuestionsRequest],
            idempotency=self._idempotency,
            publisher=self._publisher,
            routing_key=self._callback_routing_key,
            message_type="callback.questions",
            process=self._process,
            failed_payload=self._failed_payload,
            done_fields=lambda p: {
                "status": p.status,
                "question_count": len(p.questions),
            },
        )

    async def _process(
        self, envelope: Envelope[GenerateQuestionsRequest]
    ) -> QuestionPoolCallbackPayload:
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

        await self._emit_progress(
            session_id=req.session_id,
            phase="CONTEXT_BUILDING",
            message="면접 자료를 정리하고 있어요.",
            trace_id=envelope.trace_id,
        )
        context_text = await self._build_context(req, envelope.context.user_id)
        await self._emit_progress(
            session_id=req.session_id,
            phase="GENERATING",
            message="자료를 바탕으로 첫 질문을 만들고 있어요.",
            trace_id=envelope.trace_id,
        )
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
        # 실패는 예외로 위 가드에 넘어가므로 여기 도달 = 성공 —
        # FAILED 콜백 직전에 "마무리하고 있어요" 가 스치는 오해가 구조적으로 없다.
        await self._emit_progress(
            session_id=req.session_id,
            phase="FINALIZING",
            message="질문 준비를 마무리하고 있어요.",
            trace_id=envelope.trace_id,
        )
        return QuestionPoolCallbackPayload(
            session_id=req.session_id,
            kind="POOL",
            questions=pool.questions,
        )

    def _failed_payload(
        self, req: GenerateQuestionsRequest, exc: Exception
    ) -> QuestionPoolCallbackPayload:
        """실패해도 콜백은 항상 나가야 세션 시작이 무기한 멈추지 않는다."""
        error_code, retriable = classify_failure(
            exc, unexpected_code="GENERATION_FAILED"
        )
        return QuestionPoolCallbackPayload(
            session_id=req.session_id,
            kind="POOL",
            questions=[],
            status="FAILED",
            error_code=error_code,
            error_message=format_error_message(exc),
            retriable=retriable,
        )

    async def _emit_progress(
        self, *, session_id: int, phase: str, message: str, trace_id: str
    ) -> None:
        if self._session_notifier is None:
            return
        await self._session_notifier.emit_progress(
            event_type=QUESTION_POOL_PROGRESS_EVENT,
            session_id=session_id,
            phase=phase,
            message=message,
            trace_id=trace_id,
        )

    async def _build_context(
        self, req: GenerateQuestionsRequest, user_id: int | None
    ) -> str:
        base_context = _build_context(req.documents)
        # user_id 는 Core 가 검색 범위를 소유 문서로 제한하는 데 쓴다 — 없으면 검색하지 않는다.
        if not self._core or not self._embedder or user_id is None:
            return base_context

        document_ids = [d.document_id for d in req.documents]
        # PLAN vs Retrieve: 문서가 1개면 markdown 전체가 이미 base_context 에 들어 있어
        # 검색이 중복이다 → 검색 생략(PLAN). 여러 문서일 때만 RAG 로 관련 청크를 추린다.
        if len(document_ids) <= 1:
            return base_context

        try:
            return await asyncio.wait_for(
                self._do_build_context_rag(req, document_ids, base_context, user_id),
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
        user_id: int,
    ) -> str:
        query = _build_initial_rag_query(req)
        try:
            query_vec = (
                await self._embedder.embed([query], task_type="RETRIEVAL_QUERY")
            )[0]
            hits = await self._core.search_embeddings(
                user_id=user_id,
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
