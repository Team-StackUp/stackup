from unittest.mock import AsyncMock

import pytest

from ai_server.analyzer.repository_analyzer import RepositoryAnalyzer
from ai_server.analyzer.sources.base import ExtractedSource
from ai_server.chain.document_analysis_chain import DocumentAnalysisResult
from ai_server.messaging.progress import (
    ANALYSIS_PROGRESS_EVENT,
    AnalysisProgressNotifier,
)
from ai_server.model.messages.realtime import RealtimeNotifyPayload
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import MockEmbeddingProvider


def _notifier() -> tuple[AnalysisProgressNotifier, AsyncMock]:
    publisher = AsyncMock()
    notifier = AnalysisProgressNotifier(
        publisher=publisher, routing_key="realtime.user.notify"
    )
    return notifier, publisher


@pytest.mark.asyncio
async def test_emit_publishes_progress_envelope() -> None:
    notifier, publisher = _notifier()

    await notifier.emit(
        user_id=5,
        target_type="REPOSITORY",
        target_id=4,
        phase="EMBEDDING",
        message="임베딩 중",
        trace_id="trace-1",
    )

    publisher.publish.assert_awaited_once()
    kwargs = publisher.publish.await_args.kwargs
    assert kwargs["routing_key"] == "realtime.user.notify"
    assert kwargs["message_type"] == "realtime.user.notify"
    assert kwargs["trace_id"] == "trace-1"
    assert kwargs["context"].user_id == 5

    payload = kwargs["payload"]
    assert isinstance(payload, RealtimeNotifyPayload)
    assert payload.event_type == ANALYSIS_PROGRESS_EVENT
    assert payload.data.target_type == "REPOSITORY"
    assert payload.data.target_id == 4
    assert payload.data.phase == "EMBEDDING"
    # RealTime Envelope.payload 형태로 직렬화되는지 확인.
    dumped = payload.model_dump(by_alias=True)
    assert dumped["eventType"] == ANALYSIS_PROGRESS_EVENT
    assert dumped["data"]["targetType"] == "REPOSITORY"


@pytest.mark.asyncio
async def test_emit_without_user_id_is_noop() -> None:
    notifier, publisher = _notifier()

    await notifier.emit(
        user_id=None,
        target_type="RESUME",
        target_id=1,
        phase="EXTRACTING",
        message="x",
        trace_id="t",
    )

    publisher.publish.assert_not_awaited()


@pytest.mark.asyncio
async def test_emit_swallows_publish_failure() -> None:
    notifier, publisher = _notifier()
    publisher.publish = AsyncMock(side_effect=RuntimeError("broker down"))

    # 진행 정보는 휘발성 — 발행 실패가 예외로 전파되어 분석을 막으면 안 된다.
    await notifier.emit(
        user_id=5,
        target_type="REPOSITORY",
        target_id=4,
        phase="EMBEDDING",
        message="x",
        trace_id="t",
    )


@pytest.mark.asyncio
async def test_emitter_for_binds_context() -> None:
    notifier, publisher = _notifier()

    emit = notifier.emitter_for(
        user_id=9, target_type="RESUME", target_id=3, trace_id="tr"
    )
    await emit("SUMMARIZING", "요약 중")

    kwargs = publisher.publish.await_args.kwargs
    assert kwargs["context"].user_id == 9
    assert kwargs["payload"].data.target_type == "RESUME"
    assert kwargs["payload"].data.target_id == 3
    assert kwargs["payload"].data.phase == "SUMMARIZING"


@pytest.mark.asyncio
async def test_repository_analyzer_emits_phases_in_order() -> None:
    extractor = AsyncMock()
    extractor.extract = AsyncMock(
        return_value=ExtractedSource(text="x" * 300, source_type="REPOSITORY")
    )
    chain = AsyncMock()
    chain.analyze = AsyncMock(
        return_value=DocumentAnalysisResult(
            summary="s", tech_stack=["Go"], markdown="## a\n" + "y" * 300
        )
    )
    core_client = AsyncMock()
    core_client.fetch_github_token = AsyncMock(return_value="tok")
    core_client.upsert_embeddings = AsyncMock(return_value=2)

    analyzer = RepositoryAnalyzer(
        extractor=extractor,
        core_client=core_client,
        chain=chain,
        storage=AsyncMock(),
        chunker=MarkdownChunker(chunk_size=200, chunk_overlap=50),
        embedder=MockEmbeddingProvider(dim=16),
        analyzed_key_template="analyzed/repository/{repository_id}/summary.md",
    )

    phases: list[str] = []

    async def progress(phase: str, _message: str) -> None:
        phases.append(phase)

    await analyzer.analyze(
        repository_id=7,
        repo_full_name="u/r",
        default_branch="main",
        user_id=42,
        analyzed_document_id=88,
        progress=progress,
    )

    assert phases == ["EXTRACTING", "SUMMARIZING", "EMBEDDING"]


@pytest.mark.asyncio
async def test_user_channel_emit_unchanged_by_session_progress_addition() -> None:
    """B2 회귀 고정: 세션 채널 진행 이벤트(emit_progress) 도입 후에도 분석 진행은
    user 채널(realtime.user.notify) + user_id 컨텍스트 경로 그대로여야 한다."""
    notifier, publisher = _notifier()

    await notifier.emit(
        user_id=5,
        target_type="RESUME",
        target_id=1,
        phase="EXTRACTING",
        message="추출 중",
        trace_id="t-1",
    )

    kwargs = publisher.publish.await_args.kwargs
    assert kwargs["routing_key"] == "realtime.user.notify"
    assert kwargs["message_type"] == "realtime.user.notify"
    assert kwargs["context"].user_id == 5
    assert kwargs["context"].session_id is None  # 세션 채널로 새지 않는다
    assert kwargs["payload"].event_type == ANALYSIS_PROGRESS_EVENT
