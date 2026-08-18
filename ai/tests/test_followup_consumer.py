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
    GenerateFollowupRequest,
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
            "followupMessageId": 503,
            "previousQuestion": "결제 outbox 어떻게 구현?",
            "answerText": "RabbitMQ로 보냈습니다.",
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "contextDocumentIds": [7],
        },
        "context": {"userId": 42, "sessionId": 99},
    }
    return json.dumps(env).encode()


def _make_streaming_generator(result: FollowupResult):
    """streaming_generator 페이크: stream() 호출 시 on_question_token 한 번 호출 후 result 반환."""
    streaming = MagicMock()

    async def _stream(*, on_question_token, **kwargs):
        await on_question_token("토큰")
        return result

    streaming.stream = _stream
    return streaming


def _make_session_notifier():
    notifier = MagicMock()
    notifier.emit_delta = AsyncMock()
    return notifier


@pytest.mark.asyncio
async def test_consumer_generates_followup_and_publishes_callback():
    followup_result = FollowupResult(
        followup_question="구체적으로 outbox 테이블 스키마와 polling 주기는?",
        answer_evaluation=AnswerEvaluation(
            specificity=2.0, logic=3.0, structure="PARTIAL_STAR"
        ),
    )
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=followup_result)
    streaming_generator = _make_streaming_generator(followup_result)
    session_notifier = _make_session_notifier()
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
    )
    await consumer.handle(_StubMessage(_envelope()))

    # streaming path이 사용되므로 generator.generate 는 호출되지 않음
    generator.generate.assert_not_awaited()
    session_notifier.emit_delta.assert_awaited_once()
    publisher.publish.assert_awaited_once()
    payload: FollowupCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.session_id == 99
    assert payload.kind == "FOLLOWUP"
    assert payload.parent_message_id == 501
    assert payload.followup_question.startswith("구체적으로")
    assert payload.answer_evaluation.structure == "PARTIAL_STAR"
    assert payload.followup_message_id == 503
    assert publisher.publish.await_args.kwargs["message_type"] == "callback.questions"


def _make_failing_streaming_generator():
    """streaming_generator 페이크: on_question_token 을 한 번 흘린 뒤 예외로 죽는다."""
    streaming = MagicMock()

    async def _stream(*, on_question_token, **kwargs):
        await on_question_token("토큰")
        raise RuntimeError("stream boom")

    streaming.stream = _stream
    return streaming


@pytest.mark.asyncio
async def test_consumer_publishes_failed_followup_when_streaming_raises():
    """스트리밍 생성이 죽어도 콜백은 항상 나가야 Core 가 선INSERT 한 placeholder가
    영원히 '생성 중'으로 남지 않는다."""
    generator = MagicMock()
    generator.generate = AsyncMock()
    streaming_generator = _make_failing_streaming_generator()
    session_notifier = _make_session_notifier()
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
    )
    await consumer.handle(_StubMessage(_envelope()))

    generator.generate.assert_not_awaited()
    publisher.publish.assert_awaited_once()
    payload: FollowupCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "FAILED"
    assert payload.error_code == "GENERATION_FAILED"
    assert payload.retriable is True
    assert payload.followup_question == ""
    # Core 가 어떤 placeholder/질문/답변을 갱신할지 식별할 필드는 실패해도 채워져야 한다.
    assert payload.followup_message_id == 503
    assert payload.parent_message_id == 501
    assert payload.answer_message_id == 502


@pytest.mark.asyncio
async def test_consumer_publishes_failed_followup_when_generate_raises():
    """비스트리밍 경로도 동일 — generate() 실패가 피드백처럼 조용히 삼켜지면 안 된다."""
    generator = MagicMock()
    generator.generate = AsyncMock(side_effect=RuntimeError("gateway 500"))
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
    )
    await consumer.handle(_StubMessage(_envelope()))

    payload: FollowupCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "FAILED"
    assert payload.error_code == "GENERATION_FAILED"
    assert payload.followup_message_id == 503


@pytest.mark.asyncio
async def test_consumer_injects_followup_rag_context_when_available():
    followup_result = FollowupResult(
        followup_question="outbox 저장과 발행의 원자성은 어떻게 보장했나요?",
        answer_evaluation=AnswerEvaluation(
            specificity=2.0, logic=3.0, structure="PARTIAL_STAR"
        ),
    )
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=followup_result)
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

    # Track kwargs passed to stream
    received_kwargs: dict = {}

    async def _stream(*, on_question_token, **kwargs):
        received_kwargs.update(kwargs)
        await on_question_token("토큰")
        return followup_result

    streaming_generator = MagicMock()
    streaming_generator.stream = _stream
    session_notifier = _make_session_notifier()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
        rag_top_k=3,
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
    )
    await consumer.handle(_StubMessage(_envelope()))

    embedder.embed.assert_awaited_once()
    call = core.search_embeddings.await_args
    assert call.kwargs["query_embedding"] == [0.1, 0.2, 0.3]
    assert call.kwargs["document_ids"] == [7]
    assert call.kwargs["top_k"] == 3  # rag_top_k (direct vector search)
    assert call.kwargs["query_text"]  # 하이브리드 검색: 쿼리 텍스트 동봉
    assert (
        "Outbox rows are inserted in the same transaction" in received_kwargs["context"]
    )


