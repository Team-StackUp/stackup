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
from ai_server.chain.prompts import (
    answer_coaching,
    feedback_panel,
    feedback_synthesis,
    job_fit_evaluation,
    personality_evaluation,
    self_intro_evaluation,
)
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.model.messages.feedback import PanelBreakdownItem
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
    study_plan: list[str] = Field(default_factory=list)
    # 강조 표시용 핵심 구절(강점·개선 본문에서 그대로 발췌). 프론트가 부분 문자열 매칭해 하이라이트.
    highlights: list[str] = Field(default_factory=list)
    panel_breakdown: list[PanelBreakdownItem] = Field(default_factory=list)


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
        domain_question_counts: dict[str, int] | None = None,
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
        domain_question_counts: dict[str, int] | None = None,
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
    detail: str | None = Field(None, description="근거·예시 포함 2~4문장 상세 평가")
    score_rationale: str | None = Field(None, description="점수 근거 한두 문장")
    keywords: list[str] = Field(default_factory=list)


class SynthesisResult(BaseModel):
    strengths_summary: str | None = None
    weaknesses_summary: str | None = None
    improvement_keywords: list[str] = Field(default_factory=list)
    study_plan: list[str] = Field(default_factory=list)
    # strengths/weaknesses 본문에서 그대로 발췌한 핵심 구절(강조 표시용).
    highlights: list[str] = Field(default_factory=list)


@dataclass(frozen=True)
class _EvaluatorSpec:
    key: str  # 'technical' | 'logic' | 'communication'
    label: str  # 요약 표기용 ('기술'/'인성'/'논리'/'전달')
    persona: str
    dimension_name: str
    dimension_guide: str


_TECH_GUIDE = (
    "- 기술 정확성, 깊이, trade-off, 근거를 봅니다. 질문의 '기대 신호'를 "
    "답변이 얼마나 짚었는지를 핵심 근거로 삼습니다."
)

_DOMAIN_KO = {
    "FRONTEND": "프론트엔드",
    "BACKEND": "백엔드",
    "INFRA": "인프라",
    "DBA": "DBA",
}

# 직군별 실제 평가 관점. 라벨(persona)만 다르고 내용은 공통이던 문제를 해결하기 위해
# 직군마다 실무에서 중요도가 높은 관점을 명시한다. 목록에 없는 직군은 _TECH_GUIDE로 대체.
_DOMAIN_TECH_GUIDE = {
    "FRONTEND": (
        "- 렌더링·상태관리 설계, 성능(리렌더링·번들·네트워크) trade-off, 접근성·크로스브라우저 "
        "대응 근거를 봅니다. 질문의 '기대 신호'를 답변이 얼마나 짚었는지를 핵심 근거로 삼습니다."
    ),
    "BACKEND": (
        "- API·도메인 설계, 동시성·트랜잭션 처리, 장애 격리와 데이터 일관성 확보 근거를 "
        "봅니다. 질문의 '기대 신호'를 답변이 얼마나 짚었는지를 핵심 근거로 삼습니다."
    ),
    "INFRA": (
        "- 배포·오케스트레이션 설계, 모니터링·장애 대응 체계, 스케일링·비용 trade-off를 "
        "봅니다. 질문의 '기대 신호'를 답변이 얼마나 짚었는지를 핵심 근거로 삼습니다."
    ),
    "DBA": (
        "- 인덱스·쿼리 실행계획 분석, 정규화·비정규화 trade-off, 트랜잭션 격리수준과 "
        "백업·복구 전략을 봅니다. 질문의 '기대 신호'를 답변이 얼마나 짚었는지를 핵심 근거로 삼습니다."
    ),
}


def _tech_guide_for(job_category: str) -> str:
    return _DOMAIN_TECH_GUIDE.get((job_category or "").upper(), _TECH_GUIDE)


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
    ko = _DOMAIN_KO.get((job_category or "").upper(), job_category)
    return _EvaluatorSpec(
        key="technical",
        label="기술",
        persona=f"{ko} 직군 시니어 기술 면접관",
        dimension_name="기술 정확도·깊이",
        dimension_guide=_tech_guide_for(job_category),
    )


