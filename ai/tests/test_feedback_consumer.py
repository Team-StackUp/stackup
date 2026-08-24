from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.chain.feedback_generation_chain import (
    CoachingResult,
    EvaluatorResult,
    FeedbackResult,
    JobFitResult,
    LlmAnswerCoach,
    LlmFeedbackGenerator,
    LlmJobFitEvaluator,
    LlmSelfIntroEvaluator,
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
            "attemptId": "att-1",
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
    assert payload.status == "OK"  # 실패 신호 도입 후에도 성공 콜백은 OK(기본값)
    assert payload.attempt_id == "att-1"  # 요청의 attemptId 에코(F5)
    assert publisher.publish.await_args.kwargs["message_type"] == "callback.feedback"


@pytest.mark.asyncio
async def test_consumer_publishes_degraded_feedback_when_panel_generation_fails():
    """패널 generate() 자체가 예상 못 한 예외로 죽어도, gather 전체가 취소돼 피드백을
    통째로 잃는 게 아니라 빈 결과로 대체해 콜백은 계속 발행돼야 한다(회귀 방지)."""
    generator = MagicMock()
    generator.generate = AsyncMock(side_effect=RuntimeError("boom"))
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
    assert payload.overall_score is None
    assert payload.technical_accuracy is None
    assert "일시적 오류" in payload.weaknesses_summary
    assert payload.panel_breakdown == []


@pytest.mark.asyncio
async def test_consumer_publishes_failed_callback_on_unexpected_error():
    """보호 구간(패널·부가 평가) 밖의 예상 못 한 예외는 예전엔 reject→DLQ 로만 가서
    Core 가 실패를 모른 채 세션이 '피드백 생성 중'에 무기한 멈췄다 — 이제 FAILED
    콜백을 발행하고 ack 해야 한다."""
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
    consumer._build_rag_context = AsyncMock(side_effect=RuntimeError("rag down"))

    await consumer.handle(_StubMessage(_envelope()))  # raise 없이 ack 경로

    publisher.publish.assert_awaited_once()
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.session_id == 50
    assert payload.status == "FAILED"
    assert payload.error_code == "UNEXPECTED"
    assert payload.error_message == "RuntimeError: rag down"
    assert payload.retriable is True
    assert payload.attempt_id == "att-1"  # FAILED 도 에코 — Core 의 stale 판별 근거(F5)
    assert payload.overall_score is None
    assert publisher.publish.await_args.kwargs["message_type"] == "callback.feedback"


@pytest.mark.asyncio
async def test_consumer_marks_schema_error_not_retriable():
    """TypeError(LLM 출력 스키마 불일치)는 재시도해도 똑같이 죽는다 —
    questions/followup consumer 와 동일하게 retriable=false 로 분류."""
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
    consumer._build_rag_context = AsyncMock(side_effect=TypeError("bad schema"))

    await consumer.handle(_StubMessage(_envelope()))

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "FAILED"
    assert payload.error_code == "GENERATION_SCHEMA_INVALID"
    assert payload.retriable is False


@pytest.mark.asyncio
async def test_consumer_does_not_send_failed_when_success_publish_fails():
    """생성이 성공했는데 OK 콜백 발행만 실패한 경우 — FAILED 로 오인 발행하지 않고
    원 예외로 DLQ 에 보내 재처리 가능하게 남긴다(멱등 마킹도 되돌림)."""
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock(side_effect=ConnectionError("mq down"))
    store = LruIdempotencyStore(max_size=10)

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=store,
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
    )

    with pytest.raises(ConnectionError, match="mq down"):
        await consumer.handle(_StubMessage(_envelope()))

    publisher.publish.assert_awaited_once()  # FAILED 재발행 시도 없음
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "OK"
    # unmark 됐으므로 재주입 시 duplicate skip 되지 않는다.
    assert store.is_seen_then_mark("fb-1") is False


@pytest.mark.asyncio
async def test_consumer_reraises_when_failed_callback_publish_also_fails():
    """폴백(FAILED 콜백) 발행마저 실패하면 원 예외를 다시 던져 DLQ 로 보낸다(최후 안전망)."""
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock(side_effect=ConnectionError("mq down"))
    core = MagicMock()
    store = LruIdempotencyStore(max_size=10)

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=store,
        callback_routing_key="callback.feedback",
        core_client=core,
        embedder=None,
    )
    consumer._build_rag_context = AsyncMock(side_effect=RuntimeError("rag down"))

    with pytest.raises(RuntimeError, match="rag down"):
        await consumer.handle(_StubMessage(_envelope()))

    # unmark 됐으므로 DLQ 재주입 시 duplicate skip 으로 삼켜지지 않는다.
    assert store.is_seen_then_mark("fb-1") is False


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


