"""LLM 호출의 실제 시간 상한과 재시도 간격.

`llm_*_timeout_sec` 는 오래 "호출 상한"으로 읽혔지만 아니었다 — OpenAI SDK 클라이언트의
`max_retries` 기본값이 2 라 LangChain 호출 1회가 HTTP 3회이고, 코드가 그 값을 지정한 적이
없었다. `ai_request_logs` 는 호출 1건에 한 행이므로 `latency_ms` 는 숨은 시도의 합이다
(운영 `analyze.document` 실패 4건이 타임아웃 60초 설정에서 전부 92초로 찍혔다).

문서 분석은 거기에 바깥 `with_retry` 까지 겹쳐 최대 6번 HTTP 를 쳤고, 그런데도 두 시도의
간격이 1초(+지터)라 게이트웨이 정지 구간을 넘지 못해 4/4 로 함께 실패했다. 같은 입력이
3분 뒤 수동 재분석에서 37초로 성공했다.
"""

from __future__ import annotations

import pytest

from ai_server.chain.document_analysis_chain import build_document_analysis_chain
from ai_server.chain.feedback_generation_chain import (
    build_answer_coaching_chain,
    build_feedback_generation_chain,
    build_feedback_synthesis_chain,
    build_job_fit_evaluation_chain,
    build_panel_evaluator_chain,
    build_personality_evaluation_chain,
    build_self_intro_evaluation_chain,
)
from ai_server.chain.followup_generation_chain import (
    build_followup_generation_chain,
    build_streaming_followup_generator,
)
from ai_server.chain.pdf_vision import build_vision_pdf_reader
from ai_server.chain.question_generation_chain import build_question_generation_chain
from ai_server.config.settings import Settings


def _settings(**over) -> Settings:
    base = dict(
        rabbitmq_url="amqp://x",
        s3_endpoint_url="http://x",
        s3_access_key="a",
        s3_secret_key="b",
        s3_bucket_name="c",
        # 키를 반드시 준다 — 빈 값이면 ChatOpenAI 생성 자체가 OpenAIError 로 터진다.
        # 로컬에는 ai/.env 가 있어 통과하고 CI 에서만 깨지는 함정이다.
        llm_api_key="test-key",
    )
    base.update(over)
    return Settings(**base)


def _llm_of(chain) -> object:
    steps = getattr(chain, "steps", None) or chain.bound.steps
    return next(s for s in steps if type(s).__name__ == "ChatOpenAI")


def test_sdk_retry_default_would_triple_the_timeout_budget():
    """이 테스트가 지키는 것은 "기본값에 맡기지 않는다" 다."""
    from langchain_openai import ChatOpenAI

    bare = ChatOpenAI(model="x", timeout=60, api_key="k", base_url="http://localhost")
    # 우리가 지정하지 않으면 클라이언트가 2회 더 시도한다 → 실제 상한은 타임아웃의 3배.
    assert bare.root_async_client.max_retries == 2


# 한 곳만 빠져도 그 경로만 조용히 옛 동작(설정값의 3배)으로 떨어진다 — 전부 본다.
@pytest.mark.parametrize(
    "build",
    [
        build_question_generation_chain,
        build_feedback_generation_chain,
        build_panel_evaluator_chain,
        build_feedback_synthesis_chain,
        build_self_intro_evaluation_chain,
        build_personality_evaluation_chain,
        build_job_fit_evaluation_chain,
        build_answer_coaching_chain,
        build_followup_generation_chain,
    ],
)
def test_chains_pin_max_retries_from_settings(build):
    llm = _llm_of(build(_settings(llm_max_retries=1)))

    assert llm.max_retries == 1, "설정이 아니라 SDK 기본값(2)에 맡기고 있다"


def test_streaming_followup_generator_pins_max_retries():
    gen = build_streaming_followup_generator(_settings(llm_max_retries=1))

    assert gen._llm.max_retries == 1


def test_pdf_vision_reader_pins_max_retries():
    reader = build_vision_pdf_reader(_settings(llm_max_retries=1))

    assert reader._llm.max_retries == 1


def test_document_analysis_disables_sdk_retry_because_it_owns_one():
    # 두 층이 곱해지면 한 메시지가 HTTP 6회 + 로그 한 행이 여러 시도의 합이 된다.
    chain = build_document_analysis_chain(_settings(llm_max_retries=2))

    assert _llm_of(chain).max_retries == 0


def test_document_analysis_spaces_its_retries_far_enough():
    chain = build_document_analysis_chain(_settings())

    assert (
        chain.max_attempt_number == 3
    ), "2회로는 4일간 FAILED 로 남은 사례를 못 살린다"
    assert chain.wait_exponential_jitter is True
    initial = chain.exponential_jitter_params["initial"]
    # 1초(기본값)로는 게이트웨이 정지 구간 안에서 재시도가 함께 죽는다.
    assert initial >= 10, f"재시도 간격이 {initial}초로 너무 촘촘하다"
