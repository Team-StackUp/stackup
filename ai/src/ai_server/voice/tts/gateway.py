from __future__ import annotations

import httpx
import structlog

from ai_server.voice.tts.base import TtsError, TtsProvider, TtsResult
from ai_server.voice.tts.gemini import _parse_rate, pcm_to_wav

log = structlog.get_logger(__name__)


class GatewayTtsProvider(TtsProvider):
    """충남대 Mindlogic 게이트웨이 TTS (OpenAI 호환 /audio/speech).

    게이트웨이가 Gemini TTS 로 라우팅하며 raw PCM(L16, mono)을 바디로 직접 반환한다
    (네이티브 Gemini 의 base64 JSON 과 다름). WAV 로 감싸 audio/wav 로 저장한다.
    GEMINI_API_KEY(직접) 대신 LLM_API_KEY(게이트웨이)를 써서 직접 키의 429 부하를 던다.
    """

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str,
        model: str,
        voice: str,
        timeout_sec: float,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self.model_name = model
        self._voice = voice
        self._timeout = timeout_sec
        self._client = client

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        # consumer 가 넘기는 voice 는 OpenAI 용("alloy") 이므로 Gemini 전용 voice 를 쓴다.
        url = f"{self._base_url}/audio/speech"
        body = {"model": self.model_name, "input": text, "voice": self._voice}
        headers = {"Authorization": f"Bearer {self._api_key}"}
        try:
            if self._client is not None:
                resp = await self._client.post(url, headers=headers, json=body)
            else:
                async with httpx.AsyncClient(timeout=self._timeout) as client:
                    resp = await client.post(url, headers=headers, json=body)
        except httpx.HTTPError as exc:
            raise TtsError("TTS_HTTP_ERROR", str(exc)) from exc
        if resp.status_code != 200:
            raise TtsError(
                "TTS_API_ERROR",
                f"gateway tts status {resp.status_code}: {resp.text[:200]}",
            )
        audio = resp.content
        if not audio:
            raise TtsError("TTS_EMPTY_AUDIO", "gateway tts empty audio")

        content_type = resp.headers.get("content-type", "").lower()
        # 게이트웨이는 raw PCM(L16/24kHz) 을 반환 → WAV 로 감싼다.
        # 혹시 이미 컨테이너(wav/mpeg)면 그대로 전달.
        if (
            "pcm" in content_type
            or "l16" in content_type
            or "octet-stream" in content_type
        ):
            rate = _parse_rate(content_type)
            wav = pcm_to_wav(audio, sample_rate=rate)
            duration = round(len(audio) / float(rate * 2), 2)  # mono 16-bit
            return TtsResult(
                audio_bytes=wav, duration_sec=duration, content_type="audio/wav"
            )
        return TtsResult(
            audio_bytes=audio,
            duration_sec=None,
            content_type=content_type.split(";")[0] or "audio/mpeg",
        )
