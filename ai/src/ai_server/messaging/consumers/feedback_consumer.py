from __future__ import annotations

import asyncio

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.feedback_generation_chain import (
    JOB_FIT_DIMENSION,
    JOB_FIT_EVALUATOR_LABEL,
    ROLE_UNDERSTANDING_DIMENSION,
    ROLE_UNDERSTANDING_LABEL,
    SELF_INTRO_DIMENSION,
    SELF_INTRO_EVALUATOR_LABEL,
    EvaluatorResult,
    FeedbackGenerator,
    JobFitEvaluator,
    SelfIntroEvaluator,
)
from ai_server.core.client import CoreClient
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.feedback import (
    FeedbackCallbackPayload,
    FeedbackMessageItem,
    GenerateFeedbackRequest,
    PanelBreakdownItem,
    VoiceAnalysisSummary,
)
from ai_server.rag.embedder import EmbeddingProvider

log = structlog.get_logger(__name__)

_SELF_INTRO_CATEGORY = "SELF_INTRODUCTION"
_JOB_TAILORED_MODE = "JOB_TAILORED"


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
        self_intro_evaluator: SelfIntroEvaluator | None = None,
        job_fit_evaluator: JobFitEvaluator | None = None,
    ) -> None:
        self._generator = generator
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._core = core_client
        self._embedder = embedder
        self._rag_top_k = rag_top_k
        self._self_intro_evaluator = self_intro_evaluator
        self._job_fit_evaluator = job_fit_evaluator

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
            score_basis = _build_score_basis(req.messages)
            rag_context = await self._build_rag_context(req)
            voice_analysis_summary = _build_voice_analysis_summary(
                req.voice_analysis_summary
            )

            # 종합 피드백 + 자기소개 첫인상 + 직무 적합도(직무 맞춤 모드)를 병렬 실행.
            # 첫인상·직무 적합도는 종합 점수(overall)에 미포함 — generator 가 모른 채 계산한 뒤 표시용으로 덧붙인다.
            result, self_intro_item, job_fit_items = await asyncio.gather(
                self._generator.generate(
                    job_category=req.job_category,
                    mode=req.mode,
                    total_question_count=req.total_question_count,
                    end_reason=req.end_reason,
                    transcript=transcript,
                    score_basis=score_basis,
                    rag_context=rag_context,
                    voice_analysis_summary=voice_analysis_summary,
                    domain_question_counts=req.domain_question_counts,
                ),
                self._evaluate_self_intro(req, voice_analysis_summary),
                self._evaluate_job_fit(req, transcript, rag_context),
            )
            if self_intro_item is not None:
                result.panel_breakdown.append(self_intro_item)
            result.panel_breakdown.extend(job_fit_items)

            payload = FeedbackCallbackPayload(
                session_id=req.session_id,
                overall_score=result.overall_score,
                technical_accuracy=result.technical_accuracy,
                logic_score=result.logic_score,
                communication_score=result.communication_score,
                strengths_summary=result.strengths_summary,
                weaknesses_summary=result.weaknesses_summary,
                improvement_keywords=result.improvement_keywords,
                study_plan=result.study_plan,
                panel_breakdown=result.panel_breakdown,
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

    async def _evaluate_self_intro(
        self, req: GenerateFeedbackRequest, voice_analysis_summary: str
    ) -> PanelBreakdownItem | None:
        """자기소개 Q/A 를 찾아 첫인상 평가 → 패널 항목 1개. 없거나 실패하면 None(피드백은 계속)."""
        if self._self_intro_evaluator is None:
            return None
        pair = _find_self_intro(req.messages)
        if pair is None:
            return None  # 레거시 세션(자기소개 없음) 또는 빈 답변 — 건너뜀
        question, answer = pair
        try:
            ev = await self._self_intro_evaluator.evaluate(
                job_category=req.job_category,
                mode=req.mode,
                self_intro_question=question.content,
                self_intro_answer=answer.content,
                voice_analysis_summary=voice_analysis_summary,
            )
        except Exception as exc:  # noqa: BLE001
            log.warning(
                "feedback.self_intro.failed", error=str(exc), session_id=req.session_id
            )
            return None
        return PanelBreakdownItem(
            evaluator=SELF_INTRO_EVALUATOR_LABEL,
            dimension=SELF_INTRO_DIMENSION,
            score=ev.score,
            strength=ev.strength,
            weakness=ev.weakness,
            detail=ev.detail,
            score_rationale=ev.score_rationale,
        )

    async def _evaluate_job_fit(
        self, req: GenerateFeedbackRequest, transcript: str, rag_context: str
    ) -> list[PanelBreakdownItem]:
        """직무 맞춤 모드일 때 JD 대비 '직무 적합도'+'직무 이해도' 평가 → 패널 항목 2개.
        그 외 모드/빈 JD/실패는 빈 리스트."""
        if self._job_fit_evaluator is None:
            return []
        if (req.mode or "") != _JOB_TAILORED_MODE:
            return []
        jd = (req.target_job_description or "").strip()
        if not jd:
            return []
        try:
            res = await self._job_fit_evaluator.evaluate(
                company_name=req.target_company_name or "",
                job_description=jd,
                job_category=req.job_category,
                mode=req.mode,
                transcript=transcript,
                rag_context=rag_context,
            )
        except Exception as exc:  # noqa: BLE001
            log.warning(
                "feedback.job_fit.failed", error=str(exc), session_id=req.session_id
            )
            return []
        return [
            _to_panel_item(JOB_FIT_EVALUATOR_LABEL, JOB_FIT_DIMENSION, res.fit),
            _to_panel_item(
                ROLE_UNDERSTANDING_LABEL,
                ROLE_UNDERSTANDING_DIMENSION,
                res.understanding,
            ),
        ]

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
            query_vec = (
                await self._embedder.embed([last_answer], task_type="RETRIEVAL_QUERY")
            )[0]
            hits = await self._core.search_embeddings(
                query_embedding=query_vec,
                query_text=last_answer,
                document_ids=req.context_document_ids,
                top_k=self._rag_top_k,
            )
        except Exception as exc:
            log.warn("feedback.rag.failed", error=str(exc), session_id=req.session_id)
            return "(none)"
        if not hits:
            return "(none)"
        return "\n---\n".join(
            f"[doc#{h.document_id} chunk#{h.chunk_index}] {h.chunk_text}" for h in hits
        )


def _to_panel_item(
    label: str, dimension: str, ev: EvaluatorResult
) -> PanelBreakdownItem:
    """평가위원 결과(EvaluatorResult)를 패널 표시 항목으로 변환."""
    return PanelBreakdownItem(
        evaluator=label,
        dimension=dimension,
        score=ev.score,
        strength=ev.strength,
        weakness=ev.weakness,
        detail=ev.detail,
        score_rationale=ev.score_rationale,
    )


def _find_self_intro(
    messages: list[FeedbackMessageItem],
) -> tuple[FeedbackMessageItem, FeedbackMessageItem] | None:
    """자기소개 질문(category=SELF_INTRODUCTION)과 그 답변 쌍. 없거나 답변이 비면 None."""
    question = next(
        (
            m
            for m in messages
            if m.role == "INTERVIEWER" and (m.category or "") == _SELF_INTRO_CATEGORY
        ),
        None,
    )
    if question is None:
        return None
    answer = next(
        (
            m
            for m in messages
            if m.role == "INTERVIEWEE" and m.parent_message_id == question.id
        ),
        None,
    )
    if answer is None or not (answer.content or "").strip():
        return None
    return question, answer


def _build_transcript(messages: list[FeedbackMessageItem]) -> str:
    if not messages:
        return "(empty)"
    lines: list[str] = []
    for m in messages:
        speaker = (
            "면접관"
            if m.role == "INTERVIEWER"
            else ("지원자" if m.role == "INTERVIEWEE" else m.role)
        )
        line = f"[{m.sequence_number}] {speaker}: {m.content}"
        if m.role == "INTERVIEWER" and m.expected_signal:
            line += f"\n    └ 기대 신호(평가 기준): {m.expected_signal}"
        if m.role == "INTERVIEWEE" and m.evaluation is not None:
            line += f"\n    └ 답변평가: {_format_evaluation(m.evaluation)}"
        lines.append(line)
    return "\n".join(lines)


def _format_evaluation(e) -> str:
    parts: list[str] = []
    if e.specificity is not None:
        parts.append(f"specificity={e.specificity:g}")
    if e.logic is not None:
        parts.append(f"logic={e.logic:g}")
    if e.structure:
        parts.append(f"structure={e.structure}")
    if e.correctness is not None:
        parts.append(f"correctness={e.correctness:g}")
    return ", ".join(parts) if parts else "(없음)"


_STRUCTURE_SCORE = {"FULL_STAR": 5.0, "PARTIAL_STAR": 2.5, "NONE": 0.0}


def _mean(values: list[float]) -> float | None:
    vals = [v for v in values if v is not None]
    return sum(vals) / len(vals) if vals else None


def _to_100(x: float | None) -> int | None:
    return None if x is None else round(x * 20)


def _build_score_basis(messages: list[FeedbackMessageItem]) -> str:
    """per-answer 평가(0~5)를 차원별 0~100 기준값으로 결정론적 집계(하이브리드).

    이 값을 LLM 에 '기준값'으로 제시하고 ±15점 이내 산정하도록 제약 → 재현성·캘리브레이션.
    correctness(RAG 기반)가 전부 null 이면 technical_accuracy 기준값은 '근거 없음'.
    """
    evals = [
        m.evaluation
        for m in messages
        if m.role == "INTERVIEWEE" and m.evaluation is not None
    ]
    if not evals:
        return "(per-answer 평가 없음 — 전사 내용으로만 산정. 과대평가 금지.)"

    spec = _mean([e.specificity for e in evals])
    logic = _mean([e.logic for e in evals])
    corr = _mean([e.correctness for e in evals])  # null 은 자동 제외
    struct = _mean([_STRUCTURE_SCORE.get(e.structure) for e in evals])

    tech_100 = _to_100(corr)
    logic_100 = _to_100(logic)
    comm_src = _mean([v for v in (spec, struct) if v is not None])
    comm_100 = _to_100(comm_src)
    overall_src = [v for v in (tech_100, logic_100, comm_100) if v is not None]
    overall_100 = round(sum(overall_src) / len(overall_src)) if overall_src else None

    def fmt5(x: float | None) -> str:
        return f"{x:.1f}/5" if x is not None else "없음"

    corr_count = sum(1 for e in evals if e.correctness is not None)
    lines = [
        f"- 채점된 답변 수: {len(evals)} (correctness 산정 {corr_count}건)",
        f"- specificity 평균: {fmt5(spec)}, logic 평균: {fmt5(logic)}, "
        f"structure 평균: {fmt5(struct)}, correctness 평균: {fmt5(corr)}",
        "[차원별 기준값(0~100)]",
        (
            f"- technical_accuracy ≈ {tech_100} (correctness=RAG 사실일치 기반)"
            if tech_100 is not None
            else "- technical_accuracy: 근거 없음(참고문서 미선택→correctness 미산정). "
            "전사로 보수적으로 판단."
        ),
        f"- logic_score ≈ {logic_100 if logic_100 is not None else '근거 없음'}",
        f"- communication_score ≈ {comm_100 if comm_100 is not None else '근거 없음'} "
        "(specificity 명료성 + structure 구조화)",
        f"- overall_score ≈ {overall_100 if overall_100 is not None else '근거 없음'}",
    ]
    return "\n".join(lines)


def _build_voice_analysis_summary(summary: VoiceAnalysisSummary | None) -> str:
    if summary is None:
        return "No voice analysis summary was provided."

    lines: list[str] = []
    if summary.analyzed_message_count is not None:
        lines.append(f"Analyzed answer messages: {summary.analyzed_message_count}")
    if summary.average_speaking_rate_wpm is not None:
        lines.append(
            f"Average speaking rate: {summary.average_speaking_rate_wpm:g} WPM"
        )
    if summary.total_silence_duration_sec is not None:
        lines.append(
            f"Total silence duration: {summary.total_silence_duration_sec:g} seconds"
        )
    if summary.filler_word_counts:
        filler_words = ", ".join(
            f"{word}: {count}"
            for word, count in sorted(summary.filler_word_counts.items())
        )
        lines.append(f"Filler word counts: {filler_words}")

    return "\n".join(lines) if lines else "No voice analysis summary was provided."
