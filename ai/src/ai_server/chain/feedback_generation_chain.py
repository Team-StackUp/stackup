from __future__ import annotations

from typing import Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.feedback_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.observability.llm_logging_callback import CoreAiLogCallback


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
    ) -> FeedbackResult:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "mode": mode,
                "total_question_count": total_question_count or 0,
                "end_reason": end_reason or "USER_REQUEST",
                "transcript": transcript,
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