@pytest.mark.asyncio
async def test_consumer_falls_back_when_followup_rag_fails():
    followup_result = FollowupResult(
        followup_question="실패 재처리는 어떻게 했나요?",
        answer_evaluation=AnswerEvaluation(
            specificity=2.0, logic=3.0, structure="PARTIAL_STAR"
        ),
    )
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=followup_result)
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(side_effect=RuntimeError("core down"))
    embedder = MagicMock()
    embedder.embed = AsyncMock(return_value=[[0.1, 0.2, 0.3]])

    received_kwargs: dict = {}

    async def _stream(*, on_question_token, **kwargs):
        received_kwargs.update(kwargs)
        await on_question_token("토큰")
        return followup_result

    streaming_generator = MagicMock()
    streaming_generator.stream = _stream
    session_notifier = _make_session_notifier()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
    )
    await consumer.handle(_StubMessage(_envelope()))

    assert received_kwargs["context"] == "(none)"


@pytest.mark.asyncio
async def test_consumer_idempotent_skip():
    generator = MagicMock()
    generator.generate = AsyncMock()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    idempotency = LruIdempotencyStore(max_size=10)
    idempotency.is_seen_then_mark("m-1")
    streaming_generator = _make_streaming_generator(
        FollowupResult(followup_question="Q")
    )
    session_notifier = _make_session_notifier()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=idempotency,
        callback_routing_key="callback.questions",
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
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


# ---------------------------------------------------------------------------
# TTS segment synthesis tests (Part B Task 4)
# ---------------------------------------------------------------------------


def _make_tts_fake():
    """audio/mpeg を返す偽 TtsProvider."""
    from ai_server.voice.tts.base import TtsResult

    tts = MagicMock()
    tts.synthesize = AsyncMock(
        return_value=TtsResult(
            audio_bytes=b"x", duration_sec=0.5, content_type="audio/mpeg"
        )
    )
    return tts


def _make_storage_fake():
    storage = MagicMock()
    storage.put_bytes = AsyncMock()
    return storage


def _make_full_session_notifier():
    notifier = MagicMock()
    notifier.emit_delta = AsyncMock()
    notifier.emit_audio = AsyncMock()
    return notifier


def _make_streaming_generator_two_tokens(result):
    """stream() 이 on_question_token 을 두 번 호출 ('첫 문장이다. ', '둘째다.')."""
    streaming = MagicMock()

    async def _stream(*, on_question_token, **kwargs):
        await on_question_token("첫 문장이다. ")
        await on_question_token("둘째다.")
        return result

    streaming.stream = _stream
    return streaming


@pytest.mark.asyncio
async def test_segment_synthesis_emits_audio_event():
    """스트리밍 경로에서 TTS + storage 가 주입되면 segment 합성 후 emit_audio 를 발행한다."""
    followup_result = FollowupResult(
        followup_question="구체적인 질문",
        answer_evaluation=AnswerEvaluation(
            specificity=2.0, logic=3.0, structure="PARTIAL_STAR"
        ),
    )
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=followup_result)
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    tts = _make_tts_fake()
    storage = _make_storage_fake()
    session_notifier = _make_full_session_notifier()
    streaming_generator = _make_streaming_generator_two_tokens(followup_result)

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
        tts=tts,
        storage=storage,
        tts_voice="alloy",
    )
    await consumer.handle(_StubMessage(_envelope()))

    # emit_audio 는 최소 1회 이상 호출돼야 한다
    assert session_notifier.emit_audio.await_count >= 1

    # storage.put_bytes 첫 번째 호출 키가 seg-0.mp3 패턴
    assert storage.put_bytes.await_count >= 1
    first_key = storage.put_bytes.await_args_list[0].args[0]
    assert first_key == "interview/tts/99/503/seg-0.mp3"

    # emit_audio 첫 번째 호출 kwargs
    first_audio_call = session_notifier.emit_audio.await_args_list[0].kwargs
    assert first_audio_call["ext"] == "mp3"
    assert first_audio_call["seq"] == 0


@pytest.mark.asyncio
async def test_no_tts_injected_emits_no_audio():
    """tts/storage 를 주입하지 않으면 emit_audio 는 호출되지 않는다."""
    followup_result = FollowupResult(
        followup_question="Q",
        answer_evaluation=AnswerEvaluation(
            specificity=2.0, logic=2.0, structure="NONE"
        ),
    )
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=followup_result)
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    session_notifier = _make_full_session_notifier()
    streaming_generator = _make_streaming_generator_two_tokens(followup_result)

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
        # tts / storage 미주입
    )
    await consumer.handle(_StubMessage(_envelope()))

    session_notifier.emit_audio.assert_not_awaited()