def _self_intro_envelope() -> bytes:
    env = {
        "messageId": "fb-si",
        "messageType": "generate.feedback",
        "version": "v1",
        "traceId": "t-si",
        "publishedAt": "2026-05-30T00:00:00Z",
        "publisher": "core-server",
        "payload": {
            "sessionId": 51,
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
            "totalQuestionCount": 2,
            "endReason": "POOL_EXHAUSTED",
            "messages": [
                {
                    "id": 200,
                    "sequenceNumber": 1,
                    "role": "INTERVIEWER",
                    "content": "먼저 간단하게 자기소개 부탁드립니다.",
                    "category": "SELF_INTRODUCTION",
                },
                {
                    "id": 201,
                    "sequenceNumber": 2,
                    "role": "INTERVIEWEE",
                    "content": "안녕하세요, 결제 시스템을 만든 백엔드 3년차입니다.",
                    "parentMessageId": 200,
                },
                {
                    "id": 202,
                    "sequenceNumber": 3,
                    "role": "INTERVIEWER",
                    "content": "ACID?",
                    "category": "CS_FUNDAMENTAL",
                },
                {
                    "id": 203,
                    "sequenceNumber": 4,
                    "role": "INTERVIEWEE",
                    "content": "원자성·일관성·격리성·영속성",
                    "parentMessageId": 202,
                },
            ],
            "contextDocumentIds": [],
        },
        "context": {"userId": 1, "sessionId": 51},
    }
    return json.dumps(env).encode()


def _self_intro_evaluator():
    ev = MagicMock()
    ev.evaluate = AsyncMock(
        return_value=EvaluatorResult(
            score=78.0,
            strength="직무 연관 경험을 앞세움",
            weakness="다소 장황함",
            detail="결제 시스템 경험을 먼저 제시한 점이 좋았습니다.",
            score_rationale="구조·직무적합성은 좋으나 간결성 감점",
        )
    )
    return ev


@pytest.mark.asyncio
async def test_consumer_appends_self_intro_panel_item():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _self_intro_evaluator()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        self_intro_evaluator=evaluator,
    )
    await consumer.handle(_StubMessage(_self_intro_envelope()))

    # 자기소개 답변 텍스트로 첫인상 평가 호출
    ev_kwargs = evaluator.evaluate.await_args.kwargs
    assert "백엔드 3년차" in ev_kwargs["self_intro_answer"]
    assert ev_kwargs["self_intro_question"].startswith("먼저")

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    intro_items = [b for b in payload.panel_breakdown if b.evaluator == "첫인상"]
    assert len(intro_items) == 1
    assert intro_items[0].score == 78.0
    assert intro_items[0].dimension  # 비어있지 않음


@pytest.mark.asyncio
async def test_consumer_skips_self_intro_when_no_self_intro_message():
    # 레거시 세션(자기소개 질문 없음) → 첫인상 평가 호출 안 됨, 패널에 첫인상 미추가.
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _self_intro_evaluator()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        self_intro_evaluator=evaluator,
    )
    await consumer.handle(_StubMessage(_envelope()))

    evaluator.evaluate.assert_not_awaited()
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert all(b.evaluator != "첫인상" for b in payload.panel_breakdown)


def test_find_self_intro_pairs_question_and_answer():
    from ai_server.messaging.consumers.feedback_consumer import _find_self_intro

    msgs = [
        FeedbackMessageItem(
            id=200,
            sequence_number=1,
            role="INTERVIEWER",
            content="자기소개?",
            category="SELF_INTRODUCTION",
        ),
        FeedbackMessageItem(
            id=201,
            sequence_number=2,
            role="INTERVIEWEE",
            content="소개합니다",
            parent_message_id=200,
        ),
    ]
    pair = _find_self_intro(msgs)
    assert pair is not None
    q, a = pair
    assert q.id == 200 and a.id == 201

    # 답변이 비면 None
    msgs[1].content = "   "
    assert _find_self_intro(msgs) is None


