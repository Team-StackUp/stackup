from unittest.mock import AsyncMock

import pytest

from ai_server.analyzer.resume_analyzer import ResumeAnalyzeError, ResumeAnalyzer
from ai_server.analyzer.sources.base import ExtractedSource
from ai_server.chain.document_analysis_chain import DocumentAnalysisResult
from ai_server.core.client import CoreEmbeddingUpsertError
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import MockEmbeddingProvider


def _make_analyzer(
    *,
    extracted: ExtractedSource,
    analysis: DocumentAnalysisResult | None = None,
    upsert_result: int | Exception = 3,
) -> tuple[ResumeAnalyzer, AsyncMock, AsyncMock, AsyncMock, AsyncMock]:
    extractor = AsyncMock()
    extractor.extract = AsyncMock(return_value=extracted)
    chain = AsyncMock()
    if analysis is not None:
        chain.analyze = AsyncMock(return_value=analysis)
    storage = AsyncMock()

    core_client = AsyncMock()
    if isinstance(upsert_result, Exception):
        core_client.upsert_embeddings = AsyncMock(side_effect=upsert_result)
    else:
        core_client.upsert_embeddings = AsyncMock(return_value=upsert_result)

    analyzer = ResumeAnalyzer(
        extractor=extractor,
        chain=chain,
        storage=storage,
        chunker=MarkdownChunker(chunk_size=200, chunk_overlap=50),
        embedder=MockEmbeddingProvider(dim=16),
        core_client=core_client,
        analyzed_key_template="analyzed/resume/{resume_id}/summary.md",
    )
    return analyzer, extractor, chain, storage, core_client


@pytest.mark.asyncio
async def test_happy_path_extracts_analyzes_saves_and_upserts_embeddings() -> None:
    analyzer, extractor, chain, storage, core_client = _make_analyzer(
        extracted=ExtractedSource(text="hello", source_type="PDF"),
        analysis=DocumentAnalysisResult(
            summary="요약", tech_stack=["Go"], markdown="## 개요\n" + "x" * 500
        ),
        upsert_result=3,
    )
    result = await analyzer.analyze(
        resume_id=42, file_path="r/raw/1/a.pdf", analyzed_document_id=77
    )

    extractor.extract.assert_awaited_once_with("r/raw/1/a.pdf")
    chain.analyze.assert_awaited_once()
    storage.put_text.assert_awaited_once()

    core_client.upsert_embeddings.assert_awaited_once()
    kwargs = core_client.upsert_embeddings.await_args.kwargs
    assert kwargs["document_id"] == 77
    assert kwargs["dim"] == 16
    assert kwargs["model"] == "mock"
    assert len(kwargs["chunks"]) > 0  # 마크다운이 chunk_size 넘으니 여러 chunk

    assert result.summary == "요약"
    assert result.tech_stack == ["Go"]
    assert result.document_path == "analyzed/resume/42/summary.md"
    assert result.embedding_chunk_count == 3


@pytest.mark.asyncio
async def test_empty_text_raises_before_llm_or_embedding() -> None:
    analyzer, _, chain, storage, core_client = _make_analyzer(
        extracted=ExtractedSource(text="   \n\t  ", source_type="PDF"),
    )
    with pytest.raises(ResumeAnalyzeError) as exc_info:
        await analyzer.analyze(
            resume_id=1, file_path="empty.pdf", analyzed_document_id=9
        )

    assert exc_info.value.code == "EMPTY_PDF_TEXT"
    assert exc_info.value.retriable is False
    chain.analyze.assert_not_called()
    storage.put_text.assert_not_called()
    core_client.upsert_embeddings.assert_not_called()


@pytest.mark.asyncio
async def test_embedding_upsert_failure_translates_to_domain_error() -> None:
    analyzer, _, _, storage, core_client = _make_analyzer(
        extracted=ExtractedSource(text="hello", source_type="PDF"),
        analysis=DocumentAnalysisResult(
            summary="x", tech_stack=[], markdown="md content"
        ),
        upsert_result=CoreEmbeddingUpsertError(
            code="DOCUMENT_NOT_FOUND", message="404", retriable=False
        ),
    )
    with pytest.raises(ResumeAnalyzeError) as exc_info:
        await analyzer.analyze(resume_id=1, file_path="x.pdf", analyzed_document_id=9)
    assert exc_info.value.code == "DOCUMENT_NOT_FOUND"
    assert exc_info.value.retriable is False
    # 마크다운 자체는 저장됐을 수 있음 — embedding만 실패
    storage.put_text.assert_awaited_once()
    core_client.upsert_embeddings.assert_awaited_once()