@pytest.mark.asyncio
async def test_consumer_passes_parent_category_and_history_to_generator():
    followup_result = FollowupResult(
        followup_question="새 각도 질문",
        answer_evaluation=AnswerEvaluation(
            specificity=2.0, logic=2.0, structure="NONE"
        ),
    )
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=followup_result)
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    received_kwargs: dict = {}

    async def _stream(*, on_question_token, **kwargs):
        received_kwargs.update(kwargs)
        await on_question_token("토큰")
        return followup_result

    streaming_generator = MagicMock()
    streaming_generator.stream = _stream
    session_notifier = _make_session_notifier()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
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
            "followupMessageId": 503,
            "previousQuestion": "Q?",
            "answerText": "A.",
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "parentCategory": "PROJECT_DEEP_DIVE",
            "parentExpectedSignal": "동시성 제어를 DB 레벨까지 설명하는지",
            "history": [
                {"role": "INTERVIEWER", "content": "이전 질문"},
                {"role": "INTERVIEWEE", "content": "이전 답변"},
            ],
        },
        "context": {"userId": 42, "sessionId": 99},
    }
    await consumer.handle(_StubMessage(json.dumps(env).encode()))

    assert received_kwargs["parent_category"] == "PROJECT_DEEP_DIVE"
    assert received_kwargs["expected_signal"] == "동시성 제어를 DB 레벨까지 설명하는지"
    assert "이전 질문" in received_kwargs["history"]
    assert "면접관:" in received_kwargs["history"]


@pytest.mark.asyncio
async def test_callback_includes_answer_message_id():
    followup_result = FollowupResult(
        followup_question="Q",
        answer_evaluation=AnswerEvaluation(
            specificity=2.0, logic=2.0, structure="NONE"
        ),
    )
    generator = MagicMock()
    generator.generate = AsyncMock(return_value=followup_result)
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    streaming_generator = _make_streaming_generator(followup_result)
    session_notifier = _make_session_notifier()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        streaming_generator=streaming_generator,
        session_notifier=session_notifier,
    )
    await consumer.handle(_StubMessage(_envelope()))
    payload = publisher.publish.await_args.kwargs["payload"]
    assert payload.answer_message_id == 502  # _envelope 의 answerMessageId
    assert payload.followup_message_id == 503  # _envelope 의 followupMessageId


# ---------------------------------------------------------------------------
# 꼬리질문 RAG 저지연: top_k 직접 검색, timeout 폴백
# ---------------------------------------------------------------------------


def _make_req() -> GenerateFollowupRequest:
    return GenerateFollowupRequest(
        session_id=99,
        parent_message_id=501,
        answer_message_id=502,
        followup_message_id=503,
        previous_question="결제 outbox 어떻게 구현?",
        answer_text="RabbitMQ로 보냈습니다.",
        mode="TECHNICAL",
        job_category="BACKEND",
        context_document_ids=[1],
    )


def _make_hit(document_id: int = 1, chunk_index: int = 0, chunk_text: str = "x"):
    from types import SimpleNamespace

    return SimpleNamespace(
        document_id=document_id, chunk_index=chunk_index, chunk_text=chunk_text
    )


@pytest.mark.asyncio
async def test_rag_searches_top_k_directly():
    """search_embeddings 는 rag_top_k 로 직접 검색하고, 청크 텍스트가 결과에 포함된다."""
    from ai_server.messaging.consumers.followup_consumer import FollowupConsumer
    from ai_server.messaging.idempotency import LruIdempotencyStore

    hit = _make_hit(chunk_text="이 청크가 반환돼야 한다")

    embedder = MagicMock()
    embedder.embed = AsyncMock(return_value=[[0.0]])

    core = MagicMock()
    core.search_embeddings = AsyncMock(return_value=[hit])

    generator = MagicMock()
    generator.generate = AsyncMock()
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
        rag_top_k=5,
    )

    req = _make_req()
    result = await consumer._build_rag_context(req)

    # 청크 텍스트가 결과에 포함돼야 한다
    assert "이 청크가 반환돼야 한다" in result
    # rag_top_k 로 직접 검색
    call_kwargs = core.search_embeddings.await_args.kwargs
    assert call_kwargs["top_k"] == 5  # rag_top_k


@pytest.mark.asyncio
async def test_rag_timeout_returns_none():
    """rag_timeout_sec 초과 시 _build_rag_context 가 '(none)' 을 반환한다."""
    import asyncio

    from ai_server.messaging.consumers.followup_consumer import FollowupConsumer
    from ai_server.messaging.idempotency import LruIdempotencyStore

    async def _slow_embed(texts, *, task_type=""):
        await asyncio.sleep(0.1)  # 타임아웃(0.01s) 보다 훨씬 길다
        return [[0.0]]

    embedder = MagicMock()
    embedder.embed = _slow_embed

    core = MagicMock()
    core.search_embeddings = AsyncMock(return_value=[])

    generator = MagicMock()
    generator.generate = AsyncMock()
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = FollowupConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.questions",
        core_client=core,
        embedder=embedder,
        rag_top_k=5,
        rag_timeout_sec=0.01,
    )

    req = _make_req()
    result = await consumer._build_rag_context(req)

    assert result == "(none)"
