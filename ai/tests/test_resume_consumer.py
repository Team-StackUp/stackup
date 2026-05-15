from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.analyzer.resume_analyzer import (
    ResumeAnalyzeError,
    ResumeAnalysisResult,
)
from ai_server.messaging.consumers.resume_consumer import ResumeConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.envelope import Envelope
from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    ResumeAnalyzeRequest,
)


def _request_envelope(message_id: str = "req-1", resume_id: int = 42) -> bytes:
    env = Envelope[ResumeAnalyzeRequest].model_validate(
        {
            "messageId": message_id,
            "messageType": "analyze.resume",
            "version": "v1",
            "traceId": "trace-x",
            "publishedAt": datetime(2026, 5, 8, tzinfo=timezone.utc),
            "publisher": "core-server",
            "payload": {
                "resumeId": resume_id,
                "filePath": "resumes/raw/123/abc.pdf",
                "analyzedDocumentId": 77,
            },
            "context": {"userId": 123},
        }
    )
    return env.model_dump_json(by_alias=True).encode("utf-8")


def _incoming_message(body: bytes) -> MagicMock:
    msg = MagicMock()
    msg.body = body
    msg.delivery_tag = 1
    # `async with message.process(...)` 형태 호출을 위해 async context manager 반환.
    process_ctx = AsyncMock()
    process_ctx.__aenter__.return_value = None
    process_ctx.__aexit__.return_value = False
    msg.process = MagicMock(return_value=process_ctx)
    return msg


def _make_consumer(
    analyzer: AsyncMock,
    *,
    idempotency: LruIdempotencyStore | None = None,
) -> tuple[ResumeConsumer, AsyncMock]:
    publisher = AsyncMock()
    consumer = ResumeConsumer(
        analyzer=analyzer,
        publisher=publisher,
        idempotency=idempotency or LruIdempotencyStore(max_size=16),
        callback_routing_key="callback.analysis",
    )
    return consumer, publisher


def _captured_payload(publisher: AsyncMock) -> AnalysisCallbackPayload:
    publisher.publish.assert_awaited_once()
    kwargs = publisher.publish.await_args.kwargs
    assert kwargs["message_type"] == "callback.analysis"
    assert kwargs["routing_key"] == "callback.analysis"
    payload = kwargs["payload"]
    assert isinstance(payload, AnalysisCallbackPayload)
    return payload


@pytest.mark.asyncio
async def test_happy_path_publishes_analyzed_callback() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        return_value=ResumeAnalysisResult(
            summary="요약",
            tech_stack=["Python", "FastAPI"],
            document_path="analyzed/resume/42/summary.md",
            embedding_chunk_count=4,
        )
    )
    consumer, publisher = _make_consumer(analyzer)

    await consumer.handle(_incoming_message(_request_envelope()))

    analyzer.analyze.assert_awaited_once_with(
        resume_id=42, file_path="resumes/raw/123/abc.pdf", analyzed_document_id=77
    )
    payload = _captured_payload(publisher)
    assert payload.status == "ANALYZED"
    assert payload.target_type == "RESUME"
    assert payload.target_id == 42
    assert payload.summary == "요약"
    assert payload.tech_stack == ["Python", "FastAPI"]
    assert payload.document_path == "analyzed/resume/42/summary.md"
    assert payload.embedding_chunk_count == 4


@pytest.mark.asyncio
async def test_domain_error_publishes_failed_callback_with_code() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        side_effect=ResumeAnalyzeError(
            code="EMPTY_PDF_TEXT",
            message="no text",
            retriable=False,
        )
    )
    consumer, publisher = _make_consumer(analyzer)

    await consumer.handle(_incoming_message(_request_envelope()))

    payload = _captured_payload(publisher)
    assert payload.status == "FAILED"
    assert payload.error_code == "EMPTY_PDF_TEXT"
    assert payload.error_message == "no text"
    assert payload.retriable is False
    assert payload.summary is None
    assert payload.document_path is None


@pytest.mark.asyncio
async def test_unexpected_error_publishes_failed_callback_as_retriable() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(side_effect=RuntimeError("boom"))
    consumer, publisher = _make_consumer(analyzer)

    await consumer.handle(_incoming_message(_request_envelope()))

    payload = _captured_payload(publisher)
    assert payload.status == "FAILED"
    assert payload.error_code == "UNEXPECTED"
    assert payload.retriable is True


@pytest.mark.asyncio
async def test_idempotent_message_skips_analyzer_and_publish() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        return_value=ResumeAnalysisResult(
            summary="요약",
            tech_stack=[],
            document_path="analyzed/resume/42/summary.md",
            embedding_chunk_count=0,
        )
    )
    idem = LruIdempotencyStore(max_size=16)
    consumer, publisher = _make_consumer(analyzer, idempotency=idem)

    body = _request_envelope(message_id="dup-1")
    await consumer.handle(_incoming_message(body))
    await consumer.handle(_incoming_message(body))  # 중복 메시지

    assert analyzer.analyze.await_count == 1
    assert publisher.publish.await_count == 1


@pytest.mark.asyncio
async def test_parse_failure_raises_for_dlq() -> None:
    analyzer = AsyncMock()
    consumer, publisher = _make_consumer(analyzer)
    bad = _incoming_message(b"not-json")

    with pytest.raises(Exception):
        await consumer.handle(bad)

    analyzer.analyze.assert_not_called()
    publisher.publish.assert_not_called()
