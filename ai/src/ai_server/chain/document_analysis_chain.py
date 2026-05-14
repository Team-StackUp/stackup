from __future__ import annotations

from typing import Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.analyzer.sources.base import SourceType
from ai_server.chain.prompts.document_analysis import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings


class DocumentAnalysisResult(BaseModel):
    summary: str = Field(..., description="2~4 sentence Korean summary")
    tech_stack: list[str] = Field(default_factory=list)
    markdown: str = Field(..., description="Interviewer-facing markdown")


class DocumentAnalyzer(Protocol):
    async def analyze(
        self, *, text: str, source_type: SourceType
    ) -> DocumentAnalysisResult: ...


# 랭체인 파이프라인 호출을 감싼다 
class LlmDocumentAnalyzer:

    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def analyze(
        self, *, text: str, source_type: SourceType
    ) -> DocumentAnalysisResult:
        result = await self._chain.ainvoke({"text": text, "source_type": source_type})
        if not isinstance(result, DocumentAnalysisResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected DocumentAnalysisResult"
            )
        return result


# 프롬프트 -> LLM -> 파서 하나로 묶어서 처리함 
def build_document_analysis_chain(settings: Settings) -> Runnable:
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=DocumentAnalysisResult)
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
