from __future__ import annotations

from typing import Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.question_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
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
        "기술 정확도 — 개념을 정확히 아는지 파고드는 질문. 사용한 기술의 동작 원리·트레이드오프·"
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
            }
        )
        if not isinstance(result, GeneratedQuestionPool):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected GeneratedQuestionPool"
            )
        return result


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
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return prompt | llm | parser
