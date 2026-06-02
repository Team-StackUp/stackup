from __future__ import annotations

from typing import Protocol

import structlog
from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.rerank import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.observability.llm_logging_callback import CoreAiLogCallback

log = structlog.get_logger(__name__)


class RerankResult(BaseModel):
    ranked_indices: list[int] = Field(
        default_factory=list,
        description="relevant candidate indices, most relevant first",
    )


class Reranker(Protocol):
    async def rerank(
        self, *, query: str, candidates: list[str], top_k: int
    ) -> list[int]: ...


async def rerank_hits(
    reranker: Reranker, *, query: str, hits: list, top_k: int
) -> list:
    """검색 hit 리스트(.chunk_text 보유)를 query 기준으로 재정렬해 top_k 반환."""
    if not hits:
        return list(hits)
    indices = await reranker.rerank(
        query=query, candidates=[h.chunk_text for h in hits], top_k=top_k
    )
    return [hits[i] for i in indices]


def _identity(n: int, top_k: int) -> list[int]:
    return list(range(min(top_k, n)))


# 리랭킹 비활성/미주입 시 검색 순서를 그대로 사용하는 passthrough.
class NoopReranker:
    async def rerank(
        self, *, query: str, candidates: list[str], top_k: int
    ) -> list[int]:
        return _identity(len(candidates), top_k)


# LLM 으로 후보를 한 번에 재정렬한다. 실패 시 검색 순서로 graceful degrade.
class LlmReranker:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def rerank(
        self, *, query: str, candidates: list[str], top_k: int
    ) -> list[int]:
        if not candidates:
            return []
        numbered = "\n\n".join(f"[{i}] {c}" for i, c in enumerate(candidates))
        try:
            result = await self._chain.ainvoke(
                {"query": query, "candidates": numbered, "top_k": top_k}
            )
            seen: set[int] = set()
            ordered: list[int] = []
            for i in result.ranked_indices:
                if 0 <= i < len(candidates) and i not in seen:
                    seen.add(i)
                    ordered.append(i)
            if not ordered:
                raise ValueError("리랭커가 유효한 인덱스를 반환하지 않음")
            return ordered[:top_k]
        except Exception as exc:  # noqa: BLE001
            log.warning("rerank.failed_fallback_to_search_order", error=str(exc))
            return _identity(len(candidates), top_k)


def build_reranker(
    settings: Settings, core_client: CoreClient | None = None
) -> Reranker:
    if not settings.rerank_enabled:
        return NoopReranker()

    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=RerankResult)
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
                request_type="rerank",
                default_model=settings.llm_flash_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_flash_model,
        temperature=0.0,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        max_tokens=settings.llm_flash_max_tokens,
        callbacks=callbacks,
    )
    return LlmReranker(prompt | llm | parser)
