from __future__ import annotations

import math

import httpx
import pytest

from ai_server.voice.stt.base import SttError
from ai_server.voice.stt.deepgram import DeepgramSttProvider


def _client(*, status: int, body: dict | str) -> httpx.AsyncClient:
    def handler(request: httpx.Request) -> httpx.Response:
        if isinstance(body, dict):
            return httpx.Response(status, json=body)
        return httpx.Response(status, text=body)

    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


@pytest.mark.asyncio
async def test_deepgram_returns_segments_and_logprob_from_utterances():
    response = {
        "metadata": {"duration": 6.0},
        "results": {
            "channels": [
                {
                    "alternatives": [
                        {
                            "transcript": "안녕하세요 백엔드 지원자입니다",
                            "confidence": 0.9,
                        }
                    ]
                }
            ],
            "utterances": [
                {"start": 0.0, "end": 2.5, "transcript": "안녕하세요", "confidence": 0.95},
                {"start": 3.0, "end": 6.0, "transcript": "백엔드 지원자입니다", "confidence": 0.88},
            ],
        },
    }
    async with _client(status=200, body=response) as client:
        provider = DeepgramSttProvider(api_key="k", client=client)
        result = await provider.transcribe(audio_bytes=b"fake", content_type="audio/webm")

    assert result.text.startswith("안녕하세요")
    assert result.duration_sec == 6.0
    assert len(result.segments) == 2
    # confidence 0.95 → logprob ≈ ln(0.95)
    assert abs(result.segments[0].avg_logprob - math.log(0.95)) < 1e-6


@pytest.mark.asyncio
async def test_deepgram_fallback_segment_when_no_utterances():
    response = {
        "metadata": {"duration": 4.2},
        "results": {
            "channels": [
                {
                    "alternatives": [
                        {"transcript": "한 줄 답변", "confidence": 0.82},
                    ]
                }
            ],
        },
    }
    async with _client(status=200, body=response) as client:
        provider = DeepgramSttProvider(api_key="k", client=client)
        result = await provider.transcribe(audio_bytes=b"fake", content_type="audio/webm")

    assert result.text == "한 줄 답변"
    assert len(result.segments) == 1
    assert result.segments[0].end_sec == 4.2


@pytest.mark.asyncio
async def test_deepgram_auth_error_raises_non_retriable():
    async with _client(status=401, body="unauthorized") as client:
        provider = DeepgramSttProvider(api_key="k", client=client)
        with pytest.raises(SttError) as exc_info:
            await provider.transcribe(audio_bytes=b"x", content_type="audio/webm")
    assert exc_info.value.code == "STT_AUTH_FAILED"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_deepgram_5xx_is_retriable():
    async with _client(status=502, body="bad gateway") as client:
        provider = DeepgramSttProvider(api_key="k", client=client)
        with pytest.raises(SttError) as exc_info:
            await provider.transcribe(audio_bytes=b"x", content_type="audio/webm")
    assert exc_info.value.code == "STT_UNAVAILABLE"
    assert exc_info.value.retriable is True
