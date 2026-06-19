from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Protocol

import structlog
from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.feedback_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.chain.prompts import feedback_panel
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.observability.llm_logging_callback import CoreAiLogCallback

log = structlog.get_logger(__name__)


class FeedbackResult(BaseModel):
    overall_score: float | None = Field(None, description="0~100")
    technical_accuracy: float | None = Field(None, description="0~100")
    logic_score: float | None = Field(None, description="0~100")
    communication_score: float | None = Field(None, description="0~100")
    strengths_summary: str | None = Field(None)
    weaknesses_summary: str | None = Field(None)
    improvement_keywords: list[str] = Field(default_factory=list)


class FeedbackGenerator(Protocol):
    async def generate(
        self,
        *,
        job_category: str,
        mode: str,
        total_question_count: int | None,
        end_reason: str | None,
        transcript: str,
        rag_context: str,
        voice_analysis_summary: str,
        score_basis: str = "(없음)",
    ) -> FeedbackResult: ...


class LlmFeedbackGenerator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def generate(
        self,
        *,
        job_category: str,
        mode: str,
        total_question_count: int | None,
        end_reason: str | None,
        transcript: str,
        rag_context: str,
        voice_analysis_summary: str = "",
        score_basis: str = "(없음)",
    ) -> FeedbackResult:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "mode": mode,
                "total_question_count": total_question_count or 0,
                "end_reason": end_reason or "USER_REQUEST",
                "transcript": transcript,
                "score_basis": score_basis or "(없음)",
                "rag_context": rag_context or "(none)",
                "voice_analysis_summary": voice_analysis_summary
                or "No voice analysis summary was provided.",
            }
        )
        if not isinstance(result, FeedbackResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected FeedbackResult"
            )
        return result


