from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.chain.followup_generation_chain import FollowupResult
from ai_server.core.client import EmbeddingSearchHit
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
            "contextDocumentIds": [7],
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
async def test_consumer_injects_followup_rag_context_when_available():
    generator = MagicMock()
    generator.generate = AsyncMock(
        return_value=FollowupResult(
            followup_question="outbox 저장과 발행의 원자성은 어떻게 보장했나요?",
            answer_evaluation=AnswerEvaluation(
                specificity=2.0, logic=3.0, structure="PARTIAL_STAR"
            ),
        )
    )
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(
        return_value=[
            EmbeddingSearchHit(
                document_id=7,
                chunk_index=2,
                chunk_text="Outbox rows are inserted in the same transaction",
                distance=0.12,
            )
        ]
    )
    embedder = MagicMock()
    embedder.embed = AsyncMock(return_value=[[0.1, 0.2, 0.3]])

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
        rag_top_k=3,
    )
    await consumer.handle(_StubMessage(_envelope()))

    embedder.embed.assert_awaited_once()
    call = core.search_embeddings.await_args
    assert call.kwargs["query_embedding"] == [0.1, 0.2, 0.3]
    assert call.kwargs["document_ids"] == [7]
    assert call.kwargs["top_k"] == 20  # candidate_k (리랭크 후보 수)
    assert call.kwargs["query_text"]  # 하이브리드 검색: 쿼리 텍스트 동봉
    context = generator.generate.await_args.kwargs["context"]
    assert "Outbox rows are inserted in the same transaction" in context


@pytest.mark.asyncio
async def test_consumer_falls_back_when_followup_rag_fails():
    generator = MagicMock()
    generator.generate = AsyncMock(
        return_value=FollowupResult(
            followup_question="실패 재처리는 어떻게 했나요?",
            answer_evaluation=AnswerEvaluation(
                specificity=2.0, logic=3.0, structure="PARTIAL_STAR"
            ),
        )
    )
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(side_effect=RuntimeError("core down"))
    embedder = MagicMock()
    embedder.embed = AsyncMock(return_value=[[0.1, 0.2, 0.3]])

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
    )
    await consumer.handle(_StubMessage(_envelope()))

    assert generator.generate.await_args.kwargs["context"] == "(none)"


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


def test_format_history_formats_and_empty():
    from ai_server.messaging.consumers.followup_consumer import _format_history
    from ai_server.model.messages.followup import HistoryItem

    assert _format_history([]) == "(none)"
    out = _format_history(
        [
            HistoryItem(role="INTERVIEWER", content="Q1?"),
            HistoryItem(role="INTERVIEWEE", content="A1"),
        ]
    )
    assert out == "면접관: Q1?\n지원자: A1"


def test_answer_evaluation_correctness_defaults_none_and_parses():
    e1 = AnswerEvaluation(specificity=1.0, logic=2.0, structure="NONE")
    assert e1.correctness is None
    e2 = AnswerEvaluation.model_validate(
        {"specificity": 4, "logic": 4, "structure": "FULL_STAR", "correctness": 3.5}
    )
    assert e2.correctness == 3.5


@pytest.mark.asyncio
async def test_consumer_passes_parent_category_and_history_to_generator():
    generator = MagicMock()
    generator.generate = AsyncMock(
        return_value=FollowupResult(
            followup_question="새 각도 질문",
            answer_evaluation=AnswerEvaluation(
                specificity=2.0, logic=2.0, structure="NONE"
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
    env = {
        "messageId": "m-9",
        "messageType": "generate.followup",
        "version": "v1",
        "traceId": "t-9",
        "publishedAt": "2026-05-29T00:00:00Z",
        "publisher": "core-server",
        "payload": {
            "sessionId": 99,
            "parentMessageId": 501,
            "answerMessageId": 502,
            "previousQuestion": "Q?",
            "answerText": "A.",
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "parentCategory": "PROJECT_DEEP_DIVE",
            "history": [
                {"role": "INTERVIEWER", "content": "이전 질문"},
                {"role": "INTERVIEWEE", "content": "이전 답변"},
            ],
        },
        "context": {"userId": 42, "sessionId": 99},
    }
    await consumer.handle(_StubMessage(json.dumps(env).encode()))

    kwargs = generator.generate.await_args.kwargs
    assert kwargs["parent_category"] == "PROJECT_DEEP_DIVE"
    assert "이전 질문" in kwargs["history"]
    assert "면접관:" in kwargs["history"]
