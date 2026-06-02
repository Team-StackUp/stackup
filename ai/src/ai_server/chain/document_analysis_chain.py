from __future__ import annotations

from typing import Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.analyzer.sources.base import SourceType
from ai_server.chain.prompts.document_analysis import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.observability.llm_logging_callback import CoreAiLogCallback


class Project(BaseModel):
    name: str
    role: str = ""
    contribution: str = ""
    stack: list[str] = Field(default_factory=list)
    source_quote: str = Field("", description="원문에서 그대로 따온 근거 인용")


class Experience(BaseModel):
    title: str
    detail: str = ""
    source_quote: str = Field("", description="원문 근거 인용")


class Skill(BaseModel):
    name: str
    evidence: str = Field("", description="원문 근거 인용")


class DocumentAnalysisResult(BaseModel):
    summary: str = Field(..., description="2~4 sentence Korean summary")
    tech_stack: list[str] = Field(default_factory=list)
    # 구조화 추출 (AI 내부용 — 콜백 계약에는 포함되지 않음, markdown 에 녹여 사용).
    projects: list[Project] = Field(default_factory=list)
    experiences: list[Experience] = Field(default_factory=list)
    skills: list[Skill] = Field(default_factory=list)
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


# 프롬프트 -> LLM -> 파서 하나로 묶어서 처리함.
# 스키마가 커져 파싱 실패 가능성이 있으므로 with_retry 로 1회 재시도(저비용 안전장치).
def build_document_analysis_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=DocumentAnalysisResult)
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
                request_type="analyze.document",
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
    return (prompt | llm | parser).with_retry(stop_after_attempt=2)
