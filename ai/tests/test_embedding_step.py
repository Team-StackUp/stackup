from unittest.mock import AsyncMock

import pytest

from ai_server.analyzer._embedding_step import _contextualize, chunk_embed_and_upsert
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import MockEmbeddingProvider


def test_contextualize_prefixes_summary_and_heading() -> None:
    out = _contextualize(
        "본문 내용", heading_path="주요 경험 > 결제", summary="백엔드 지원자"
    )
    assert out.startswith("[백엔드 지원자 > 주요 경험 > 결제]")
    assert out.endswith("본문 내용")


def test_contextualize_without_context_returns_raw() -> None:
    assert _contextualize("본문", heading_path="", summary="") == "본문"


def test_contextualize_truncates_long_summary() -> None:
    out = _contextualize("c", heading_path="", summary="가" * 500)
    assert "…" in out
    # 프리픽스 길이가 요약 상한 + 여유 이내
    assert len(out) < 500


@pytest.mark.asyncio
async def test_chunk_embed_and_upsert_stores_contextualized_text() -> None:
    core = AsyncMock()
    core.upsert_embeddings = AsyncMock(return_value=2)
    md = "## 주요 경험\n" + ("결제 시스템 동시성 분산락. " * 40)

    await chunk_embed_and_upsert(
        document_id=7,
        markdown=md,
        chunker=MarkdownChunker(chunk_size=200, chunk_overlap=40),
        embedder=MockEmbeddingProvider(dim=8),
        core_client=core,
        summary="백엔드 면접 지원자",
    )

    core.upsert_embeddings.assert_awaited_once()
    chunks = core.upsert_embeddings.await_args.kwargs["chunks"]
    assert len(chunks) > 0
    # 저장되는 chunk_text 가 문맥 프리픽스를 포함
    assert chunks[0].chunk_text.startswith("[백엔드 면접 지원자 > 주요 경험]")


@pytest.mark.asyncio
async def test_chunk_embed_and_upsert_empty_markdown_skips() -> None:
    core = AsyncMock()
    core.upsert_embeddings = AsyncMock()
    n = await chunk_embed_and_upsert(
        document_id=1,
        markdown="   \n  ",
        chunker=MarkdownChunker(chunk_size=100, chunk_overlap=10),
        embedder=MockEmbeddingProvider(dim=8),
        core_client=core,
        summary="x",
    )
    assert n == 0
    core.upsert_embeddings.assert_not_called()
