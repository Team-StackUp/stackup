from __future__ import annotations

import asyncio
import time
from contextlib import asynccontextmanager
from typing import AsyncIterator

import structlog

from ai_server.core.client import CoreClient

log = structlog.get_logger(__name__)

# error_message 컬럼 상한(1000)보다 넉넉히 짧게 — 원문 전체가 아니라 분류가 목적이다.
_MAX_ERROR_LEN = 500


def record_ai_call(
    core_client: CoreClient | None,
    *,
    request_type: str,
    model_name: str | None,
    latency_ms: int | None,
    status: str,
    error_message: str | None = None,
    user_id: int | None = None,
    session_id: int | None = None,
    input_tokens: int | None = None,
    output_tokens: int | None = None,
) -> None:
    """`ai_request_logs` 에 fire-and-forget 기록.

    LangChain 경로는 `CoreAiLogCallback` 이 콜백으로 잡지만 STT/TTS/임베딩처럼 체인을
    거치지 않는 외부 호출은 각자 호출해야 한다. 관측은 부가 기능이므로 기록 실패가
    본 작업을 죽이지 않는다 — 태스크로 떼어 보내고 예외는 삼킨다.
    """
    if core_client is None:
        return

    async def _do() -> None:
        try:
            await core_client.record_ai_log(
                request_type=request_type,
                model_name=model_name,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                latency_ms=latency_ms,
                status=status,
                user_id=user_id,
                session_id=session_id,
                error_message=(
                    error_message[:_MAX_ERROR_LEN] if error_message else None
                ),
            )
        except Exception as exc:
            log.warn("ai_log.failed", error=str(exc), request_type=request_type)

    asyncio.create_task(_do())


@asynccontextmanager
async def measure_ai_call(
    core_client: CoreClient | None,
    *,
    request_type: str,
    model_name: str | None,
    user_id: int | None = None,
    session_id: int | None = None,
) -> AsyncIterator[None]:
    """블록의 지연을 재서 성공/실패를 기록한다.

    수동으로 perf_counter 를 감싸면 실패 경로에서 기록을 빠뜨리기 쉽다(실제로 TTS·임베딩은
    아예 기록이 없었고, 그래서 운영 TTS 실패 43건의 원인을 사후에 알 수 없었다).
    """
    started = time.perf_counter()
    try:
        yield
    except Exception as exc:
        record_ai_call(
            core_client,
            request_type=request_type,
            model_name=model_name,
            latency_ms=_elapsed_ms(started),
            status="FAILED",
            error_message=f"{type(exc).__name__}: {exc}",
            user_id=user_id,
            session_id=session_id,
        )
        raise
    record_ai_call(
        core_client,
        request_type=request_type,
        model_name=model_name,
        latency_ms=_elapsed_ms(started),
        status="SUCCESS",
        user_id=user_id,
        session_id=session_id,
    )


def _elapsed_ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
