from __future__ import annotations

from typing import Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.followup_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.model.messages.followup import AnswerEvaluation
from ai_server.observability.llm_logging_callback import CoreAiLogCallback


class FollowupResult(BaseModel):
    followup_question: str = Field(..., description="한국어 꼬리질문 1개")
    answer_evaluation: AnswerEvaluation


class FollowupGenerator(Protocol):
    async def generate(
        self,
        *,
        job_category: str,
        mode: str,
        previous_question: str,
        answer_text: str,
        context: str = "(none)",
    ) -> FollowupResult: ...


class LlmFollowupGenerator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def generate(
        self,
        *,
        job_category: str,
        mode: str,
        previous_question: str,
        answer_text: str,
        context: str = "(none)",
    ) -> FollowupResult:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "mode": mode,
                "previous_question": previous_question,
                "answer_text": answer_text,
                "context": context,
            }
        )
        if not isinstance(result, FollowupResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected FollowupResult"
            )
        return result


def build_followup_generation_chain(settings: Settings, core_client: CoreClient | None = None) -> Runnable:
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=FollowupResult)
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
            request_type="generate.followup",
            default_model=settings.llm_flash_model,
        ))

    llm = ChatOpenAI(
        model=settings.llm_flash_model,
        temperature=settings.llm_flash_temperature,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        max_tokens=settings.llm_flash_max_tokens,
        callbacks=callbacks,
    )
    return prompt | llm | parser
