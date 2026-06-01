from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.chain.followup_generation_chain import FollowupResult
from ai_server.messaging.consumers.followup_consumer import FollowupConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.messages.followup import (
    AnswerEvaluation,
    FollowupCallbackPayload,
)


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


def _envelope() -> bytes:
    env = {
        "messageId": "m-1",
        "messageType": "generate.followup",
        "version": "v1",
        "traceId": "t-1",
        "publishedAt": "2026-05-29T00:00:00Z",
        "publisher": "core-server",
        "payload": {
            "sessionId": 99,
            "parentMessageId": 501,
            "answerMessageId": 502,
            "previousQuestion": "결제 outbox 어떻게 구현?",
            "answerText": "RabbitMQ로 보냈습니다.",
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
        },
        "context": {"userId": 42, "sessionId": 99},
    }
    return json.dumps(env).encode()


@pytest.mark.asyncio
async def test_consumer_generates_followup_and_publishes_callback():
    generator = MagicMock()
    generator.generate = AsyncMock(
        return_value=FollowupResult(
            followup_question="구체적으로 outbox 테이블 스키마와 polling 주기는?",
            answer_evaluation=AnswerEvaluation(
                specificity=2.0, logic=3.0, structure="PARTIAL_STAR"
            ),
        )
    )
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
    )
    await consumer.handle(_StubMessage(_envelope()))

    generator.generate.assert_awaited_once()
    publisher.publish.assert_awaited_once()
    payload: FollowupCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.session_id == 99
    assert payload.kind == "FOLLOWUP"
    assert payload.parent_message_id == 501
    assert payload.followup_question.startswith("구체적으로")
    assert payload.answer_evaluation.structure == "PARTIAL_STAR"
    assert publisher.publish.await_args.kwargs["message_type"] == "callback.questions"


@pytest.mark.asyncio
async def test_consumer_idempotent_skip():
    generator = MagicMock()
    generator.generate = AsyncMock()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    idempotency = LruIdempotencyStore(max_size=10)
    idempotency.is_seen_then_mark("m-1")

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=idempotency,
        callback_routing_key="callback.questions",
    )
    await consumer.handle(_StubMessage(_envelope()))
    generator.generate.assert_not_awaited()
    publisher.publish.assert_not_awaited()
