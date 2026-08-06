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
                GeneratedQuestion(
                    category="CS_FUNDAMENTAL", question="DB 트랜잭션 격리수준?"
                ),
                GeneratedQuestion(
                    category="PROJECT_DEEP_DIVE", question="결제 outbox 어떻게 보장?"
                ),
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
            "jobCategories": ["BACKEND"],
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
    assert call.kwargs["job_categories"] == ["BACKEND"]
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
async def test_consumer_publishes_failed_pool_when_generate_raises():
    """generate() 가 예외로 죽어도 콜백은 항상 나가야 세션 시작이 무기한 멈추지 않는다."""
    generator = MagicMock()
    generator.generate = AsyncMock(side_effect=RuntimeError("gateway 500"))
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
            "jobCategories": ["BACKEND"],
            "documents": [],
            "maxQuestions": 5,
            "initialQuestionCount": 2,
        }
    )
    await consumer.handle(_StubMessage(body))

    publisher.publish.assert_awaited_once()
    payload: QuestionPoolCallbackPayload = publisher.publish.await_args.kwargs[
        "payload"
    ]
    assert payload.status == "FAILED"
    assert payload.error_code == "GENERATION_FAILED"
    assert payload.retriable is True
    assert payload.questions == []


@pytest.mark.asyncio
async def test_consumer_marks_failure_non_retriable_on_schema_type_error():
    """LLM 출력이 스키마와 안 맞아 TypeError 가 나면 재시도해도 같은 이유로 또 실패할
    가능성이 높으므로 retriable=False 로 구분한다."""
    generator = MagicMock()
    generator.generate = AsyncMock(side_effect=TypeError("unexpected type"))
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
            "jobCategories": ["BACKEND"],
            "documents": [],
            "maxQuestions": 5,
            "initialQuestionCount": 2,
        }
    )
    await consumer.handle(_StubMessage(body))

    payload: QuestionPoolCallbackPayload = publisher.publish.await_args.kwargs[
        "payload"
    ]
    assert payload.status == "FAILED"
    assert payload.error_code == "GENERATION_SCHEMA_INVALID"
    assert payload.retriable is False


@pytest.mark.asyncio
async def test_consumer_forwards_self_intro_answer_to_generator():
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
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
            "jobCategories": ["BACKEND"],
            "documents": [],
            "maxQuestions": 5,
            "initialQuestionCount": 2,
            "selfIntroAnswer": "결제 시스템을 설계한 백엔드 3년차입니다.",
        }
    )
    await consumer.handle(_StubMessage(body))

    call = generator.generate.await_args
    assert (
        call.kwargs["self_introduction"] == "결제 시스템을 설계한 백엔드 3년차입니다."
    )


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
            "jobCategories": ["BACKEND"],
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
            "jobCategories": ["BACKEND"],
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
            "jobCategories": ["BACKEND"],
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
            "jobCategories": ["BACKEND"],
            "documents": [
                {
                    "documentId": 1,
                    "sourceType": "REPOSITORY",
                    "summary": "outbox 구현",
                    "techStack": ["Spring"],
                    "markdown": "transactional publisher",
                },
                {
                    "documentId": 2,
                    "sourceType": "RESUME",
                    "summary": "백엔드 3년",
                    "techStack": ["Java"],
                    "markdown": "결제 시스템",
                },
            ],
            "initialQuestionCount": 1,
            "maxQuestions": 5,
        }
    )
    await consumer.handle(_StubMessage(body))

    # 문서 2개 → Retrieve 경로(RAG 검색 발동)
    embedder.embed.assert_awaited_once()
    call = core.search_embeddings.await_args
    assert call.kwargs["query_embedding"] == [0.1, 0.2]
    assert call.kwargs["document_ids"] == [1, 2]
    assert call.kwargs["top_k"] == 2  # rag_top_k (direct vector search)
    assert call.kwargs["query_text"]  # 하이브리드 검색: 쿼리 텍스트 동봉
    context = generator.generate.await_args.kwargs["context"]
    assert "Outbox table uses status and retry count" in context
    assert "outbox 구현" in context


