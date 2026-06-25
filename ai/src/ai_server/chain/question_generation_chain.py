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
    ) -> GeneratedQuestionPool:
        result = await self._chain.ainvoke(
            {
                "job_categories": ", ".join(job_categories),
                "mode": mode,
                "max_questions": max_questions,
                "context": context,
                "recent_questions": _format_recent_questions(recent_questions),
                "self_introduction": _format_self_introduction(self_introduction),
            }
        )
        if not isinstance(result, GeneratedQuestionPool):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected GeneratedQuestionPool"
            )
        return result


def build_question_generation_chain(settings: Settings, core_client: CoreClient | None = None) -> Runnable:
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
        callbacks.append(CoreAiLogCallback(
            core_client=core_client,
            request_type="generate.questions",
            default_model=settings.llm_pro_model,
        ))

    llm = ChatOpenAI(
        model=settings.llm_pro_model,
        temperature=settings.llm_pro_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return prompt | llm | parser