def _domain_specs_weighted(
    job_category: str, mode: str, domain_question_counts: dict[str, int]
) -> list[tuple[_EvaluatorSpec, float]]:
    """직군별 기술 평가위원 + 가중치(질문 수). PERSONALITY/단일/레거시는 단일 평가위원."""
    if (mode or "").upper() == "PERSONALITY" or not domain_question_counts:
        return [(_domain_spec(job_category, mode), 1.0)]
    specs: list[tuple[_EvaluatorSpec, float]] = []
    for dom, cnt in domain_question_counts.items():
        ko = _DOMAIN_KO.get((dom or "").upper(), dom)
        weight = float(cnt) if cnt and cnt > 0 else 1.0
        specs.append(
            (
                _EvaluatorSpec(
                    key=f"tech:{dom}",
                    label=ko,
                    persona=f"{ko} 직군 시니어 기술 면접관",
                    dimension_name="기술 정확도·깊이",
                    dimension_guide=_tech_guide_for(dom),
                ),
                weight,
            )
        )
    return specs or [(_domain_spec(job_category, mode), 1.0)]


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


def build_feedback_synthesis_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    """패널 결과를 통합한 종합 서술형 평 + 학습 방향 생성 체인."""
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=SynthesisResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", feedback_synthesis.SYSTEM_PROMPT),
            ("human", feedback_synthesis.HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.feedback.synthesis",
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


# ── 자기소개(첫인상) 평가 ─────────────────────────────────────────────────────
# 모든 면접의 첫 질문은 자기소개다. 기술 채점이 아닌 첫인상·전달력만 별도로 평가해
# 패널의 '첫인상' 항목으로 표시한다. 종합 점수 집계에는 포함하지 않는다(별도 정성 평가).

SELF_INTRO_EVALUATOR_LABEL = "첫인상"
SELF_INTRO_DIMENSION = "자기소개 전달력·구성·직무적합성"


def build_self_intro_evaluation_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    """자기소개 답변 1건을 첫인상(전달력·구조·간결성·직무적합성)으로 평가하는 경량 체인(Flash)."""
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=EvaluatorResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", self_intro_evaluation.SYSTEM_PROMPT),
            ("human", self_intro_evaluation.HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.feedback.self_intro",
                default_model=settings.llm_flash_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_flash_model,
        temperature=settings.llm_flash_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return prompt | llm | parser


PERSONALITY_EVALUATOR_LABEL = "인성·자소서"
PERSONALITY_DIMENSION = "자소서 소유·인성 답변 구체성/STAR"


def build_personality_evaluation_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    """인성·자소서 답변(경험형·BEHAVIORAL)을 기술 축과 별개로 평가하는 경량 체인(Flash)."""
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=EvaluatorResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", personality_evaluation.SYSTEM_PROMPT),
            ("human", personality_evaluation.HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.feedback.personality",
                default_model=settings.llm_flash_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_flash_model,
        temperature=settings.llm_flash_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return prompt | llm | parser


class PersonalityEvaluator(Protocol):
    async def evaluate(
        self,
        *,
        job_category: str,
        mode: str,
        transcript: str,
    ) -> EvaluatorResult: ...


class LlmPersonalityEvaluator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def evaluate(
        self,
        *,
        job_category: str,
        mode: str,
        transcript: str,
    ) -> EvaluatorResult:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "mode": mode,
                "transcript": transcript or "(빈 답변)",
            }
        )
        if not isinstance(result, EvaluatorResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected EvaluatorResult"
            )
        return result


class SelfIntroEvaluator(Protocol):
    async def evaluate(
        self,
        *,
        job_category: str,
        mode: str,
        self_intro_question: str,
        self_intro_answer: str,
        voice_analysis_summary: str = "",
    ) -> EvaluatorResult: ...


class LlmSelfIntroEvaluator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def evaluate(
        self,
        *,
        job_category: str,
        mode: str,
        self_intro_question: str,
        self_intro_answer: str,
        voice_analysis_summary: str = "",
    ) -> EvaluatorResult:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "mode": mode,
                "self_intro_question": self_intro_question or "자기소개를 해주세요.",
                "self_intro_answer": self_intro_answer or "(빈 답변)",
                "voice_analysis_summary": voice_analysis_summary
                or "No voice analysis summary was provided.",
            }
        )
        if not isinstance(result, EvaluatorResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected EvaluatorResult"
            )
        return result