@pytest.mark.asyncio
async def test_single_document_skips_rag_plan_mode():
    # 문서 1개 → PLAN: markdown 이 이미 컨텍스트에 있으니 검색 생략
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock()
    embedder = MagicMock()
    embedder.embed = AsyncMock()

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
            "sessionId": 1,
            "mode": "TECHNICAL",
            "jobCategories": ["BACKEND"],
            "documents": [
                {
                    "documentId": 1,
                    "sourceType": "RESUME",
                    "summary": "백엔드",
                    "techStack": ["Java"],
                    "markdown": "결제 시스템 구현",
                }
            ],
            "maxQuestions": 5,
        }
    )
    await consumer.handle(_StubMessage(body))

    core.search_embeddings.assert_not_awaited()
    embedder.embed.assert_not_awaited()
    context = generator.generate.await_args.kwargs["context"]
    assert "결제 시스템 구현" in context  # base context 사용


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
            "jobCategories": ["BACKEND"],
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


@pytest.mark.asyncio
async def test_multi_document_rag_timeout_falls_back_to_base_context():
    """rag_timeout_sec 초과 시 검색 없이 base_context 로 폴백 — followup 과 대칭.
    다문서(2개 이상)라야 RAG 경로를 타므로 문서를 2개 준다."""
    import asyncio

    async def _slow_embed(texts, *, task_type=""):
        await asyncio.sleep(0.1)  # 타임아웃(0.01s) 보다 훨씬 길다
        return [[0.0]]

    generator = MagicMock()
    generator.generate = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(return_value=[])
    embedder = MagicMock()
    embedder.embed = _slow_embed

    consumer = QuestionsConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
        rag_timeout_sec=0.01,
    )
    body = _envelope(
        {
            "sessionId": 99,
            "mode": "TECHNICAL",
            "jobCategories": ["BACKEND"],
            "documents": [
                {
                    "documentId": 1,
                    "sourceType": "RESUME",
                    "summary": "Java/Spring backend",
                    "markdown": "payment service",
                },
                {
                    "documentId": 2,
                    "sourceType": "REPOSITORY",
                    "summary": "결제 레포",
                    "markdown": "outbox pattern",
                },
            ],
            "maxQuestions": 5,
        }
    )
    await consumer.handle(_StubMessage(body))

    core.search_embeddings.assert_not_awaited()
    context = generator.generate.await_args.kwargs["context"]
    assert "Java/Spring backend" in context
    assert "Retrieved document chunks" not in context


def test_build_initial_rag_query_includes_self_intro_and_jd():
    from ai_server.messaging.consumers.questions_consumer import (
        _build_initial_rag_query,
    )
    from ai_server.model.messages.questions import GenerateQuestionsRequest

    req = GenerateQuestionsRequest(
        session_id=1,
        mode="JOB_TAILORED",
        job_categories=["BACKEND"],
        documents=[],
        self_intro_answer="결제 시스템을 만든 백엔드 3년차",
        target_company_name="토스",
        target_job_description="대용량 결제 백엔드, Kotlin/Spring",
    )
    q = _build_initial_rag_query(req)
    assert "결제 시스템을 만든 백엔드" in q  # 자기소개가 쿼리에 포함
    assert "대용량 결제 백엔드" in q  # JD 가 쿼리에 포함
    assert "토스" in q


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
    assert "문서 #1 (이력서)" in text
    assert "요약1" in text
    assert "Java, Spring" in text
    assert "본문1" in text
    assert "문서 #2 (GitHub 레포)" in text
    assert "readme" in text


def test_generated_question_parses_evidence_and_signal():
    from ai_server.model.messages.questions import GeneratedQuestion

    q = GeneratedQuestion.model_validate(
        {
            "category": "PROJECT_DEEP_DIVE",
            "question": "결제 동시성 어떻게 처리했나요?",
            "targetEvidence": "결제 시스템에서 분산락 도입",
            "expectedSignal": "동시성 제어 trade-off 이해",
        }
    )
    assert q.target_evidence == "결제 시스템에서 분산락 도입"
    assert q.expected_signal == "동시성 제어 trade-off 이해"

    # 하위호환: 신규 필드 없어도 기본값
    q2 = GeneratedQuestion.model_validate({"category": "BEHAVIORAL", "question": "Q?"})
    assert q2.target_evidence == ""
    assert q2.expected_signal == ""