def build_feedback_generation_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=FeedbackResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", SYSTEM_PROMPT),
            ("human", HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.feedback",
                default_model=settings.llm_pro_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_pro_model,
        temperature=settings.llm_pro_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return prompt | llm | parser


# ── 멀티 면접관 패널 ──────────────────────────────────────────────────────────
# 단일 평가자 대신 직군·논리·커뮤니케이션 평가위원이 각자 한 축을 채점(병렬) →
# 가중평균으로 종합. A=평가만 / B=직군별(+단일직군도 다관점) / C=가중평균 / D=프롬프트 멀티콜.


class EvaluatorResult(BaseModel):
    score: float | None = Field(None, description="0~100, 산정 불가 시 null")
    strength: str | None = None
    weakness: str | None = None
    keywords: list[str] = Field(default_factory=list)


@dataclass(frozen=True)
class _EvaluatorSpec:
    key: str  # 'technical' | 'logic' | 'communication'
    label: str  # 요약 표기용 ('기술'/'인성'/'논리'/'전달')
    persona: str
    dimension_name: str
    dimension_guide: str


def _domain_spec(job_category: str, mode: str) -> _EvaluatorSpec:
    # PERSONALITY 모드는 기술 평가자를 인성·협업 평가자로 교체(사용자 결정).
    if (mode or "").upper() == "PERSONALITY":
        return _EvaluatorSpec(
            key="technical",
            label="인성",
            persona="인성·협업 중심 면접관",
            dimension_name="인성·협업 역량",
            dimension_guide=(
                "- 협업/갈등 해결, 성장 경험, 태도, 자기주도성을 봅니다. "
                "기술 정확도는 평가하지 않습니다."
            ),
        )
    return _EvaluatorSpec(
        key="technical",
        label="기술",
        persona=f"{job_category} 직군 시니어 기술 면접관",
        dimension_name="기술 정확도·깊이",
        dimension_guide=(
            "- 기술 정확성, 깊이, trade-off, 근거를 봅니다. 질문의 '기대 신호'를 "
            "답변이 얼마나 짚었는지를 핵심 근거로 삼습니다."
        ),
    )


_LOGIC_SPEC = _EvaluatorSpec(
    key="logic",
    label="논리",
    persona="논리·문제해결 평가위원",
    dimension_name="논리·인과관계 명확성",
    dimension_guide=(
        "- 주장→근거→결론의 인과, trade-off 설명의 일관성, 문제 구조화를 봅니다."
    ),
)

_COMM_SPEC = _EvaluatorSpec(
    key="communication",
    label="전달",
    persona="커뮤니케이션·전달력 평가위원",
    dimension_name="명료성·구조화·전달력",
    dimension_guide=(
        "- 답변의 구조(STAR 등)·간결성·명료성을 보고, 음성 분석(WPM·무음·간투어)이 "
        "있으면 전달력 판단에 적극 활용합니다."
    ),
)


def build_panel_evaluator_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    """패널 평가위원 1명용 체인. persona/dimension 을 invoke 변수로 받아 N회 재사용."""
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=EvaluatorResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", feedback_panel.SYSTEM_PROMPT),
            ("human", feedback_panel.HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.feedback.panel",
                default_model=settings.llm_pro_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_pro_model,
        temperature=settings.llm_pro_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return prompt | llm | parser


def _weighted_overall(pairs: list[tuple[float | None, float]]) -> float | None:
    """(score, weight) 중 score 가 있는 것만 가중평균. 전부 None 이면 None."""
    present = [(s, w) for s, w in pairs if s is not None and w > 0]
    if not present:
        return None
    total_w = sum(w for _, w in present)
    return round(sum(s * w for s, w in present) / total_w)


def _merge_notes(items: list[tuple[str, str | None]]) -> str | None:
    parts = [f"[{label}] {note.strip()}" for label, note in items if note and note.strip()]
    return " ".join(parts) if parts else None


def _dedup_keywords(keywords: list[str], cap: int = 8) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for kw in keywords:
        k = (kw or "").strip()
        if k and k not in seen:
            seen.add(k)
            out.append(k)
        if len(out) >= cap:
            break
    return out


class PanelFeedbackGenerator:
    """직군·논리·커뮤니케이션 평가위원을 병렬 호출 → 가중평균 종합. FeedbackGenerator 호환."""

    def __init__(self, chain: Runnable, *, weights: tuple[float, float, float] = (0.5, 0.25, 0.25)) -> None:
        self._chain = chain
        self._w_tech, self._w_logic, self._w_comm = weights

    async def generate(
        self,
        *,
        job_category: str,
        mode: str,
        total_question_count: int | None,
        end_reason: str | None,
        transcript: str,
        rag_context: str,
        voice_analysis_summary: str = "",
        score_basis: str = "(없음)",
    ) -> FeedbackResult:
        specs = [_domain_spec(job_category, mode), _LOGIC_SPEC, _COMM_SPEC]
        shared = {
            "job_category": job_category,
            "mode": mode,
            "total_question_count": total_question_count or 0,
            "end_reason": end_reason or "USER_REQUEST",
            "transcript": transcript,
            "score_basis": score_basis or "(없음)",
            "rag_context": rag_context or "(none)",
            "voice_analysis_summary": voice_analysis_summary
            or "No voice analysis summary was provided.",
        }
        raw = await asyncio.gather(
            *(
                self._chain.ainvoke(
                    {
                        **shared,
                        "persona": s.persona,
                        "dimension_name": s.dimension_name,
                        "dimension_guide": s.dimension_guide,
                    }
                )
                for s in specs
            ),
            return_exceptions=True,
        )

        results: dict[str, EvaluatorResult] = {}
        for spec, r in zip(specs, raw):
            if isinstance(r, EvaluatorResult):
                results[spec.key] = r
            else:
                log.warning(
                    "feedback.panel.evaluator_failed",
                    evaluator=spec.key,
                    error=str(r),
                )
                results[spec.key] = EvaluatorResult()

        tech = results["technical"]
        logic = results["logic"]
        comm = results["communication"]
        domain_label = specs[0].label

        overall = _weighted_overall(
            [
                (tech.score, self._w_tech),
                (logic.score, self._w_logic),
                (comm.score, self._w_comm),
            ]
        )
        strengths = _merge_notes(
            [(domain_label, tech.strength), ("논리", logic.strength), ("전달", comm.strength)]
        )
        weaknesses = _merge_notes(
            [(domain_label, tech.weakness), ("논리", logic.weakness), ("전달", comm.weakness)]
        )
        keywords = _dedup_keywords(tech.keywords + logic.keywords + comm.keywords)

        return FeedbackResult(
            overall_score=overall,
            technical_accuracy=tech.score,
            logic_score=logic.score,
            communication_score=comm.score,
            strengths_summary=strengths,
            weaknesses_summary=weaknesses,
            improvement_keywords=keywords,
        )
