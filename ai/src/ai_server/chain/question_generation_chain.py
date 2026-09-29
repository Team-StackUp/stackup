from __future__ import annotations

from typing import Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.question_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.model.messages.job_category import is_engineering
from ai_server.model.messages.questions import GeneratedQuestion
from ai_server.observability.llm_logging_callback import CoreAiLogCallback


class GeneratedQuestionPool(BaseModel):
    questions: list[GeneratedQuestion] = Field(default_factory=list)


def _format_recent_questions(recent_questions: list[str] | None) -> str:
    if not recent_questions:
        return "(없음)"
    return "\n".join(f"- {q}" for q in recent_questions)


def _format_self_introduction(self_introduction: str | None) -> str:
    text = (self_introduction or "").strip()
    return text if text else "(자기소개 없음)"


# 평가 축 → 질문 설계 지침. 라벨만 넘기면 LLM 이 제각각 해석하므로 관점을 명시한다.
_FOCUS_GUIDE = {
    "TECHNICAL": (
        "직무 역량 — 개념·원리를 정확히 아는지 파고드는 질문. 개발 직군이면 사용한 기술의 동작 원리·트레이드오프·"
        "대안과의 비교를 묻고, 뭉뚱그린 답으로는 통과할 수 없게 구체적인 지점을 짚으세요."
    ),
    "LOGIC": (
        "논리력 — 결론에 이른 근거와 판단 과정을 드러내게 하는 질문. 왜 그 선택이었는지, 다른 안을 "
        "왜 버렸는지, 가정이 틀렸다면 어떻게 되는지를 묻습니다."
    ),
    "COMMUNICATION": (
        "전달력 — 복잡한 내용을 상대 눈높이로 설명하게 하는 질문. 비전공자/신입에게 설명하기, "
        "설계 의도를 짧게 요약하기처럼 구조적으로 말해야 풀리는 질문을 섞으세요."
    ),
}


def _format_focus_areas(focus_areas: list[str] | None) -> str:
    """약점 집중 블록. 지정이 없으면 일반 면접 안내."""
    if not focus_areas:
        return "(지정 없음 — 특정 영역에 치우치지 말고 자료가 받쳐주는 대로 출제.)"
    lines = [
        "지난 면접에서 아래 영역의 점수가 낮았습니다. 이번 면접은 그 영역을 다시 검증합니다:"
    ]
    for area in focus_areas:
        guide = _FOCUS_GUIDE.get(area)
        lines.append(f"- {area}: {guide}" if guide else f"- {area}")
    return "\n".join(lines)


def _format_target_role(company_name: str | None, job_description: str | None) -> str:
    """직무 맞춤 모드의 타깃 회사/JD 블록. 둘 다 없으면 일반 면접 안내."""
    company = (company_name or "").strip()
    jd = (job_description or "").strip()
    if not company and not jd:
        return (
            "(일반 면접 — 특정 회사/직무 지정 없음. 지원자 자료와 자기소개만으로 출제.)"
        )
    lines = []
    if company:
        lines.append(f"지원 회사: {company}")
    lines.append("채용공고(JD):")
    lines.append(jd if jd else "(JD 본문 없음)")
    return "\n".join(lines)


class QuestionGenerator(Protocol):
    async def generate(
        self,
        *,
        job_categories: list[str],
        mode: str,
        max_questions: int,
        context: str,
        recent_questions: list[str] | None = None,
        self_introduction: str | None = None,
        target_company_name: str | None = None,
        target_job_description: str | None = None,
        focus_areas: list[str] | None = None,
        industry: str | None = None,
    ) -> GeneratedQuestionPool: ...


class LlmQuestionGenerator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def generate(
        self,
        *,
        job_categories: list[str],
        mode: str,
        max_questions: int,
        context: str,
        recent_questions: list[str] | None = None,
        self_introduction: str | None = None,
        target_company_name: str | None = None,
        target_job_description: str | None = None,
        focus_areas: list[str] | None = None,
        industry: str | None = None,
    ) -> GeneratedQuestionPool:
        result = await self._chain.ainvoke(
            {
                "job_categories": ", ".join(job_categories),
                "mode": mode,
                "max_questions": max_questions,
                "context": context,
                "recent_questions": _format_recent_questions(recent_questions),
                "self_introduction": _format_self_introduction(self_introduction),
                "target_role": _format_target_role(
                    target_company_name, target_job_description
                ),
                "focus_areas": _format_focus_areas(focus_areas),
                "job_guide": _format_job_guide(job_categories),
                # 비어 있으면 "(지정 없음)" — 프롬프트가 산업 지침을 무시하도록.
                "industry": (industry or "").strip() or "(지정 없음)",
            }
        )
        if not isinstance(result, GeneratedQuestionPool):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected GeneratedQuestionPool"
            )
        return result


