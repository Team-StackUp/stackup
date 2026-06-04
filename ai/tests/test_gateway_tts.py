from __future__ import annotations

import wave
import io

import httpx
import pytest

from ai_server.voice.tts.base import TtsError
from ai_server.voice.tts.gateway import GatewayTtsProvider


def _client(*, status: int, content: bytes, content_type: str) -> httpx.AsyncClient:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            status, content=content, headers={"content-type": content_type}
        )

    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


@pytest.mark.asyncio
async def test_gateway_tts_wraps_raw_pcm_into_wav():
    pcm = b"\x00\x01" * 24000  # 1초 분량(24kHz mono 16-bit)
    async with _client(status=200, content=pcm, content_type="audio/pcm") as client:
        provider = GatewayTtsProvider(
            api_key="k",
            base_url="https://gw/v1/gateway",
            model="gemini-2.5-flash-preview-tts",
            voice="Kore",
            timeout_sec=5.0,
            client=client,
        )
        result = await provider.synthesize("질문입니다", voice="alloy")

    assert result.content_type == "audio/wav"
    # 유효한 WAV 컨테이너인지 확인
    with wave.open(io.BytesIO(result.audio_bytes), "rb") as wav:
        assert wav.getframerate() == 24000
        assert wav.getnchannels() == 1
    assert result.duration_sec == pytest.approx(1.0, abs=0.05)


@pytest.mark.asyncio
async def test_gateway_tts_raises_on_error_status():
    async with _client(
        status=400, content=b'{"detail":"bad"}', content_type="application/json"
    ) as client:
        provider = GatewayTtsProvider(
            api_key="k",
            base_url="https://gw/v1/gateway",
            model="gemini-2.5-flash-preview-tts",
            voice="Kore",
            timeout_sec=5.0,
            client=client,
        )
        with pytest.raises(TtsError):
            await provider.synthesize("질문", voice="alloy")
