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
import contextvars
import uuid
from collections.abc import Iterator

import structlog

# 로그 바인딩과 별개로, `ai_request_logs` 기록부가 읽어 가는 값.
#
# `record_ai_call` 과 `CoreAiLogCallback` 은 둘 다 session_id 를 **받을 수** 있게 만들어
# 놨는데 실제로 넘기는 호출부가 stt.transcribe 하나뿐이었다. 그래서 2026-08-06 이래
# ai_request_logs 의 session_id 는 그 한 종류 말고 **전부 NULL** 이다 — "이 면접이 얼마나
# 들었나", "어느 세션이 게이트웨이 크레딧을 태웠나" 를 답할 수 없다(2026-09-17 에 실제로
# 크레딧이 소진돼 운영이 402 로 죽었을 때 정확히 이 질문을 못 했다).
#
# 체인 빌더마다 인자로 넘기면 LangChain 콜백 생성 지점 6곳 + 체인 팩토리 시그니처가
# 전부 오염된다. 관측값은 횡단 관심사이므로 traceId 와 같은 방식으로 컨텍스트에 싣는다.
_session_id: contextvars.ContextVar[int | None] = contextvars.ContextVar(
    "ai_log_session_id", default=None
)
_user_id: contextvars.ContextVar[int | None] = contextvars.ContextVar(
    "ai_log_user_id", default=None
)


def current_session_id() -> int | None:
    """기록부가 명시적 인자 없이 읽어 가는 세션 id."""
    return _session_id.get()


def current_user_id() -> int | None:
    return _user_id.get()


@contextlib.contextmanager
def trace_context(
    trace_id: str | None,
    *,
    session_id: int | None = None,
    user_id: int | None = None,
) -> Iterator[str]:
    """`trace_id` 를 이 구간의 모든 로그에 바인딩한다. 실제 쓰인 값을 돌려준다.

    `session_id`/`user_id` 를 주면 같은 구간의 `ai_request_logs` 기록에도 자동으로 붙는다
    (로그 줄에도 실린다 — 어느 면접의 호출인지 로그만 봐도 알 수 있게).

    비어 있으면 새로 만든다 — 없는 것보다 낫고, 적어도 그 처리 한 건은 묶인다.
    끝나면 반드시 되돌린다: 워커 태스크가 재사용되면 남은 값이 다음 메시지 로그에 묻는다.
    """
    effective = trace_id if trace_id else str(uuid.uuid4())
    bound: dict[str, object] = {"trace_id": effective}
    if session_id is not None:
        bound["session_id"] = session_id
    tokens = structlog.contextvars.bind_contextvars(**bound)
    sid_token = _session_id.set(session_id)
    uid_token = _user_id.set(user_id)
    try:
        yield effective
    finally:
        _user_id.reset(uid_token)
        _session_id.reset(sid_token)
        structlog.contextvars.reset_contextvars(**tokens)
