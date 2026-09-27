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
                {
                    "start": 0.0,
                    "end": 2.5,
                    "transcript": "안녕하세요",
                    "confidence": 0.95,
                },
                {
                    "start": 3.0,
                    "end": 6.0,
                    "transcript": "백엔드 지원자입니다",
                    "confidence": 0.88,
                },
            ],
        },
    }
    async with _client(status=200, body=response) as client:
        provider = DeepgramSttProvider(api_key="k", client=client)
        result = await provider.transcribe(
            audio_bytes=b"fake", content_type="audio/webm"
        )

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
        result = await provider.transcribe(
            audio_bytes=b"fake", content_type="audio/webm"
        )

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


@pytest.mark.asyncio
async def test_deepgram_timeout_keeps_exception_type_in_message():
    """타임아웃 예외는 str() 이 빈 문자열이다 — 타입을 안 남기면 로그가
    'Deepgram 호출 실패: ' 로 끝나 connect/read 를 구분할 수 없었다."""

    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("", request=request)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        provider = DeepgramSttProvider(api_key="k", client=client)
        with pytest.raises(SttError) as exc_info:
            await provider.transcribe(audio_bytes=b"x", content_type="audio/webm")

    assert exc_info.value.code == "STT_UNAVAILABLE"
    assert exc_info.value.retriable is True
    assert "ConnectTimeout" in exc_info.value.message


def test_deepgram_connect_timeout_is_separate_from_read_timeout():
    """일괄 timeout 이면 연결이 막혔을 때도 read 한도만큼 붙잡고 있다가 실패한다."""
    provider = DeepgramSttProvider(
        api_key="k", timeout_sec=30.0, connect_timeout_sec=5.0
    )
    timeout = provider._timeout  # noqa: SLF001 — 구성값 회귀 고정
    assert timeout.connect == 5.0
    assert timeout.read == 30.0


def test_default_model_is_nova2_not_whisper():
    """운영 음성 30회 측정에서 whisper-large 는 23%(7/30)가 20초 타임아웃이었다.

    파일에 따라 60% 까지 갔고, 재시도 3회로도 최악 오디오는 5번에 1번꼴로 사용자에게
    실패가 보인다. nova-2 는 같은 조건 28/28 성공. 모델 기본값이 조용히 되돌아가면
    그 실패율이 함께 돌아오므로 여기서 고정한다.
    """
    # 운영이 실제로 쓰는 값은 Settings 기본값이다(factory 가 이걸 provider 에 넘긴다).
    from ai_server.config.settings import Settings

    assert Settings.model_fields["deepgram_model"].default == "nova-2"
    # provider 자체 기본값도 맞춰 둔다 — 두 곳이 어긋나면 어느 쪽이 진짜인지 헷갈린다.
    assert DeepgramSttProvider(api_key="k").model_name == "nova-2"


@pytest.mark.asyncio
async def test_keyword_hint_is_never_sent():
    """직전 질문을 keywords 로 보내지 않는다.

    한국어에서 효과가 없음을 실측했다(힌트 유/무 5회씩, 출력 분포 동일 — 차이로 보이던
    것은 nova-2 의 비결정성이었다). 효과가 없는데 매 호출마다 질문 200자를 외부로 보내고,
    Deepgram 이 언젠가 이 값을 반영하면 답변 전사가 질문 어휘로 끌려간다.
    """
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen.update(dict(request.url.params))
        return httpx.Response(200, json={"metadata": {}, "results": {"channels": []}})

    for model in ("nova-2", "nova-3", "whisper-large"):
        seen.clear()
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            provider = DeepgramSttProvider(api_key="k", model=model, client=client)
            await provider.transcribe(
                audio_bytes=b"x",
                content_type="audio/webm",
                hint="ACID 를 설명해 주세요",
            )
        assert seen["model"] == model
        assert "keywords" not in seen, f"{model} 에서 keywords 가 실렸다"