@pytest.mark.asyncio
async def test_self_intro_evaluator_forwards_inputs_to_chain():
    class _FakeChain:
        def __init__(self):
            self.input = None

        async def ainvoke(self, value):
            self.input = value
            return EvaluatorResult(score=80.0)

    chain = _FakeChain()
    evaluator = LlmSelfIntroEvaluator(chain)

    await evaluator.evaluate(
        job_category="BACKEND",
        mode="TECHNICAL",
        self_intro_question="자기소개 부탁드립니다.",
        self_intro_answer="백엔드 개발자입니다.",
        voice_analysis_summary="Average speaking rate: 120 WPM",
    )

    assert chain.input["self_intro_answer"] == "백엔드 개발자입니다."
    assert chain.input["job_category"] == "BACKEND"


def _job_tailored_envelope(
    *, with_jd: bool = True, mode: str = "JOB_TAILORED"
) -> bytes:
    payload = {
        "sessionId": 60,
        "mode": mode,
        "jobCategory": "BACKEND",
        "totalQuestionCount": 2,
        "endReason": "POOL_EXHAUSTED",
        "messages": [
            {"id": 1, "sequenceNumber": 1, "role": "INTERVIEWER", "content": "ACID?"},
            {
                "id": 2,
                "sequenceNumber": 2,
                "role": "INTERVIEWEE",
                "content": "원자성·일관성·격리성·영속성",
                "parentMessageId": 1,
            },
        ],
        "contextDocumentIds": [],
    }
    if with_jd:
        payload["targetCompanyName"] = "토스"
        payload["targetJobDescription"] = (
            "Kotlin/Spring 백엔드, 대용량 결제 시스템 경험 우대"
        )
    env = {
        "messageId": "fb-jt",
        "messageType": "generate.feedback",
        "version": "v1",
        "traceId": "t-jt",
        "publishedAt": "2026-05-30T00:00:00Z",
        "publisher": "core-server",
        "payload": payload,
        "context": {"userId": 1, "sessionId": 60},
    }
    return json.dumps(env).encode()


def _job_fit_evaluator():
    ev = MagicMock()
    ev.evaluate = AsyncMock(
        return_value=JobFitResult(
            fit=EvaluatorResult(
                score=72.0,
                strength="결제 도메인 경험이 JD 핵심 요구와 일치",
                weakness="대용량 트래픽 처리 근거는 미검증",
                detail="JD의 '대용량 결제' 요구에 결제 경험은 부합하나, 처리량 근거가 약함.",
                score_rationale="핵심 요구 충족하나 일부 갭",
            ),
            understanding=EvaluatorResult(
                score=60.0,
                strength="결제 도메인 책임은 인지",
                weakness="직무가 다루는 범위를 피상적으로만 이해",
                detail="이 직무의 핵심 책임을 구체적으로 짚지 못함.",
                score_rationale="동기 연결이 약함",
            ),
        )
    )
    return ev


@pytest.mark.asyncio
async def test_consumer_appends_job_fit_panel_item():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _job_fit_evaluator()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        job_fit_evaluator=evaluator,
    )
    await consumer.handle(_StubMessage(_job_tailored_envelope()))

    ev_kwargs = evaluator.evaluate.await_args.kwargs
    assert ev_kwargs["company_name"] == "토스"
    assert "대용량 결제" in ev_kwargs["job_description"]

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    fit_items = [b for b in payload.panel_breakdown if b.evaluator == "직무 적합도"]
    assert len(fit_items) == 1
    assert fit_items[0].score == 72.0
    # 직무 이해도가 별도 항목으로 분리되어 포함된다.
    und_items = [b for b in payload.panel_breakdown if b.evaluator == "직무 이해도"]
    assert len(und_items) == 1
    assert und_items[0].score == 60.0


@pytest.mark.asyncio
async def test_consumer_drops_empty_job_fit_axis():
    # understanding 축이 빈(점수·내용 없음) 경우 그 패널 항목은 표시에서 제외된다.
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = MagicMock()
    evaluator.evaluate = AsyncMock(
        return_value=JobFitResult(
            fit=EvaluatorResult(score=72.0, detail="결제 경험 부합"),
            understanding=EvaluatorResult(),  # 빈 축
        )
    )

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        job_fit_evaluator=evaluator,
    )
    await consumer.handle(_StubMessage(_job_tailored_envelope()))

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    labels = [b.evaluator for b in payload.panel_breakdown]
    assert "직무 적합도" in labels
    assert "직무 이해도" not in labels  # 빈 축은 제외


