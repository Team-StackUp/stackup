"""메시지 처리 구간에 traceId 를 묶는다.

`ai/CLAUDE.md` 는 오래 전부터 컨슈머 패턴으로 이렇게 적어 왔다::

    with trace_context(envelope.trace_id):
        await resume_analyzer.handle(envelope.payload)

그런데 **`trace_context` 가 존재한 적이 없다.** 문서를 따라 쓰면 ImportError 가 나고,
실제 컨슈머들은 traceId 를 로그에 전혀 싣지 않았다 — 운영 AI 로그에 trace 가 하나도
없는 이유다. Core 는 2026-09-28 에 같은 문제를 고쳤고(로그 패턴·필터 순서·큐 전파),
이건 그 나머지 절반이다.

structlog 기본 프로세서 체인의 첫 번째가 ``merge_contextvars`` 라서, 여기서 한 번 묶어
두면 **그 구간의 모든 로그 줄에 자동으로 실린다** — 호출부마다 인자로 넘길 필요가 없다.
contextvars 는 asyncio 태스크별로 격리되므로 동시에 처리되는 메시지끼리 섞이지 않는다.
"""

from __future__ import annotations

import contextlib
import uuid
from collections.abc import Iterator

import structlog


@contextlib.contextmanager
def trace_context(trace_id: str | None) -> Iterator[str]:
    """`trace_id` 를 이 구간의 모든 로그에 바인딩한다. 실제 쓰인 값을 돌려준다.

    비어 있으면 새로 만든다 — 없는 것보다 낫고, 적어도 그 처리 한 건은 묶인다.
    끝나면 반드시 되돌린다: 워커 태스크가 재사용되면 남은 값이 다음 메시지 로그에 묻는다.
    """
    effective = trace_id if trace_id else str(uuid.uuid4())
    tokens = structlog.contextvars.bind_contextvars(trace_id=effective)
    try:
        yield effective
    finally:
        structlog.contextvars.reset_contextvars(**tokens)
