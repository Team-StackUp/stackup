from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.analyzer.web_resume_analyzer import (
    WebResumeAnalysisResult,
    WebResumeAnalyzeError,
)
from ai_server.messaging.consumers.web_consumer import WebResumeConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.envelope import Envelope
from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    WebResumeAnalyzeRequest,
)


def _request_envelope(message_id: str = "req-1", resume_id: int = 11) -> bytes:
    env = Envelope[WebResumeAnalyzeRequest].model_validate(
        {
            "messageId": message_id,
            "messageType": "analyze.web",
            "version": "v1",
            "traceId": "trace-w",
            "publishedAt": datetime(2026, 5, 14, tzinfo=timezone.utc),
            "publisher": "core-server",
            "payload": {
                "resumeId": resume_id,
                "url": "https://example.com/me",
                "analyzedDocumentId": 99,
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


def _make_consumer(analyzer: AsyncMock) -> tuple[WebResumeConsumer, AsyncMock]:
    publisher = AsyncMock()
    consumer = WebResumeConsumer(
        analyzer=analyzer,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=16),
        callback_routing_key="callback.analysis",
    )
    return consumer, publisher


def _captured_payload(publisher: AsyncMock) -> AnalysisCallbackPayload:
    publisher.publish.assert_awaited_once()
    payload = publisher.publish.await_args.kwargs["payload"]
    assert isinstance(payload, AnalysisCallbackPayload)
    return payload


@pytest.mark.asyncio
async def test_happy_path_publishes_analyzed_callback() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        return_value=WebResumeAnalysisResult(
            summary="요약",
            tech_stack=["React"],
            document_path="analyzed/web-resume/11/summary.md",
            embedding_chunk_count=2,
        )
    )
    consumer, publisher = _make_consumer(analyzer)
    await consumer.handle(_incoming_message(_request_envelope()))

    analyzer.analyze.assert_awaited_once_with(
        resume_id=11, url="https://example.com/me", analyzed_document_id=99
    )
    payload = _captured_payload(publisher)
    assert payload.status == "ANALYZED"
    assert payload.target_type == "WEB"
    assert payload.target_id == 11
    assert payload.summary == "요약"
    assert payload.document_path == "analyzed/web-resume/11/summary.md"


@pytest.mark.asyncio
async def test_domain_error_publishes_failed_callback() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        side_effect=WebResumeAnalyzeError(
            code="WEB_HTTP_STATUS", message="404", retriable=False
        )
    )
    consumer, publisher = _make_consumer(analyzer)
    await consumer.handle(_incoming_message(_request_envelope()))

    payload = _captured_payload(publisher)
    assert payload.status == "FAILED"
    assert payload.error_code == "WEB_HTTP_STATUS"
    assert payload.retriable is False
    assert payload.target_type == "WEB"