# ── 직무 적합도 + 직무 이해도 평가 (직무 맞춤 모드 전용) ───────────────────────
# 면접의 핵심은 직무 적합성이므로 두 축을 분리해 평가한다(한 번의 호출로 구조화 출력):
#   · 직무 적합도(fit)  — JD 요구 기술·경험·책임을 실제로 갖췄는가(역량 매칭).
#   · 직무 이해도(understanding) — 직무가 무엇을 하는 자리인지·핵심 책임을 이해하고 동기로 연결했는가.
# 둘 다 패널 항목으로 표시하되 종합 점수 집계에는 포함하지 않는다(별도 정성 평가).

JOB_FIT_EVALUATOR_LABEL = "직무 적합도"
JOB_FIT_DIMENSION = "채용공고(JD) 요구 대비 역량 적합도·갭"
ROLE_UNDERSTANDING_LABEL = "직무 이해도"
ROLE_UNDERSTANDING_DIMENSION = "직무 이해·지원동기 연결"


class JobFitResult(BaseModel):
    """직무 맞춤 평가의 두 축. 각 축은 EvaluatorResult 형태(score/strength/weakness/detail/rationale)."""

    fit: EvaluatorResult = Field(default_factory=EvaluatorResult)
    understanding: EvaluatorResult = Field(default_factory=EvaluatorResult)


def build_job_fit_evaluation_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    """면접 답변·자료를 JD 와 대조해 직무 적합도·직무 이해도를 함께 평가하는 체인(Pro — 갭 추론)."""
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=JobFitResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", job_fit_evaluation.SYSTEM_PROMPT),
            ("human", job_fit_evaluation.HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.feedback.job_fit",
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


class JobFitEvaluator(Protocol):
    async def evaluate(
        self,
        *,
        company_name: str,
        job_description: str,
        job_category: str,
        mode: str,
        transcript: str,
        rag_context: str = "(none)",
    ) -> JobFitResult: ...


class LlmJobFitEvaluator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def evaluate(
        self,
        *,
        company_name: str,
        job_description: str,
        job_category: str,
        mode: str,
        transcript: str,
        rag_context: str = "(none)",
    ) -> JobFitResult:
        result = await self._chain.ainvoke(
            {
                "company_name": company_name or "(회사명 미입력)",
                "job_description": job_description or "(JD 본문 없음)",
                "job_category": job_category,
                "mode": mode,
                "transcript": transcript,
                "rag_context": rag_context or "(none)",
            }
        )
        if not isinstance(result, JobFitResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected JobFitResult"
            )
        return result


# ── 질문별 복기 (답변 코칭) ───────────────────────────────────────────────────
# 답변 1건당 모범 답안 + 리라이트 + 한 줄 코칭. 점수가 아니라 "어떻게 더 잘하는지"를 준다.
# 답변별 병렬 호출(Flash). 자기소개 답변은 제외(첫인상 평가가 커버).


class CoachingResult(BaseModel):
    model_answer: str | None = Field(None, description="이 질문에 대한 강한 답변 예시")
    answer_rewrite: str | None = Field(
        None, description="지원자 답변을 더 좋게 고쳐 쓴 버전"
    )
    coaching_comment: str | None = Field(None, description="가장 중요한 보완점 한 문장")


def build_answer_coaching_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    """답변 1건을 코칭(모범 답안·리라이트·한 줄 코칭)하는 체인(Flash — 답변 수만큼 병렬)."""
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=CoachingResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", answer_coaching.SYSTEM_PROMPT),
            ("human", answer_coaching.HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.feedback.coaching",
                default_model=settings.llm_flash_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_flash_model,
        temperature=settings.llm_flash_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return prompt | llm | parser


class AnswerCoach(Protocol):
    async def coach(
        self,
        *,
        job_category: str,
        mode: str,
        target_role: str,
        question: str,
        expected_signal: str,
        answer: str,
        rag_context: str = "(none)",
    ) -> CoachingResult: ...


class LlmAnswerCoach:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def coach(
        self,
        *,
        job_category: str,
        mode: str,
        target_role: str,
        question: str,
        expected_signal: str,
        answer: str,
        rag_context: str = "(none)",
    ) -> CoachingResult:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "mode": mode,
                "target_role": target_role or "",
                "question": question,
                "expected_signal": expected_signal or "(명시 없음)",
                "answer": answer or "(빈 답변)",
                "rag_context": rag_context or "(none)",
            }
        )
        if not isinstance(result, CoachingResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected CoachingResult"
            )
        return result


