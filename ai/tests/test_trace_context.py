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
