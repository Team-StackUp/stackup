from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.analyzer.repository_analyzer import (
    RepositoryAnalysisResult,
    RepositoryAnalyzeError,
)
from ai_server.messaging.consumers.repository_consumer import RepositoryConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.envelope import Envelope
from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    RepositoryAnalyzeRequest,
)


def _request_envelope(message_id: str = "req-1", repository_id: int = 7) -> bytes:
    env = Envelope[RepositoryAnalyzeRequest].model_validate(
        {
            "messageId": message_id,
            "messageType": "analyze.repository",
            "version": "v1",
            "traceId": "trace-r",
            "publishedAt": datetime(2026, 5, 14, tzinfo=timezone.utc),
            "publisher": "core-server",
            "payload": {
                "repositoryId": repository_id,
                "repoFullName": "user/repo",
                "defaultBranch": "main",
                "analyzedDocumentId": 88,
            },
            "context": {"userId": 1},
        }
    )
    return env.model_dump_json(by_alias=True).encode("utf-8")


def _incoming_message(body: bytes) -> MagicMock:
    msg = MagicMock()
    msg.body = body
    msg.delivery_tag = 1
    process_ctx = AsyncMock()
    process_ctx.__aenter__.return_value = None
    process_ctx.__aexit__.return_value = False
    msg.process = MagicMock(return_value=process_ctx)
    return msg


def _make_consumer(
    analyzer: AsyncMock,
) -> tuple[RepositoryConsumer, AsyncMock]:
    publisher = AsyncMock()
    consumer = RepositoryConsumer(
        analyzer=analyzer,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=16),
        callback_routing_key="callback.analysis",
    )
    return consumer, publisher


def _captured_payload(publisher: AsyncMock) -> AnalysisCallbackPayload:
    publisher.publish.assert_awaited_once()
    kwargs = publisher.publish.await_args.kwargs
    assert kwargs["message_type"] == "callback.analysis"
    payload = kwargs["payload"]
    assert isinstance(payload, AnalysisCallbackPayload)
    return payload


@pytest.mark.asyncio
async def test_happy_path_publishes_analyzed_callback() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        return_value=RepositoryAnalysisResult(
            summary="요약",
            tech_stack=["Go"],
            document_path="analyzed/repository/7/summary.md",
            embedding_chunk_count=3,
        )
    )
    consumer, publisher = _make_consumer(analyzer)
    await consumer.handle(_incoming_message(_request_envelope()))

    analyzer.analyze.assert_awaited_once_with(
        repository_id=7,
        repo_full_name="user/repo",
        default_branch="main",
        user_id=1,
        analyzed_document_id=88,
    )
    payload = _captured_payload(publisher)
    assert payload.status == "ANALYZED"
    assert payload.target_type == "REPOSITORY"
    assert payload.target_id == 7
    assert payload.summary == "요약"
    assert payload.tech_stack == ["Go"]
    assert payload.document_path == "analyzed/repository/7/summary.md"
    assert payload.embedding_chunk_count == 3


@pytest.mark.asyncio
async def test_domain_error_publishes_failed_callback() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        side_effect=RepositoryAnalyzeError(
            code="REPO_NOT_FOUND", message="404", retriable=False
        )
    )
    consumer, publisher = _make_consumer(analyzer)
    await consumer.handle(_incoming_message(_request_envelope()))

    payload = _captured_payload(publisher)
    assert payload.status == "FAILED"
    assert payload.error_code == "REPO_NOT_FOUND"
    assert payload.retriable is False
    assert payload.target_type == "REPOSITORY"


@pytest.mark.asyncio
async def test_unexpected_error_publishes_retriable_failure() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(side_effect=RuntimeError("boom"))
    consumer, publisher = _make_consumer(analyzer)
    await consumer.handle(_incoming_message(_request_envelope()))

    payload = _captured_payload(publisher)
    assert payload.status == "FAILED"
    assert payload.error_code == "UNEXPECTED"
    assert payload.retriable is True


@pytest.mark.asyncio
async def test_idempotent_duplicate_skipped() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        return_value=RepositoryAnalysisResult(
            summary="x",
            tech_stack=[],
            document_path="analyzed/repository/7/summary.md",
            embedding_chunk_count=0,
        )
    )
    consumer, publisher = _make_consumer(analyzer)
    body = _request_envelope(message_id="dup-r")
    await consumer.handle(_incoming_message(body))
    await consumer.handle(_incoming_message(body))

    assert analyzer.analyze.await_count == 1
    assert publisher.publish.await_count == 1