# 직군 지침. 개발/비개발은 묻는 것이 다르고, 특히 **카테고리 자체가 개발 전용**이다
# (CS_FUNDAMENTAL·TECH_CHOICE). 이걸 주지 않으면 영업·인사 지원자에게 'CS 기초' 질문이 나간다.
# 라벨만 넘기면 LLM 이 제각각 해석하므로, 패널 평가의 `_DOMAIN_TECH_GUIDE` 와 같이
# **무엇을 묻고 무엇을 묻지 말지**를 문장으로 못 박는다.
_ENGINEERING_GUIDE = (
    "개발 직군입니다. 기술 스택·설계 선택·트러블슈팅을 중심으로 묻고, "
    "CS_FUNDAMENTAL·TECH_CHOICE·PROJECT_DEEP_DIVE 를 적극 활용합니다. "
    "DOMAIN_KNOWLEDGE 는 쓰지 않습니다."
)
_NON_ENGINEERING_GUIDE = (
    "비개발 직군입니다.\n"
    "- 코드·아키텍처·기술 스택을 묻지 않습니다. **CS_FUNDAMENTAL·TECH_CHOICE 를 쓰지 마세요.**\n"
    "- 대신 DOMAIN_KNOWLEDGE(직무 지식·업무 도구·업계 이해)와 PROJECT_DEEP_DIVE(담당 업무·"
    "성과), BEHAVIORAL 을 씁니다.\n"
    "- '역량·도구'는 그 직군의 실무를 뜻합니다 — 예: 마케팅이면 퍼포먼스 지표·GA4, 재무면 결산·"
    "세무, 생산·품질이면 공정·불량률, 법무면 계약 검토, 디자인이면 사용자 리서치, "
    "인사면 채용·평가·노무.\n"
    "- 성과는 숫자로 확인합니다(전환율·불량률·원가 절감·처리 건수·리드타임 등).\n"
    "- 자료에 GitHub 레포가 없는 것이 정상입니다. 없는 자료를 전제로 묻지 마세요."
)
_MIXED_GUIDE = (
    "개발 직군과 비개발 직군이 함께 지정됐습니다. **질문마다 그 질문이 겨냥하는 직군의 규칙을 "
    "따릅니다** — 개발 직군 질문에만 CS_FUNDAMENTAL·TECH_CHOICE 를 쓰고, 비개발 직군 질문에는 "
    "DOMAIN_KNOWLEDGE 를 씁니다.\n\n"
    "[개발] " + _ENGINEERING_GUIDE + "\n\n[비개발] " + _NON_ENGINEERING_GUIDE
)


def _format_job_guide(job_categories: list[str] | None) -> str:
    cats = [c for c in (job_categories or []) if c]
    if not cats:
        # 직군이 비는 경로는 없지만, 비면 개발을 가정하지 않는다 — 가정이 틀렸을 때
        # 비개발 지원자에게 기술 질문이 나가는 쪽이 반대보다 나쁘다.
        return _NON_ENGINEERING_GUIDE
    eng = [c for c in cats if is_engineering(c)]
    non = [c for c in cats if not is_engineering(c)]
    if eng and non:
        return _MIXED_GUIDE
    return _ENGINEERING_GUIDE if eng else _NON_ENGINEERING_GUIDE


def build_question_generation_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=GeneratedQuestionPool)
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
                request_type="generate.questions",
                default_model=settings.llm_pro_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_pro_model,
        temperature=settings.llm_pro_temperature,
        timeout=settings.llm_pro_timeout_sec,
        max_retries=settings.llm_max_retries,
        api_key=settings.llm_api_key_for("pro"),
        base_url=settings.llm_base_url_for("pro"),
        callbacks=callbacks,
    )
    return prompt | llm | parser
