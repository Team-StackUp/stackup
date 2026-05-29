from __future__ import annotations

from typing import Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.question_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.model.messages.questions import GeneratedQuestion


class GeneratedQuestionPool(BaseModel):
    questions: list[GeneratedQuestion] = Field(default_factory=list)


class QuestionGenerator(Protocol):
    async def generate(
        self,
        *,
        job_category: str,
        interview_type: str,
        max_questions: int,
        context: str,
    ) -> GeneratedQuestionPool: ...


class LlmQuestionGenerator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def generate(
        self,
        *,
        job_category: str,
        interview_type: str,
        max_questions: int,
        context: str,
    ) -> GeneratedQuestionPool:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "interview_type": interview_type,
                "max_questions": max_questions,
                "context": context,
            }
        )
        if not isinstance(result, GeneratedQuestionPool):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected GeneratedQuestionPool"
            )
        return result


def build_question_generation_chain(settings: Settings) -> Runnable:
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=GeneratedQuestionPool)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", SYSTEM_PROMPT),
            ("human", HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    llm = ChatOpenAI(
        model=settings.llm_pro_model,
        temperature=settings.llm_pro_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
    )
    return prompt | llm | parser
