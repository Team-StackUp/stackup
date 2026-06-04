from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.chain.feedback_generation_chain import (
    FeedbackResult,
    LlmFeedbackGenerator,
)
from ai_server.chain.prompts.feedback_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.core.client import EmbeddingSearchHit
from ai_server.messaging.consumers.feedback_consumer import (
    FeedbackConsumer,
    _build_score_basis,
    _build_transcript,
)
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.messages.feedback import (
    FeedbackCallbackPayload,
    FeedbackMessageItem,
    MessageEvaluation,
)

VOICE_SUMMARY = {
    "analyzedMessageCount": 2,
    "averageSpeakingRateWpm": 132.5,
    "totalSilenceDurationSec": 4.2,
    "fillerWordCounts": {"um": 3, "like": 1},
}


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


def _envelope(
    *,
    context_documents: list[int] | None = None,
    voice_analysis_summary: dict | None = None,
    end_reason: str = "MAX_QUESTIONS_REACHED",
) -> bytes:
    env = {
        "messageId": "fb-1",
        "messageType": "generate.feedback",
        "version": "v1",
        "traceId": "t-1",
        "publishedAt": "2026-05-30T00:00:00Z",
        "publisher": "core-server",
        "payload": {
            "sessionId": 50,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "totalQuestionCount": 2,
            "endReason": end_reason,
            "messages": [
                {
                    "id": 100,
                    "sequenceNumber": 1,
                    "role": "INTERVIEWER",
                    "content": "ACID?",
                },
                {
                    "id": 101,
                    "sequenceNumber": 2,
                    "role": "INTERVIEWEE",
                    "content": "원자성·일관성·격리성·영속성",
                    "parentMessageId": 100,
                },
            ],
            "contextDocumentIds": context_documents or [],
        },
        "context": {"userId": 1, "sessionId": 50},
    }
    if voice_analysis_summary is not None:
        env["payload"]["voiceAnalysisSummary"] = voice_analysis_summary
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
async def test_consumer_accepts_pool_exhausted_end_reason():
    # 회귀: Core 가 POOL_EXHAUSTED 로 종료해도 파싱 실패(DLQ) 없이 피드백 생성돼야 한다.
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
    )
    await consumer.handle(_StubMessage(_envelope(end_reason="POOL_EXHAUSTED")))

    generator.generate.assert_awaited_once()
    publisher.publish.assert_awaited_once()


@pytest.mark.asyncio
async def test_consumer_calls_rag_when_documents_and_embedder_present():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    core = MagicMock()
    core.search_embeddings = AsyncMock(
        return_value=[
            EmbeddingSearchHit(
                document_id=7,
                chunk_index=2,
                chunk_text="JPA dirty checking",
                distance=0.12,
            )
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
async def test_consumer_accepts_voice_summary_and_passes_it_to_generator():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
    )
    await consumer.handle(_StubMessage(_envelope(voice_analysis_summary=VOICE_SUMMARY)))

    invoked_kwargs = generator.generate.await_args.kwargs
    voice_context = invoked_kwargs["voice_analysis_summary"]
    assert "Analyzed answer messages: 2" in voice_context
    assert "Average speaking rate: 132.5 WPM" in voice_context
    assert "Total silence duration: 4.2 seconds" in voice_context
    assert "like: 1" in voice_context
    assert "um: 3" in voice_context

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert not hasattr(payload, "voice_analysis_summary")


def _answer(seq: int, *, spec=None, logic=None, structure=None, correctness=None):
    return FeedbackMessageItem(
        id=seq,
        sequence_number=seq,
        role="INTERVIEWEE",
        content="답변",
        evaluation=MessageEvaluation(
            specificity=spec, logic=logic, structure=structure, correctness=correctness
        ),
    )


def test_build_score_basis_aggregates_to_0_100():
    msgs = [
        _answer(2, spec=4.0, logic=3.0, structure="FULL_STAR", correctness=3.0),
        _answer(4, spec=2.0, logic=4.0, structure="PARTIAL_STAR", correctness=2.0),
    ]
    basis = _build_score_basis(msgs)
    # correctness 평균 2.5 → technical_accuracy ≈ 50
    assert "technical_accuracy ≈ 50" in basis
    # logic 평균 3.5 → 70
    assert "logic_score ≈ 70" in basis
    assert "채점된 답변 수: 2" in basis


def test_build_score_basis_marks_correctness_absent_without_rag():
    # correctness 가 전부 null(참고문서 미선택) → technical_accuracy 근거 없음.
    msgs = [_answer(2, spec=3.0, logic=3.0, structure="NONE", correctness=None)]
    basis = _build_score_basis(msgs)
    assert "technical_accuracy: 근거 없음" in basis


def test_build_score_basis_empty_when_no_evaluations():
    assert "per-answer 평가 없음" in _build_score_basis([])


def test_transcript_includes_expected_signal_on_question():
    msgs = [
        FeedbackMessageItem(
            id=1,
            sequence_number=1,
            role="INTERVIEWER",
            content="동시성을 어떻게 제어하나요?",
            expected_signal="DB 락/격리수준까지 설명하는지",
        ),
        _answer(2, spec=3.0, logic=3.0, structure="NONE"),
    ]
    transcript = _build_transcript(msgs)
    assert "기대 신호(평가 기준): DB 락/격리수준까지 설명하는지" in transcript


def test_feedback_prompt_has_score_anchor_and_slot():
    assert "점수 앵커" in SYSTEM_PROMPT
    assert "±15" in SYSTEM_PROMPT
    assert "{score_basis}" in HUMAN_PROMPT


@pytest.mark.asyncio
async def test_llm_feedback_generator_includes_voice_summary_in_chain_input():
    class _FakeChain:
        def __init__(self):
            self.input = None

        async def ainvoke(self, value):
            self.input = value
            return FeedbackResult(overall_score=70.0)

    chain = _FakeChain()
    generator = LlmFeedbackGenerator(chain)

    await generator.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=1,
        end_reason="USER_REQUEST",
        transcript="answer",
        rag_context="(none)",
        voice_analysis_summary="Average speaking rate: 132.5 WPM",
    )

    assert chain.input["voice_analysis_summary"] == "Average speaking rate: 132.5 WPM"
    assert "voice_analysis_summary" in HUMAN_PROMPT


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


def test_build_transcript_annotates_interviewee_evaluation():
    from ai_server.messaging.consumers.feedback_consumer import _build_transcript
    from ai_server.model.messages.feedback import FeedbackMessageItem, MessageEvaluation

    msgs = [
        FeedbackMessageItem(
            id=1, sequence_number=1, role="INTERVIEWER", content="질문?"
        ),
        FeedbackMessageItem(
            id=2,
            sequence_number=2,
            role="INTERVIEWEE",
            content="답변.",
            evaluation=MessageEvaluation(
                specificity=2.0, logic=3.0, structure="PARTIAL_STAR", correctness=1.0
            ),
        ),
    ]
    out = _build_transcript(msgs)
    assert "답변평가:" in out
    assert "specificity=2" in out
    assert "correctness=1" in out
    # 면접관 줄엔 평가 주석 없음
    assert out.splitlines()[0].endswith("질문?")
