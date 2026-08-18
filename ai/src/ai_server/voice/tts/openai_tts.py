from __future__ import annotations

import httpx
import structlog

from ai_server.voice.tts.base import TtsError, TtsProvider, TtsResult

log = structlog.get_logger(__name__)


class OpenAiTtsProvider(TtsProvider):
    """OpenAI 오디오 합성 (POST /audio/speech). mp3 반환."""

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str,
        model: str,
        timeout_sec: float,
    ) -> None:
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self.model_name = model
        self._timeout = timeout_sec

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        url = f"{self._base_url}/audio/speech"
        body = {
            "model": self.model_name,
            "voice": voice,
            "input": text,
            "response_format": "mp3",
        }
        headers = {"Authorization": f"Bearer {self._api_key}"}
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.post(url, json=body, headers=headers)
        except httpx.HTTPError as exc:
            raise TtsError("TTS_HTTP_ERROR", str(exc)) from exc
        if resp.status_code != 200:
            raise TtsError(
                "TTS_API_ERROR",
                f"openai tts status {resp.status_code}: {resp.text[:200]}",
            )
        audio = resp.content
        if not audio:
            raise TtsError("TTS_EMPTY_AUDIO", "empty audio body")
        # OpenAI 는 duration 을 주지 않음 → None (Core completeTts 가 null 허용).
        return TtsResult(
            audio_bytes=audio, duration_sec=None, content_type="audio/mpeg"
        )