@pytest.mark.asyncio
async def test_consumer_skips_job_fit_when_not_job_tailored():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _job_fit_evaluator()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        job_fit_evaluator=evaluator,
    )
    # JD 가 실려 있어도 모드가 직무 맞춤이 아니면 평가 안 함.
    await consumer.handle(
        _StubMessage(_job_tailored_envelope(with_jd=True, mode="TECHNICAL"))
    )

    evaluator.evaluate.assert_not_awaited()
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert all(b.evaluator != "직무 적합도" for b in payload.panel_breakdown)


@pytest.mark.asyncio
async def test_consumer_skips_job_fit_when_no_jd():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _job_fit_evaluator()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        job_fit_evaluator=evaluator,
    )
    await consumer.handle(_StubMessage(_job_tailored_envelope(with_jd=False)))

    evaluator.evaluate.assert_not_awaited()


@pytest.mark.asyncio
async def test_job_fit_evaluator_forwards_inputs_to_chain():
    class _FakeChain:
        def __init__(self):
            self.input = None

        async def ainvoke(self, value):
            self.input = value
            return JobFitResult(
                fit=EvaluatorResult(score=70.0),
                understanding=EvaluatorResult(score=55.0),
            )

    chain = _FakeChain()
    evaluator = LlmJobFitEvaluator(chain)

    result = await evaluator.evaluate(
        company_name="토스",
        job_description="대용량 결제 백엔드",
        job_category="BACKEND",
        mode="JOB_TAILORED",
        transcript="Q/A",
        rag_context="resume chunk",
    )

    assert chain.input["company_name"] == "토스"
    assert chain.input["job_description"] == "대용량 결제 백엔드"
    assert chain.input["transcript"] == "Q/A"
    assert result.fit.score == 70.0
    assert result.understanding.score == 55.0


def _answer_coach():
    c = MagicMock()
    c.coach = AsyncMock(
        return_value=CoachingResult(
            model_answer="격리수준별 이상현상과 트레이드오프를 설명…",
            answer_rewrite="두괄식으로 핵심 먼저, 그다음 근거…",
            coaching_comment="결론을 먼저 말하고 근거로 받치세요.",
        )
    )
    return c


@pytest.mark.asyncio
async def test_consumer_appends_answer_coaching_excluding_self_intro():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    coach = _answer_coach()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        answer_coach=coach,
    )
    # _self_intro_envelope: 자기소개 Q(200)/A(201) + ACID Q(202)/A(203).
    await consumer.handle(_StubMessage(_self_intro_envelope()))

    # 자기소개 답변(201)은 코칭 제외, ACID 답변(203)만 코칭.
    assert coach.coach.await_count == 1
    assert "ACID" in coach.coach.await_args.kwargs["question"]

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert len(payload.answer_coaching) == 1
    item = payload.answer_coaching[0]
    assert item.message_id == 203
    assert item.model_answer.startswith("격리수준")
    assert all(c.message_id != 201 for c in payload.answer_coaching)


def test_collect_coachable_pairs_excludes_self_intro_and_empty():
    from ai_server.messaging.consumers.feedback_consumer import _collect_coachable_pairs

    msgs = [
        FeedbackMessageItem(
            id=1,
            sequence_number=1,
            role="INTERVIEWER",
            content="자기소개?",
            category="SELF_INTRODUCTION",
        ),
        FeedbackMessageItem(
            id=2,
            sequence_number=2,
            role="INTERVIEWEE",
            content="소개",
            parent_message_id=1,
        ),
        FeedbackMessageItem(
            id=3,
            sequence_number=3,
            role="INTERVIEWER",
            content="ACID?",
            category="CS_FUNDAMENTAL",
        ),
        FeedbackMessageItem(
            id=4,
            sequence_number=4,
            role="INTERVIEWEE",
            content="원자성…",
            parent_message_id=3,
        ),
        # 빈 답변은 제외
        FeedbackMessageItem(
            id=5,
            sequence_number=5,
            role="INTERVIEWER",
            content="Q2?",
            category="TECH_CHOICE",
        ),
        FeedbackMessageItem(
            id=6,
            sequence_number=6,
            role="INTERVIEWEE",
            content="   ",
            parent_message_id=5,
        ),
    ]
    pairs = _collect_coachable_pairs(msgs)
    assert [(q.id, a.id) for q, a in pairs] == [(3, 4)]


