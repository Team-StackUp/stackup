from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.observability.ai_call_log import measure_ai_call, record_ai_call
from ai_server.voice.tts.base import TtsResult
from ai_server.voice.tts.logging_provider import LoggingTtsProvider


def _core() -> MagicMock:
    core = MagicMock()
    core.record_ai_log = AsyncMock()
    return core


@pytest.mark.asyncio
async def test_record_is_fire_and_forget_and_swallows_failure():
    """관측이 본 작업을 죽이면 안 된다 — Core 가 죽어도 호출부는 영향을 받지 않는다."""
    core = _core()
    core.record_ai_log.side_effect = RuntimeError("core down")

    record_ai_call(
        core,
        request_type="tts.synthesize",
        model_name="m",
        latency_ms=10,
        status="SUCCESS",
    )
    await asyncio.sleep(0)
    await asyncio.sleep(0)

    core.record_ai_log.assert_awaited_once()


@pytest.mark.asyncio
async def test_record_without_core_client_is_noop():
    record_ai_call(None, request_type="x", model_name=None, latency_ms=1, status="OK")
    await asyncio.sleep(0)


@pytest.mark.asyncio
async def test_record_truncates_long_error_message():
    core = _core()
    record_ai_call(
        core,
        request_type="x",
        model_name=None,
        latency_ms=1,
        status="FAILED",
        error_message="e" * 5000,
    )
    await asyncio.sleep(0)
    assert len(core.record_ai_log.await_args.kwargs["error_message"]) == 500


@pytest.mark.asyncio
async def test_measure_records_success_with_latency():
    core = _core()
    async with measure_ai_call(core, request_type="embedding.embed", model_name="g"):
        await asyncio.sleep(0.01)
    await asyncio.sleep(0)

    kwargs = core.record_ai_log.await_args.kwargs
    assert kwargs["status"] == "SUCCESS"
    assert kwargs["request_type"] == "embedding.embed"
    assert kwargs["latency_ms"] >= 5


@pytest.mark.asyncio
async def test_measure_records_failure_and_reraises():
    """실패 경로 기록을 빠뜨리면 '왜 실패했는지'가 영원히 남지 않는다."""
    core = _core()
    with pytest.raises(ValueError):
        async with measure_ai_call(core, request_type="tts.synthesize", model_name="m"):
            raise ValueError("boom")
    await asyncio.sleep(0)

    kwargs = core.record_ai_log.await_args.kwargs
    assert kwargs["status"] == "FAILED"
    assert kwargs["error_message"] == "ValueError: boom"


class _StubTts:
    model_name = "gemini-tts"

    def __init__(self, exc: Exception | None = None) -> None:
        self.exc = exc
        self.calls = 0

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        self.calls += 1
        if self.exc:
            raise self.exc
        return TtsResult(audio_bytes=b"x", duration_sec=1.0)


@pytest.mark.asyncio
async def test_logging_tts_provider_records_success_and_delegates():
    core = _core()
    inner = _StubTts()
    provider = LoggingTtsProvider(inner, core)

    result = await provider.synthesize("안녕하세요", voice="Kore")
    await asyncio.sleep(0)

    assert result.audio_bytes == b"x"
    assert inner.calls == 1
    kwargs = core.record_ai_log.await_args.kwargs
    assert kwargs["request_type"] == "tts.synthesize"
    assert kwargs["model_name"] == "gemini-tts"
    assert kwargs["status"] == "SUCCESS"


@pytest.mark.asyncio
async def test_logging_tts_provider_records_failure():
    # 운영 TTS 실패 43건의 원인을 사후에 알 수 없었던 이유가 이 기록의 부재였다.
    core = _core()
    provider = LoggingTtsProvider(_StubTts(exc=RuntimeError("gateway 400")), core)

    with pytest.raises(RuntimeError):
        await provider.synthesize("안녕하세요", voice="Kore")
    await asyncio.sleep(0)

    kwargs = core.record_ai_log.await_args.kwargs
    assert kwargs["status"] == "FAILED"
    assert "gateway 400" in kwargs["error_message"]
