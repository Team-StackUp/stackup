from unittest.mock import AsyncMock

import pytest

from ai_server.analyzer.repository_analyzer import (
    RepositoryAnalyzeError,
    RepositoryAnalyzer,
)
from ai_server.analyzer.sources.base import ExtractedSource
from ai_server.analyzer.sources.github_repo import RepositoryFetchError
from ai_server.chain.document_analysis_chain import DocumentAnalysisResult
from ai_server.core.client import CoreTokenError


def _make_analyzer(
    *,
    extract_result: ExtractedSource | Exception | None = None,
    analysis: DocumentAnalysisResult | None = None,
    token_result: str | Exception = "user-token",
) -> tuple[RepositoryAnalyzer, AsyncMock, AsyncMock, AsyncMock, AsyncMock]:
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
    if isinstance(token_result, Exception):
        core_client.fetch_github_token = AsyncMock(side_effect=token_result)
    else:
        core_client.fetch_github_token = AsyncMock(return_value=token_result)

    analyzer = RepositoryAnalyzer(
        extractor=extractor,
        core_client=core_client,
        chain=chain,
        storage=storage,
        analyzed_key_template="analyzed/repository/{repository_id}/summary.md",
    )
    return analyzer, extractor, chain, storage, core_client


@pytest.mark.asyncio
async def test_happy_path_fetches_token_then_extracts_with_it() -> None:
    analyzer, extractor, chain, storage, core_client = _make_analyzer(
        extract_result=ExtractedSource(text="README + tree", source_type="REPOSITORY"),
        analysis=DocumentAnalysisResult(
            summary="요약", tech_stack=["Go"], markdown="## 개요\nx"
        ),
        token_result="user-abc",
    )
    result = await analyzer.analyze(
        repository_id=7,
        repo_full_name="user/repo",
        default_branch="main",
        user_id=42,
    )

    core_client.fetch_github_token.assert_awaited_once_with(42)
    extractor.extract.assert_awaited_once_with("user/repo", access_token="user-abc")
    chain.analyze.assert_awaited_once_with(
        text="README + tree", source_type="REPOSITORY"
    )
    storage.put_text.assert_awaited_once_with(
        "analyzed/repository/7/summary.md", "## 개요\nx"
    )
    assert result.summary == "요약"
    assert result.document_path == "analyzed/repository/7/summary.md"


@pytest.mark.asyncio
async def test_missing_user_id_raises_before_token_fetch() -> None:
    analyzer, extractor, _, storage, core_client = _make_analyzer()
    with pytest.raises(RepositoryAnalyzeError) as exc_info:
        await analyzer.analyze(
            repository_id=1,
            repo_full_name="user/repo",
            user_id=None,
        )
    assert exc_info.value.code == "MISSING_USER_ID"
    assert exc_info.value.retriable is False
    core_client.fetch_github_token.assert_not_called()
    extractor.extract.assert_not_called()
    storage.put_text.assert_not_called()


@pytest.mark.asyncio
async def test_token_fetch_error_translates_to_domain_error() -> None:
    analyzer, extractor, _, storage, _ = _make_analyzer(
        token_result=CoreTokenError(
            code="USER_NOT_FOUND", message="404", retriable=False
        ),
    )
    with pytest.raises(RepositoryAnalyzeError) as exc_info:
        await analyzer.analyze(repository_id=1, repo_full_name="user/repo", user_id=42)
    assert exc_info.value.code == "USER_NOT_FOUND"
    assert exc_info.value.retriable is False
    extractor.extract.assert_not_called()
    storage.put_text.assert_not_called()


@pytest.mark.asyncio
async def test_token_fetch_retriable_propagates_retriable() -> None:
    analyzer, _, _, _, _ = _make_analyzer(
        token_result=CoreTokenError(
            code="CORE_UNAVAILABLE", message="5xx", retriable=True
        ),
    )
    with pytest.raises(RepositoryAnalyzeError) as exc_info:
        await analyzer.analyze(repository_id=1, repo_full_name="user/repo", user_id=42)
    assert exc_info.value.code == "CORE_UNAVAILABLE"
    assert exc_info.value.retriable is True


@pytest.mark.asyncio
async def test_fetch_error_translates_to_domain_error() -> None:
    analyzer, _, chain, storage, _ = _make_analyzer(
        extract_result=RepositoryFetchError(
            code="REPO_NOT_FOUND", message="not found", retriable=False
        ),
    )
    with pytest.raises(RepositoryAnalyzeError) as exc_info:
        await analyzer.analyze(repository_id=1, repo_full_name="user/repo", user_id=42)
    assert exc_info.value.code == "REPO_NOT_FOUND"
    chain.analyze.assert_not_called()
    storage.put_text.assert_not_called()


@pytest.mark.asyncio
async def test_empty_text_raises_empty_repo_content() -> None:
    analyzer, _, chain, storage, _ = _make_analyzer(
        extract_result=ExtractedSource(text="   ", source_type="REPOSITORY"),
    )
    with pytest.raises(RepositoryAnalyzeError) as exc_info:
        await analyzer.analyze(repository_id=1, repo_full_name="user/repo", user_id=42)
    assert exc_info.value.code == "EMPTY_REPO_CONTENT"
    chain.analyze.assert_not_called()
    storage.put_text.assert_not_called()
