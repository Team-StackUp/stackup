from unittest.mock import AsyncMock

import pytest

from ai_server.analyzer.resume_analyzer import (
    ResumeAnalyzeError,
    ResumeAnalyzer,
)
from ai_server.analyzer.sources.base import ExtractedSource
from ai_server.chain.document_analysis_chain import DocumentAnalysisResult


def _make_analyzer(
    *,
    extracted: ExtractedSource,
    analysis: DocumentAnalysisResult | None = None,
) -> tuple[ResumeAnalyzer, AsyncMock, AsyncMock, AsyncMock]:
    extractor = AsyncMock()
    extractor.extract = AsyncMock(return_value=extracted)
    chain = AsyncMock()
    if analysis is not None:
        chain.analyze = AsyncMock(return_value=analysis)
    storage = AsyncMock()
    analyzer = ResumeAnalyzer(
        extractor=extractor,
        chain=chain,
        storage=storage,
        analyzed_key_template="analyzed/resume/{resume_id}/summary.md",
    )
    return analyzer, extractor, chain, storage


@pytest.mark.asyncio
async def test_happy_path_extracts_analyzes_and_saves() -> None:
    analyzer, extractor, chain, storage = _make_analyzer(
        extracted=ExtractedSource(text="hello", source_type="PDF"),
        analysis=DocumentAnalysisResult(
            summary="요약", tech_stack=["Go"], markdown="## 개요\nx"
        ),
    )
    result = await analyzer.analyze(resume_id=42, file_path="r/raw/1/a.pdf")

    extractor.extract.assert_awaited_once_with("r/raw/1/a.pdf")
    chain.analyze.assert_awaited_once_with(text="hello", source_type="PDF")
    storage.put_text.assert_awaited_once_with(
        "analyzed/resume/42/summary.md", "## 개요\nx"
    )

    assert result.summary == "요약"
    assert result.tech_stack == ["Go"]
    assert result.document_path == "analyzed/resume/42/summary.md"


@pytest.mark.asyncio
async def test_empty_text_raises_domain_error_and_skips_llm() -> None:
    analyzer, _, chain, storage = _make_analyzer(
        extracted=ExtractedSource(text="   \n\t  ", source_type="PDF"),
    )
    with pytest.raises(ResumeAnalyzeError) as exc_info:
        await analyzer.analyze(resume_id=1, file_path="empty.pdf")

    assert exc_info.value.code == "EMPTY_PDF_TEXT"
    assert exc_info.value.retriable is False
    chain.analyze.assert_not_called()
    storage.put_text.assert_not_called()
