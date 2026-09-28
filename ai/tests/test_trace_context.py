"""traceId 가 **실제로 로그 줄에 실리는지** 확인한다.

Core 쪽에서 똑같은 함정을 겪었다: MDC 는 채우는데 로그 패턴이 참조하지 않아
운영 로그 어디에도 traceId 가 없었다. "바인딩했다"가 아니라 "출력에 나온다"를 본다.
"""

from __future__ import annotations

import asyncio

import structlog

from ai_server.observability.trace import trace_context


def _capture():
    cap = structlog.testing.LogCapture()
    structlog.configure(
        processors=[structlog.contextvars.merge_contextvars, cap],
    )
    return cap


def teardown_function() -> None:
    structlog.reset_defaults()
    structlog.contextvars.clear_contextvars()


def test_trace_id_appears_in_log_output():
    cap = _capture()
    log = structlog.get_logger("t")

    with trace_context("envelope-trace-1"):
        log.info("inside")
    log.info("outside")

    assert cap.entries[0]["trace_id"] == "envelope-trace-1"
    # 빠져나오면 지워져야 한다 — 남으면 다음 메시지 로그에 엉뚱한 traceId 가 묻는다.
    assert "trace_id" not in cap.entries[1]


def test_trace_id_generated_when_missing():
    cap = _capture()
    log = structlog.get_logger("t")

    with trace_context(None) as a:
        log.info("a")
    with trace_context("") as b:
        log.info("b")

    # 없는 것보다 낫다 — 적어도 그 처리 한 건은 묶인다.
    assert a and b and a != b
    assert cap.entries[0]["trace_id"] == a
    assert cap.entries[1]["trace_id"] == b


def test_trace_id_cleared_on_exception():
    cap = _capture()
    log = structlog.get_logger("t")

    try:
        with trace_context("t1"):
            raise RuntimeError("boom")
    except RuntimeError:
        pass  # 컨슈머는 예외를 다시 던져 DLQ 로 보낸다 — 그 경로에서도 정리돼야 한다
    log.info("after")

    assert "trace_id" not in cap.entries[0]


def test_concurrent_messages_do_not_leak_into_each_other():
    """컨슈머는 메시지를 동시에 처리한다. contextvars 가 태스크별로 격리되지 않으면
    A 의 traceId 가 B 의 로그에 묻는다 — 추적이 틀린 것은 없는 것보다 나쁘다."""
    cap = _capture()
    log = structlog.get_logger("t")

    async def work(tid: str, delay: float) -> None:
        with trace_context(tid):
            await asyncio.sleep(delay)
            log.info("done", which=tid)

    async def main() -> None:
        await asyncio.gather(work("A", 0.02), work("B", 0.01), work("C", 0.0))

    asyncio.run(main())

    seen = {e["which"]: e["trace_id"] for e in cap.entries}
    assert seen == {"A": "A", "B": "B", "C": "C"}


# ── ai_request_logs 세션 귀속 ────────────────────────────────────────────────
#
# record_ai_call 과 CoreAiLogCallback 은 둘 다 session_id 를 받을 수 있게 만들어 놨는데
# 실제로 넘기는 호출부가 stt.transcribe 하나뿐이어서, 2026-08-06 이래 ai_request_logs 의
# session_id 는 그 한 종류 말고 전부 NULL 이었다. "이 면접이 얼마나 들었나", "어느 세션이
# 게이트웨이 크레딧을 태웠나" 를 답할 수 없다.


class _RecordingCore:
    def __init__(self) -> None:
        self.calls: list[dict] = []

    async def record_ai_log(self, **kwargs) -> None:
        self.calls.append(kwargs)


def test_record_ai_call_picks_up_session_from_context():
    from ai_server.observability.ai_call_log import record_ai_call

    core = _RecordingCore()

    async def main() -> None:
        with trace_context("t", session_id=102, user_id=7):
            record_ai_call(
                core,
                request_type="tts.synthesize",
                model_name="m",
                latency_ms=10,
                status="SUCCESS",
            )
        # fire-and-forget 이라 태스크가 돌 틈을 준다
        await asyncio.sleep(0)
        await asyncio.sleep(0)

    asyncio.run(main())

    assert core.calls, "기록이 아예 안 됐다"
    assert core.calls[0]["session_id"] == 102
    assert core.calls[0]["user_id"] == 7


def test_explicit_session_wins_over_context():
    from ai_server.observability.ai_call_log import record_ai_call

    core = _RecordingCore()

    async def main() -> None:
        with trace_context("t", session_id=102):
            # 호출부가 명시하면 그게 이긴다(voice_consumer 가 그렇게 쓴다).
            record_ai_call(
                core,
                request_type="stt.transcribe",
                model_name="m",
                latency_ms=10,
                status="SUCCESS",
                session_id=999,
            )
        await asyncio.sleep(0)
        await asyncio.sleep(0)

    asyncio.run(main())

    assert core.calls[0]["session_id"] == 999


def test_no_session_context_records_none_not_crash():
    from ai_server.observability.ai_call_log import record_ai_call

    core = _RecordingCore()

    async def main() -> None:
        # 문서 분석처럼 세션이 없는 흐름 — 기록은 되고 session_id 만 비어야 한다.
        with trace_context("t"):
            record_ai_call(
                core,
                request_type="analyze.document",
                model_name="m",
                latency_ms=10,
                status="SUCCESS",
            )
        await asyncio.sleep(0)
        await asyncio.sleep(0)

    asyncio.run(main())

    assert core.calls[0]["session_id"] is None


def test_langchain_callback_picks_up_session_from_context():
    from ai_server.observability.llm_logging_callback import CoreAiLogCallback

    core = _RecordingCore()
    cb = CoreAiLogCallback(
        core_client=core, request_type="generate.feedback.panel", default_model="m"
    )

    async def main() -> None:
        with trace_context("t", session_id=102, user_id=7):
            await cb._fire(
                request_type="generate.feedback.panel",
                model_name="m",
                input_tokens=1,
                output_tokens=2,
                latency_ms=3,
                status="SUCCESS",
                error_message=None,
            )
        await asyncio.sleep(0)
        await asyncio.sleep(0)

    asyncio.run(main())

    # 체인 빌더는 세션을 모른다 — 기록 시점에 컨텍스트에서 채워야 한다.
    assert core.calls[0]["session_id"] == 102
    assert core.calls[0]["user_id"] == 7


def test_session_id_also_appears_in_logs():
    cap = _capture()
    log = structlog.get_logger("t")

    with trace_context("t", session_id=102):
        log.info("inside")

    # 로그만 봐도 어느 면접의 호출인지 알 수 있어야 한다.
    assert cap.entries[0]["session_id"] == 102