def _weighted_overall(pairs: list[tuple[float | None, float]]) -> float | None:
    """(score, weight) 중 score 가 있는 것만 가중평균. 전부 None 이면 None."""
    present = [(s, w) for s, w in pairs if s is not None and w > 0]
    if not present:
        return None
    total_w = sum(w for _, w in present)
    return round(sum(s * w for s, w in present) / total_w)


def _merge_notes(items: list[tuple[str, str | None]]) -> str | None:
    parts = [
        f"[{label}] {note.strip()}" for label, note in items if note and note.strip()
    ]
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


def _filter_highlights(
    highlights: list[str],
    strengths: str | None,
    weaknesses: str | None,
    cap: int = 6,
) -> list[str]:
    """LLM 이 뽑은 핵심 구절 중 강점·개선 본문에 실제로 등장하는 것만 남긴다.
    프론트가 부분 문자열 매칭으로 하이라이트하므로, 본문에 없는(지어낸/바꿔쓴) 구절은 버려
    매칭 실패를 막는다. 중복 제거 + cap 개로 제한."""
    corpus = f"{strengths or ''}\n{weaknesses or ''}"
    seen: set[str] = set()
    out: list[str] = []
    for h in highlights:
        phrase = (h or "").strip()
        if len(phrase) < 2 or phrase in seen:
            continue
        if phrase in corpus:
            seen.add(phrase)
            out.append(phrase)
        if len(out) >= cap:
            break
    return out


