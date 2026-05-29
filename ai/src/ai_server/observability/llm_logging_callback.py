from __future__ import annotations

import asyncio
import time
from typing import Any
from uuid import UUID

import structlog
from langchain_core.callbacks import AsyncCallbackHandler
from langchain_core.outputs import LLMResult

from ai_server.core.client import CoreClient

log = structlog.get_logger(__name__)


class CoreAiLogCallback(AsyncCallbackHandler):
    """LangChain LLM 호출별로 input/output 토큰 + latency 를 측정해 Core 에 fire-and-forget POST.

    request_type 은 chain 별로 미리 정해 (예: 'analyze.document', 'generate.questions',
    'generate.followup'). 핸들러는 LangChain async 콜백을 받아 start/end timestamp 와 응답
    metadata 에서 토큰 수를 추출한다.
    """

    def __init__(self, *, core_client: CoreClient, request_type: str, default_model: str) -> None:
        self._core = core_client
        self._request_type = request_type
        self._default_model = default_model
        self._start_at: dict[UUID, float] = {}

    async def on_llm_start(self, serialized, prompts, *, run_id: UUID, **kwargs: Any) -> None:
        self._start_at[run_id] = time.perf_counter()

    async def on_llm_end(self, response: LLMResult, *, run_id: UUID, **kwargs: Any) -> None:
        latency_ms = self._latency_ms(run_id)
        in_tok, out_tok, model = _extract_usage(response, self._default_model)
        await self._fire(
            request_type=self._request_type,
            model_name=model,
            input_tokens=in_tok,
            output_tokens=out_tok,
            latency_ms=latency_ms,
            status="SUCCESS",
            error_message=None,
        )

    async def on_llm_error(self, error: BaseException, *, run_id: UUID, **kwargs: Any) -> None:
        latency_ms = self._latency_ms(run_id)
        await self._fire(
            request_type=self._request_type,
            model_name=self._default_model,
            input_tokens=None,
            output_tokens=None,
            latency_ms=latency_ms,
            status="FAILED",
            error_message=str(error)[:1000],
        )

    def _latency_ms(self, run_id: UUID) -> int | None:
        started = self._start_at.pop(run_id, None)
        if started is None:
            return None
        return int((time.perf_counter() - started) * 1000)

    async def _fire(self, **kwargs: Any) -> None:
        # fire-and-forget: 본 핸들러 실패가 LLM 응답 흐름을 막지 않도록.
        async def _do() -> None:
            try:
                await self._core.record_ai_log(**kwargs)
            except Exception as exc:
                log.warn("core.ai_log.background_failed", error=str(exc), **kwargs)
        asyncio.create_task(_do())


def _extract_usage(response: LLMResult, default_model: str) -> tuple[int | None, int | None, str]:
    in_tok = out_tok = None
    model = default_model
    try:
        llm_output = response.llm_output or {}
        if isinstance(llm_output, dict):
            model = llm_output.get("model_name") or llm_output.get("model") or model
            usage = llm_output.get("token_usage") or llm_output.get("usage") or {}
            if isinstance(usage, dict):
                in_tok = usage.get("prompt_tokens") or usage.get("input_tokens")
                out_tok = usage.get("completion_tokens") or usage.get("output_tokens")

        if (in_tok is None or out_tok is None) and response.generations:
            for gen_list in response.generations:
                for gen in gen_list:
                    info = getattr(gen, "generation_info", None) or {}
                    usage = info.get("usage_metadata") or info.get("token_usage") or {}
                    if isinstance(usage, dict):
                        in_tok = in_tok or usage.get("input_tokens") or usage.get("prompt_tokens")
                        out_tok = out_tok or usage.get("output_tokens") or usage.get("completion_tokens")
    except Exception as exc:
        log.debug("ai_log.usage_extract_failed", error=str(exc))
    return in_tok, out_tok, model
