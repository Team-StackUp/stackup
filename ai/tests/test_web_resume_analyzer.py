from unittest.mock import AsyncMock

import pytest

from ai_server.analyzer.sources.base import ExtractedSource
from ai_server.analyzer.sources.web import WebFetchError
from ai_server.analyzer.web_resume_analyzer import (
    WebResumeAnalyzeError,
    WebResumeAnalyzer,
)
from ai_server.chain.document_analysis_chain import DocumentAnalysisResult


def _make_analyzer(
    *,
    extract_result: ExtractedSource | Exception | None = None,
    analysis: DocumentAnalysisResult | None = None,
) -> tuple[WebResumeAnalyzer, AsyncMock, AsyncMock, AsyncMock]:
    extractor = AsyncMock()
    if isinstance(extract_result, Exception):
        extractor.extract = AsyncMock(side_effect=extract_result)
    else:
        extractor.extract = AsyncMock(return_value=extract_result)
    chain = AsyncMock()
    if analysis is not None:
        chain.analyze = AsyncMock(return_value=analysis)
    storage = AsyncMock()
    analyzer = WebResumeAnalyzer(
        extractor=extractor,
        chain=chain,
        storage=storage,
        analyzed_key_template="analyzed/web-resume/{resume_id}/summary.md",
    )
    return analyzer, extractor, chain, storage


@pytest.mark.asyncio
async def test_happy_path() -> None:
    analyzer, extractor, chain, storage = _make_analyzer(
        extract_result=ExtractedSource(text="본문 텍스트", source_type="WEB"),
        analysis=DocumentAnalysisResult(
            summary="요약", tech_stack=["TypeScript"], markdown="## 개요\nweb"
        ),
    )
    result = await analyzer.analyze(resume_id=11, url="https://example.com/me")

    extractor.extract.assert_awaited_once_with("https://example.com/me")
    chain.analyze.assert_awaited_once_with(text="본문 텍스트", source_type="WEB")
    storage.put_text.assert_awaited_once_with(
        "analyzed/web-resume/11/summary.md", "## 개요\nweb"
    )
    assert result.summary == "요약"
    assert result.tech_stack == ["TypeScript"]
    assert result.document_path == "analyzed/web-resume/11/summary.md"


@pytest.mark.asyncio
async def test_fetch_error_translates_to_domain_error() -> None:
    analyzer, _, chain, storage = _make_analyzer(
        extract_result=WebFetchError(
            code="WEB_HTTP_STATUS", message="404", retriable=False
        ),
    )
    with pytest.raises(WebResumeAnalyzeError) as exc_info:
        await analyzer.analyze(resume_id=1, url="https://example.com/x")
    assert exc_info.value.code == "WEB_HTTP_STATUS"
    assert exc_info.value.retriable is False
    chain.analyze.assert_not_called()
    storage.put_text.assert_not_called()