class PanelFeedbackGenerator:
    """직군·논리·커뮤니케이션 평가위원을 병렬 호출 → 가중평균 종합. FeedbackGenerator 호환."""

    def __init__(
        self,
        chain: Runnable,
        *,
        synthesis_chain: Runnable | None = None,
        weights: tuple[float, float, float] = (0.5, 0.25, 0.25),
    ) -> None:
        self._chain = chain
        self._synthesis = synthesis_chain
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
        domain_question_counts: dict[str, int] | None = None,
    ) -> FeedbackResult:
        domain_specs = _domain_specs_weighted(
            job_category, mode, domain_question_counts or {}
        )
        # 평가위원 순서: 직군 기술 평가위원(N) + 논리 + 전달.
        specs = [s for s, _ in domain_specs] + [_LOGIC_SPEC, _COMM_SPEC]
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

        # 위치 기준 매핑(직군이 여러 개라 key 가 겹치지 않게).
        results: list[EvaluatorResult] = []
        for spec, r in zip(specs, raw):
            if isinstance(r, EvaluatorResult):
                results.append(r)
            else:
                log.warning(
                    "feedback.panel.evaluator_failed", evaluator=spec.key, error=str(r)
                )
                results.append(EvaluatorResult())

        n_domain = len(domain_specs)
        domain_results = list(
            zip(
                [s for s, _ in domain_specs],
                [w for _, w in domain_specs],
                results[:n_domain],
            )
        )
        logic = results[n_domain]
        comm = results[n_domain + 1]

        # technical_accuracy = 직군 평가위원 점수의 질문수 가중평균.
        technical_accuracy = _weighted_overall(
            [(r.score, w) for _, w, r in domain_results]
        )
        overall = _weighted_overall(
            [
                (technical_accuracy, self._w_tech),
                (logic.score, self._w_logic),
                (comm.score, self._w_comm),
            ]
        )

        # 기계적 병합(synthesis 미설정/실패 시 폴백용).
        fb_strength_items = [(spec.label, r.strength) for spec, _, r in domain_results]
        fb_strength_items += [("논리", logic.strength), ("전달", comm.strength)]
        fb_strengths = _merge_notes(fb_strength_items)
        fb_weak_items = [(spec.label, r.weakness) for spec, _, r in domain_results]
        fb_weak_items += [("논리", logic.weakness), ("전달", comm.weakness)]
        fb_weaknesses = _merge_notes(fb_weak_items)
        fb_keywords = _dedup_keywords(
            [k for _, _, r in domain_results for k in r.keywords]
            + logic.keywords
            + comm.keywords
        )

        breakdown = [
            PanelBreakdownItem(
                evaluator=spec.label,
                dimension=spec.dimension_name,
                score=r.score,
                strength=r.strength,
                weakness=r.weakness,
                detail=r.detail,
                score_rationale=r.score_rationale,
            )
            for spec, _, r in domain_results
        ]
        for spec, r in ((_LOGIC_SPEC, logic), (_COMM_SPEC, comm)):
            breakdown.append(
                PanelBreakdownItem(
                    evaluator=spec.label,
                    dimension=spec.dimension_name,
                    score=r.score,
                    strength=r.strength,
                    weakness=r.weakness,
                    detail=r.detail,
                    score_rationale=r.score_rationale,
                )
            )

        # 종합 서술형 + 학습 방향(synthesis). 미설정/실패 시 기계적 병합으로 폴백.
        # highlights 는 synthesis 가 만든 strengths/weaknesses 본문에서 발췌되므로 synthesis 가
        # 없거나 실패하면 빈 리스트(불일치 하이라이트 방지).
        strengths, weaknesses, keywords, study_plan, highlights = (
            fb_strengths,
            fb_weaknesses,
            fb_keywords,
            [],
            [],
        )
        if self._synthesis is not None:
            panel_summary = "\n".join(
                f"- {b.evaluator}({b.dimension}) "
                f"{('%g점' % b.score) if b.score is not None else '점수없음'}: "
                f"{(b.detail or '').strip() or (b.strength or '')}"
                for b in breakdown
            )
            try:
                syn = await self._synthesis.ainvoke(
                    {
                        "job_category": job_category,
                        "mode": mode,
                        "panel_summary": panel_summary,
                        "transcript": transcript,
                    }
                )
                if isinstance(syn, SynthesisResult):
                    strengths = syn.strengths_summary or fb_strengths
                    weaknesses = syn.weaknesses_summary or fb_weaknesses
                    keywords = _dedup_keywords(syn.improvement_keywords) or fb_keywords
                    study_plan = syn.study_plan or []
                    highlights = _filter_highlights(
                        syn.highlights, strengths, weaknesses
                    )
            except Exception as exc:  # noqa: BLE001
                log.warning("feedback.synthesis.failed", error=str(exc))

        return FeedbackResult(
            overall_score=overall,
            technical_accuracy=technical_accuracy,
            logic_score=logic.score,
            communication_score=comm.score,
            strengths_summary=strengths,
            weaknesses_summary=weaknesses,
            improvement_keywords=keywords,
            study_plan=study_plan,
            highlights=highlights,
            panel_breakdown=breakdown,
        )
