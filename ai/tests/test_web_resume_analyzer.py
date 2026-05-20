from unittest.mock import AsyncMock

import pytest

from ai_server.analyzer.sources.base import ExtractedSource
from ai_server.analyzer.sources.web import WebFetchError
from ai_server.analyzer.web_resume_analyzer import (
    WebResumeAnalyzeError,
    WebResumeAnalyzer,
)
from ai_server.chain.document_analysis_chain import DocumentAnalysisResult
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import MockEmbeddingProvider


def _make_analyzer(
    *,
    extract_result: ExtractedSource | Exception | None = None,
    analysis: DocumentAnalysisResult | None = None,
    upsert_result: int = 2,
) -> tuple[WebResumeAnalyzer, AsyncMock, AsyncMock, AsyncMock, AsyncMock]:
    extractor = AsyncMock()
    if isinstance(extract_result, Exception):
        extractor.extract = AsyncMock(side_effect=extract_result)
    else:
        extractor.extract = AsyncMock(return_value=extract_result)
    chain = AsyncMock()
    if analysis is not None:
        chain.analyze = AsyncMock(return_value=analysis)
    storage = AsyncMock()

    core_client = AsyncMock()
    core_client.upsert_embeddings = AsyncMock(return_value=upsert_result)

    analyzer = WebResumeAnalyzer(
        extractor=extractor,
        chain=chain,
        storage=storage,
        chunker=MarkdownChunker(chunk_size=200, chunk_overlap=50),
        embedder=MockEmbeddingProvider(dim=16),
        core_client=core_client,
        analyzed_key_template="analyzed/web-resume/{resume_id}/summary.md",
    )
    return analyzer, extractor, chain, storage, core_client


@pytest.mark.asyncio
async def test_happy_path() -> None:
    analyzer, extractor, chain, storage, core_client = _make_analyzer(
        extract_result=ExtractedSource(text="본문" * 30, source_type="WEB"),
        analysis=DocumentAnalysisResult(
            summary="요약", tech_stack=["TypeScript"], markdown="## 개요\nweb"
        ),
        upsert_result=2,
    )
    result = await analyzer.analyze(
        resume_id=11, url="https://example.com/me", analyzed_document_id=99
    )

    extractor.extract.assert_awaited_once_with("https://example.com/me")
    chain.analyze.assert_awaited_once()
    storage.put_text.assert_awaited_once()
    core_client.upsert_embeddings.assert_awaited_once()
    assert core_client.upsert_embeddings.await_args.kwargs["document_id"] == 99
    assert result.summary == "요약"
    assert result.embedding_chunk_count == 2
    assert result.document_path == "analyzed/web-resume/11/summary.md"


@pytest.mark.asyncio
async def test_fetch_error_translates_to_domain_error() -> None:
    analyzer, _, chain, storage, core_client = _make_analyzer(
        extract_result=WebFetchError(
            code="WEB_HTTP_STATUS", message="404", retriable=False
        ),
    )
    with pytest.raises(WebResumeAnalyzeError) as exc_info:
        await analyzer.analyze(
            resume_id=1, url="https://example.com/x", analyzed_document_id=1
        )
    assert exc_info.value.code == "WEB_HTTP_STATUS"
    chain.analyze.assert_not_called()
    storage.put_text.assert_not_called()
    core_client.upsert_embeddings.assert_not_called()