@pytest.mark.asyncio
async def test_answer_coach_forwards_inputs_to_chain():
    class _FakeChain:
        def __init__(self):
            self.input = None

        async def ainvoke(self, value):
            self.input = value
            return CoachingResult(
                model_answer="m", answer_rewrite="r", coaching_comment="c"
            )

    chain = _FakeChain()
    coach = LlmAnswerCoach(chain)

    await coach.coach(
        job_category="BACKEND",
        mode="TECHNICAL",
        target_role="",
        question="트랜잭션 격리수준?",
        expected_signal="격리수준·이상현상",
        answer="음… 잘 모르겠습니다",
        rag_context="resume chunk",
    )

    assert chain.input["question"] == "트랜잭션 격리수준?"
    assert chain.input["expected_signal"] == "격리수준·이상현상"
    assert chain.input["answer"] == "음… 잘 모르겠습니다"


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


def _personality_envelope(mode: str) -> bytes:
    env = {
        "messageId": "fb-pers",
        "messageType": "generate.feedback",
        "version": "v1",
        "traceId": "t-pers",
        "publishedAt": "2026-05-30T00:00:00Z",
        "publisher": "core-server",
        "payload": {
            "sessionId": 61,
            "mode": mode,
            "jobCategory": "BACKEND",
            "totalQuestionCount": 2,
            "endReason": "POOL_EXHAUSTED",
            "messages": [
                {
                    "id": 300,
                    "sequenceNumber": 1,
                    "role": "INTERVIEWER",
                    "content": "리더십을 발휘한 경험을 말해보세요.",
                    "category": "BEHAVIORAL",
                },
                {
                    "id": 301,
                    "sequenceNumber": 2,
                    "role": "INTERVIEWEE",
                    "content": "배포 지연 문제에서 제가 온콜 로테이션을 제안해 장애 대응 시간을 절반으로 줄였습니다.",
                    "parentMessageId": 300,
                },
            ],
            "contextDocumentIds": [],
        },
        "context": {"userId": 1, "sessionId": 61},
    }
    return json.dumps(env).encode()


def _personality_evaluator():
    ev = MagicMock()
    ev.evaluate = AsyncMock(
        return_value=EvaluatorResult(
            score=72.0,
            strength="구체적 행동·결과 제시",
            weakness="STAR 상황 설명 부족",
            detail="온콜 로테이션 제안과 대응시간 절반 감소를 구체적으로 언급.",
            score_rationale="본인 행동·결과는 명확하나 상황 맥락이 약함",
        )
    )
    return ev


def _make_consumer(publisher, generator, evaluator):
    return FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        personality_evaluator=evaluator,
    )


@pytest.mark.asyncio
async def test_consumer_appends_personality_panel_item():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _personality_evaluator()
    consumer = _make_consumer(publisher, generator, evaluator)

    await consumer.handle(_StubMessage(_personality_envelope("PERSONALITY")))

    evaluator.evaluate.assert_awaited_once()
    assert "온콜" in evaluator.evaluate.await_args.kwargs["transcript"]
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    items = [b for b in payload.panel_breakdown if b.evaluator == "인성·자소서"]
    assert len(items) == 1
    assert items[0].score == 72.0
    # 가산·비집계: 종합 점수는 불변.
    assert payload.overall_score == 85.0


@pytest.mark.asyncio
async def test_consumer_appends_personality_in_integrated():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _personality_evaluator()
    consumer = _make_consumer(publisher, generator, evaluator)

    await consumer.handle(_StubMessage(_personality_envelope("INTEGRATED")))

    evaluator.evaluate.assert_awaited_once()
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert any(b.evaluator == "인성·자소서" for b in payload.panel_breakdown)


@pytest.mark.asyncio
async def test_consumer_skips_personality_in_technical_mode():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    evaluator = _personality_evaluator()
    consumer = _make_consumer(publisher, generator, evaluator)

    await consumer.handle(_StubMessage(_personality_envelope("TECHNICAL")))

    evaluator.evaluate.assert_not_awaited()
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert all(b.evaluator != "인성·자소서" for b in payload.panel_breakdown)


