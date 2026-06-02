from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.chain.question_generation_chain import GeneratedQuestionPool
from ai_server.core.client import EmbeddingSearchHit
from ai_server.messaging.consumers.questions_consumer import (
    QuestionsConsumer,
    _build_context,
)
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.messages.questions import (
    DocumentContext,
    GeneratedQuestion,
    QuestionPoolCallbackPayload,
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


def _envelope(payload: dict) -> bytes:
    env = {
        "messageId": "m-1",
        "messageType": "generate.questions",
        "version": "v1",
        "traceId": "t-1",
        "publishedAt": "2026-05-29T00:00:00Z",
        "publisher": "core-server",
        "payload": payload,
        "context": {"userId": 42, "sessionId": 99},
    }
    return json.dumps(env).encode()


@pytest.mark.asyncio
async def test_consumer_generates_questions_and_publishes_callback():
    generator = MagicMock()
    generator.generate = AsyncMock(
        return_value=GeneratedQuestionPool(
            questions=[
                GeneratedQuestion(category="CS_FUNDAMENTAL", question="DB 트랜잭션 격리수준?"),
                GeneratedQuestion(category="PROJECT_DEEP_DIVE", question="결제 outbox 어떻게 보장?"),
            ]
        )
    )
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = QuestionsConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
    )

    body = _envelope(
        {
            "sessionId": 99,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "documents": [
                {
                    "documentId": 1,
                    "sourceType": "RESUME",
                    "summary": "Java/Spring 백엔드 3년차.",
                    "techStack": ["Java", "Spring Boot"],
                    "markdown": "## 경력\n토스페이먼츠.",
                }
            ],
            "maxQuestions": 5,
            "initialQuestionCount": 2,
        }
    )
    await consumer.handle(_StubMessage(body))

    generator.generate.assert_awaited_once()
    call = generator.generate.await_args
    assert call.kwargs["job_category"] == "BACKEND"
    assert call.kwargs["mode"] == "TECHNICAL"
    # maxQuestions is the session limit; initialQuestionCount controls this result.
    assert call.kwargs["max_questions"] == 2
    assert "Java" in call.kwargs["context"]

    publisher.publish.assert_awaited_once()
    pub_call = publisher.publish.await_args
    payload: QuestionPoolCallbackPayload = pub_call.kwargs["payload"]
    assert payload.session_id == 99
    assert payload.kind == "POOL"
    assert len(payload.questions) == 2
    assert pub_call.kwargs["routing_key"] == "callback.questions"
    assert pub_call.kwargs["message_type"] == "callback.questions"
    assert pub_call.kwargs["correlation_id"] == "m-1"


@pytest.mark.asyncio
async def test_consumer_skips_when_message_id_already_seen():
    generator = MagicMock()
    generator.generate = AsyncMock()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    idempotency = LruIdempotencyStore(max_size=10)
    idempotency.is_seen_then_mark("m-1")  # 이미 본 것

    consumer = QuestionsConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=idempotency,
        callback_routing_key="callback.questions",
    )
    body = _envelope(
        {
            "sessionId": 99,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "documents": [],
            "maxQuestions": 3,
        }
    )
    await consumer.handle(_StubMessage(body))
    generator.generate.assert_not_awaited()
    publisher.publish.assert_not_awaited()


@pytest.mark.asyncio
async def test_consumer_defaults_initial_question_count_to_one():
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = QuestionsConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        initial_pool_size=3,
    )
    body = _envelope(
        {
            "sessionId": 99,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "documents": [],
            "maxQuestions": 5,
        }
    )
    await consumer.handle(_StubMessage(body))

    assert generator.generate.await_args.kwargs["max_questions"] == 1


@pytest.mark.asyncio
async def test_consumer_clamps_initial_question_count_to_at_least_one():
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = QuestionsConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        initial_pool_size=3,
    )
    body = _envelope(
        {
            "sessionId": 99,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "documents": [],
            "initialQuestionCount": 0,
            "maxQuestions": 5,
        }
    )
    await consumer.handle(_StubMessage(body))

    assert generator.generate.await_args.kwargs["max_questions"] == 1


@pytest.mark.asyncio
async def test_consumer_injects_initial_rag_chunks_when_available():
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(
        return_value=[
            EmbeddingSearchHit(
                document_id=1,
                chunk_index=4,
                chunk_text="Outbox table uses status and retry count",
                distance=0.11,
            )
        ]
    )
    embedder = MagicMock()
    embedder.embed = AsyncMock(return_value=[[0.1, 0.2]])

    consumer = QuestionsConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
        rag_top_k=2,
    )
    body = _envelope(
        {
            "sessionId": 99,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "documents": [
                {
                    "documentId": 1,
                    "sourceType": "REPOSITORY",
                    "summary": "outbox 구현",
                    "techStack": ["Spring"],
                    "markdown": "transactional publisher",
                }
            ],
            "initialQuestionCount": 1,
            "maxQuestions": 5,
        }
    )
    await consumer.handle(_StubMessage(body))

    embedder.embed.assert_awaited_once()
    call = core.search_embeddings.await_args
    assert call.kwargs["query_embedding"] == [0.1, 0.2]
    assert call.kwargs["document_ids"] == [1]
    assert call.kwargs["top_k"] == 20  # candidate_k (리랭크 후보 수)
    assert call.kwargs["query_text"]  # 하이브리드 검색: 쿼리 텍스트 동봉
    context = generator.generate.await_args.kwargs["context"]
    assert "Outbox table uses status and retry count" in context
    assert "outbox 구현" in context


@pytest.mark.asyncio
async def test_consumer_falls_back_to_document_context_when_rag_fails():
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(side_effect=RuntimeError("core down"))
    embedder = MagicMock()
    embedder.embed = AsyncMock(return_value=[[0.1, 0.2]])

    consumer = QuestionsConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
    )
    body = _envelope(
        {
            "sessionId": 99,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "documents": [
                {
                    "documentId": 1,
                    "sourceType": "RESUME",
                    "summary": "Java/Spring backend",
                    "techStack": ["Java"],
                    "markdown": "payment service",
                }
            ],
            "maxQuestions": 5,
        }
    )
    await consumer.handle(_StubMessage(body))

    context = generator.generate.await_args.kwargs["context"]
    assert "Java/Spring backend" in context
    assert "Retrieved document chunks" not in context


def test_build_context_handles_empty_documents():
    assert _build_context([]) == "(no documents)"


def test_build_context_joins_doc_blocks():
    docs = [
        DocumentContext(
            document_id=1,
            source_type="RESUME",
            summary="요약1",
            tech_stack=["Java", "Spring"],
            markdown="본문1",
        ),
        DocumentContext(
            document_id=2,
            source_type="REPOSITORY",
            summary=None,
            tech_stack=[],
            markdown="readme",
        ),
    ]
    text = _build_context(docs)
    assert "문서 #1 (RESUME)" in text
    assert "요약1" in text
    assert "Java, Spring" in text
    assert "본문1" in text
    assert "문서 #2 (REPOSITORY)" in text
    assert "readme" in text
