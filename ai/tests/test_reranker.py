from dataclasses import dataclass

import pytest

from ai_server.rag.reranker import (
    LlmReranker,
    NoopReranker,
    RerankResult,
    rerank_hits,
)


@dataclass
class _Hit:
    chunk_text: str


class _FakeChain:
    def __init__(self, result=None, *, raises: Exception | None = None) -> None:
        self._result = result
        self._raises = raises

    async def ainvoke(self, _inputs):
        if self._raises is not None:
            raise self._raises
        return self._result


@pytest.mark.asyncio
async def test_noop_reranker_returns_identity_truncated() -> None:
    r = NoopReranker()
    assert await r.rerank(query="q", candidates=["a", "b", "c"], top_k=2) == [0, 1]


@pytest.mark.asyncio
async def test_llm_reranker_reorders_by_returned_indices() -> None:
    chain = _FakeChain(RerankResult(ranked_indices=[2, 0]))
    r = LlmReranker(chain)
    assert await r.rerank(query="q", candidates=["a", "b", "c"], top_k=2) == [2, 0]


@pytest.mark.asyncio
async def test_llm_reranker_filters_out_of_range_and_dupes() -> None:
    chain = _FakeChain(RerankResult(ranked_indices=[1, 1, 9, -1, 0]))
    r = LlmReranker(chain)
    # 중복/범위밖 제거 후 순서 유지
    assert await r.rerank(query="q", candidates=["a", "b", "c"], top_k=5) == [1, 0]


@pytest.mark.asyncio
async def test_llm_reranker_falls_back_to_search_order_on_failure() -> None:
    chain = _FakeChain(raises=RuntimeError("llm down"))
    r = LlmReranker(chain)
    assert await r.rerank(query="q", candidates=["a", "b", "c"], top_k=2) == [0, 1]


@pytest.mark.asyncio
async def test_llm_reranker_empty_indices_falls_back() -> None:
    chain = _FakeChain(RerankResult(ranked_indices=[]))
    r = LlmReranker(chain)
    assert await r.rerank(query="q", candidates=["a", "b"], top_k=2) == [0, 1]


@pytest.mark.asyncio
async def test_rerank_hits_maps_indices_back_to_hits() -> None:
    chain = _FakeChain(RerankResult(ranked_indices=[2, 0]))
    r = LlmReranker(chain)
    hits = [_Hit("zero"), _Hit("one"), _Hit("two")]
    out = await rerank_hits(r, query="q", hits=hits, top_k=2)
    assert [h.chunk_text for h in out] == ["two", "zero"]


@pytest.mark.asyncio
async def test_rerank_hits_empty_returns_empty() -> None:
    out = await rerank_hits(NoopReranker(), query="q", hits=[], top_k=5)
    assert out == []