@pytest.mark.asyncio
async def test_consumer_emits_feedback_progress_with_scoring_counter():
    """B2: 피드백 생성 중 세션 채널 진행 이벤트 — PREPARING → SCORING(0/5) →
    세부 평가 완료마다 카운터 증가(1..5) → FINALIZING 순서·개수를 고정한다."""
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    session_notifier = MagicMock()
    session_notifier.emit_progress = AsyncMock()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        session_notifier=session_notifier,
    )
    await consumer.handle(_StubMessage(_envelope()))

    calls = session_notifier.emit_progress.await_args_list
    assert len(calls) == 8  # 1 PREPARING + 1 SCORING(0/5) + 5 완료 + 1 FINALIZING
    phases = [c.kwargs["phase"] for c in calls]
    assert phases[0] == "PREPARING"
    assert phases[1] == "SCORING"
    assert (calls[1].kwargs["completed"], calls[1].kwargs["total"]) == (0, 5)
    assert phases[-1] == "FINALIZING"
    completions = [
        c.kwargs["completed"]
        for c in calls
        if c.kwargs["phase"] == "SCORING" and c.kwargs["completed"]
    ]
    assert sorted(completions) == [1, 2, 3, 4, 5]
    assert all(c.kwargs["event_type"] == "FEEDBACK_PROGRESS" for c in calls)
    assert all(c.kwargs["session_id"] == 50 for c in calls)
    publisher.publish.assert_awaited_once()


@pytest.mark.asyncio
async def test_consumer_without_session_notifier_still_publishes_callback():
    """B2 회귀 고정: notifier 미주입(None) 이면 진행 이벤트 없이 기존 흐름 그대로."""
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
    await consumer.handle(_StubMessage(_envelope()))
    publisher.publish.assert_awaited_once()


# ---------------------------------------------------------------------------
# 마크다운 리포트 저장 (reportS3Key)


class _FakeStorage:
    """put_text 호출을 (key, text, content_type) 로 축적. fail=True 면 저장 실패 재현."""

    def __init__(self, fail: bool = False):
        self.puts: list[tuple[str, str, str | None]] = []
        self._fail = fail

    async def put_text(
        self, key, text, *, content_type: str | None = "text/markdown; charset=utf-8"
    ):
        if self._fail:
            raise RuntimeError("minio down")
        self.puts.append((key, text, content_type))


@pytest.mark.asyncio
async def test_consumer_saves_markdown_report_and_sets_key():
    generator = _generator()
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    storage = _FakeStorage()

    consumer = FeedbackConsumer(
        generator=generator,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.feedback",
        core_client=MagicMock(),
        embedder=None,
        storage=storage,
        report_key_template="feedback/{session_id}/report.md",
    )
    await consumer.handle(_StubMessage(_envelope()))

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "OK"
    assert payload.report_s3_key == "feedback/50/report.md"
    assert len(storage.puts) == 1
    key, text, content_type = storage.puts[0]
    assert key == "feedback/50/report.md"
    assert text.startswith("# 면접 피드백 리포트")
    assert "ACID 4요소를 명확히 답변." in text  # 결과 요약이 실제로 담긴다


@pytest.mark.asyncio
async def test_report_save_failure_falls_back_to_none_and_keeps_feedback_ok():
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
        storage=_FakeStorage(fail=True),
        report_key_template="feedback/{session_id}/report.md",
    )
    await consumer.handle(_StubMessage(_envelope()))

    # 리포트는 부가 산출물 — 저장 실패가 피드백을 FAILED 로 승격시키면 안 된다.
    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.status == "OK"
    assert payload.overall_score == 85.0
    assert payload.report_s3_key is None


@pytest.mark.asyncio
async def test_no_storage_keeps_report_key_none():
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
    await consumer.handle(_StubMessage(_envelope()))

    payload: FeedbackCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.report_s3_key is None


# 코칭 상한·동시성은 게이트웨이 429 를 막는 안전밸브다. 상수로 박혀 있으면 게이트웨이가
# 조이기 시작해도 재배포 없이는 낮출 수 없다 — runner 가 settings 값을 실제로 전달하는지 고정.
def test_runner_wires_coaching_limits_from_settings() -> None:
    import inspect

    from ai_server.messaging import runner as runner_module

    # 공백·줄바꿈에 깨지지 않게 정규화한 뒤 본다. 런타임 조립에 필요한 의존성이 많아
    # 인스턴스를 만들어 검증하기보다 배선 자체를 고정하는 쪽이 싸다.
    source = "".join(inspect.getsource(runner_module).split())
    assert "coaching_max_answers=settings.feedback_coaching_max_answers" in source
    assert "coaching_concurrency=settings.feedback_coaching_concurrency" in source


def test_coaching_limits_are_settings_backed() -> None:
    from ai_server.config.settings import Settings

    fields = Settings.model_fields
    assert "feedback_coaching_max_answers" in fields
    assert "feedback_coaching_concurrency" in fields
