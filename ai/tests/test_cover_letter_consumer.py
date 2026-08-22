from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.analyzer.resume_analyzer import (
    ResumeAnalyzeError,
    ResumeAnalysisResult,
)
from ai_server.messaging.consumers.cover_letter_consumer import CoverLetterConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.envelope import Envelope
from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    CoverLetterAnalyzeRequest,
)


def _request_envelope(message_id: str = "cl-1", cover_letter_id: int = 9) -> bytes:
    env = Envelope[CoverLetterAnalyzeRequest].model_validate(
        {
            "messageId": message_id,
            "messageType": "analyze.cover_letter",
            "version": "v1",
            "traceId": "trace-x",
            "publishedAt": datetime(2026, 6, 28, tzinfo=timezone.utc),
            "publisher": "core-server",
            "payload": {
                "coverLetterId": cover_letter_id,
                "content": "# 자기소개서\n\n## 지원동기\n저는 ...",
                "analyzedDocumentId": 55,
            },
            "context": {"userId": 123},
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


def _make_consumer(analyzer: AsyncMock) -> tuple[CoverLetterConsumer, AsyncMock]:
    publisher = AsyncMock()
    consumer = CoverLetterConsumer(
        analyzer=analyzer,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=16),
        callback_routing_key="callback.analysis",
    )
    return consumer, publisher


@pytest.mark.asyncio
async def test_happy_path_publishes_cover_letter_callback() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        return_value=ResumeAnalysisResult(
            summary="자소서 요약",
            tech_stack=["Spring"],
            document_path="analyzed/cover-letter/9/summary.md",
            embedding_chunk_count=2,
        )
    )
    consumer, publisher = _make_consumer(analyzer)

    await consumer.handle(_incoming_message(_request_envelope()))

    # inline content 가 ResumeAnalyzer 의 file_path(=locator)로, cover_letter_id 가 resume_id 로 전달.
    analyzer.analyze.assert_awaited_once_with(
        resume_id=9,
        file_path="# 자기소개서\n\n## 지원동기\n저는 ...",
        analyzed_document_id=55,
        progress=None,
    )
    publisher.publish.assert_awaited_once()
    payload = publisher.publish.await_args.kwargs["payload"]
    assert isinstance(payload, AnalysisCallbackPayload)
    assert payload.status == "ANALYZED"
    assert payload.target_type == "COVER_LETTER"
    assert payload.target_id == 9
    assert payload.summary == "자소서 요약"


@pytest.mark.asyncio
async def test_domain_error_publishes_failed_cover_letter_callback() -> None:
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(
        side_effect=ResumeAnalyzeError(
            code="EMPTY_PDF_TEXT", message="비어있음", retriable=False
        )
    )
    consumer, publisher = _make_consumer(analyzer)

    await consumer.handle(_incoming_message(_request_envelope()))

    payload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "FAILED"
    assert payload.target_type == "COVER_LETTER"
    assert payload.error_code == "EMPTY_PDF_TEXT"


@pytest.mark.asyncio
async def test_unexpected_error_publishes_failed_callback() -> None:
    """예상 못 한 예외도 FAILED 콜백(UNEXPECTED, retriable=true) — 공용 가드(F6) 회귀 고정."""
    analyzer = AsyncMock()
    analyzer.analyze = AsyncMock(side_effect=RuntimeError("llm blew up"))
    consumer, publisher = _make_consumer(analyzer)

    await consumer.handle(_incoming_message(_request_envelope()))

    publisher.publish.assert_awaited_once()
    payload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "FAILED"
    assert payload.error_code == "UNEXPECTED"
    assert payload.error_message == "RuntimeError: llm blew up"
    assert payload.retriable is True
    assert payload.target_id == 9
