from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.chain.feedback_generation_chain import FeedbackResult
from ai_server.core.client import EmbeddingSearchHit
from ai_server.messaging.consumers.feedback_consumer import FeedbackConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.messages.feedback import FeedbackCallbackPayload


class _StubMessage:
    def __init__(self, body: bytes):
        self.body = body
        self.delivery_tag = 1

    def process(self, requeue: bool = False):
        return _NoopCtx()


class _NoopCtx:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False


def _envelope(*, context_documents: list[int] | None = None) -> bytes:
    env = {
        "messageId": "fb-1",
        "messageType": "generate.feedback",
        "version": "v1",
        "traceId": "t-1",
        "publishedAt": "2026-05-30T00:00:00Z",
        "publisher": "core-server",
        "payload": {
            "sessionId": 50,
            "interviewType": "TECHNICAL",
            "jobCategory": "BACKEND",
            "totalQuestionCount": 2,
            "endReason": "MAX_QUESTIONS_REACHED",
            "messages": [
                {"id": 100, "sequenceNumber": 1, "role": "INTERVIEWER", "content": "ACID?"},
                {"id": 101, "sequenceNumber": 2, "role": "INTERVIEWEE",
                 "content": "원자성·일관성·격리성·영속성", "parentMessageId": 100},
            ],
            "contextDocumentIds": context_documents or [],
        },
        "context": {"userId": 1, "sessionId": 50},
    }
    return json.dumps(env).encode()


def _generator():
    g = MagicMock()
    g.generate = AsyncMock(
        return_value=FeedbackResult(
            overall_score=85.0,
            technical_accuracy=82.0,
            logic_score=88.0,
            communication_score=80.0,
            strengths_summary="ACID 4요소를 명확히 답변.",
            weaknesses_summary="구체적 사례 부족.",
            improvement_keywords=["MVCC", "Repeatable Read"],
        )
    )
    return g


@pytest.mark.asyncio
async def test_consumer_generates_feedback_and_publishes_callback():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=core,
        embedder=None,
    )
    await consumer.handle(_StubMessage(_envelope()))

    generator.generate.assert_awaited_once()
    publisher.publish.assert_awaited_once()
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.session_id == 50
    assert payload.overall_score == 85.0
    assert publisher.publish.await_args.kwargs["message_type"] == "callback.feedback"


@pytest.mark.asyncio
async def test_consumer_calls_rag_when_documents_and_embedder_present():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(
        return_value=[
            EmbeddingSearchHit(document_id=7, chunk_index=2, chunk_text="JPA dirty checking", distance=0.12)
        ]
    )
    embedder = MagicMock()
    embedder.embed = AsyncMock(return_value=[[0.1, 0.2, 0.3]])

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=core,
        embedder=embedder,
        rag_top_k=3,
    )
    await consumer.handle(_StubMessage(_envelope(context_documents=[7])))

    embedder.embed.assert_awaited_once()
    core.search_embeddings.assert_awaited_once()
    # rag_context 가 chain 호출 인자로 전달됐는지 확인
    invoked_kwargs = generator.generate.await_args.kwargs
    assert "JPA dirty checking" in invoked_kwargs["rag_context"]


@pytest.mark.asyncio
async def test_consumer_idempotent_skip():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    idempotency = LruIdempotencyStore(max_size=10)
    idempotency.is_seen_then_mark("fb-1")

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=idempotency,
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
    )
    await consumer.handle(_StubMessage(_envelope()))
    generator.generate.assert_not_awaited()
    publisher.publish.assert_not_awaited()
