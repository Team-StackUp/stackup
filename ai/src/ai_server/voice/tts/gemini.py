from __future__ import annotations

import base64
import io
import wave

import httpx
import structlog

from ai_server.voice.tts.base import TtsError, TtsProvider, TtsResult

log = structlog.get_logger(__name__)


def _parse_rate(mime: str, default: int = 24000) -> int:
    # 예: "audio/L16;codec=pcm;rate=24000"
    for part in mime.split(";"):
        part = part.strip()
        if part.startswith("rate="):
            try:
                return int(part[len("rate=") :])
            except ValueError:
                return default
    return default


def pcm_to_wav(
    pcm: bytes, *, sample_rate: int, channels: int = 1, sample_width: int = 2
) -> bytes:
    """raw PCM(L16) 을 WAV 컨테이너로 감싼다(브라우저 재생 가능)."""
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(sample_width)
        wav.setframerate(sample_rate)
        wav.writeframes(pcm)
    return buf.getvalue()


class GeminiTtsProvider(TtsProvider):
    """Gemini TTS (generateContent, responseModalities=AUDIO). 한국어 지원.

    Gemini 는 raw PCM(L16, mono)을 base64 로 반환하므로 WAV 로 감싸 audio/wav 로 저장한다.
    Deepgram/OpenAI TTS 가 한국어 미지원이라 기존 GEMINI_API_KEY 를 재사용한다.
    """

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str,
        model: str,
        voice: str,
        timeout_sec: float,
    ) -> None:
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self.model_name = model
        self._voice = voice
        self._timeout = timeout_sec

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        # consumer 가 넘기는 voice 는 OpenAI 용("alloy") 이므로 Gemini 전용 voice 를 쓴다.
        voice_name = self._voice
        url = f"{self._base_url}/models/{self.model_name}:generateContent"
        body = {
            "contents": [{"parts": [{"text": text}]}],
            "generationConfig": {
                "responseModalities": ["AUDIO"],
                "speechConfig": {
                    "voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice_name}}
                },
            },
        }
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.post(url, params={"key": self._api_key}, json=body)
        except httpx.HTTPError as exc:
            raise TtsError("TTS_HTTP_ERROR", str(exc)) from exc
        if resp.status_code != 200:
            raise TtsError(
                "TTS_API_ERROR",
                f"gemini tts status {resp.status_code}: {resp.text[:200]}",
            )
        try:
            data = resp.json()
            part = data["candidates"][0]["content"]["parts"][0]
            inline = part.get("inlineData") or part.get("inline_data")
            pcm = base64.b64decode(inline["data"])
            mime = inline.get("mimeType") or inline.get("mime_type") or ""
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise TtsError("TTS_EMPTY_AUDIO", f"gemini tts no audio: {exc}") from exc
        if not pcm:
            raise TtsError("TTS_EMPTY_AUDIO", "empty pcm")
        rate = _parse_rate(mime)
        wav = pcm_to_wav(pcm, sample_rate=rate)
        duration = round(len(pcm) / float(rate * 2), 2)  # mono 16-bit
        return TtsResult(
            audio_bytes=wav, duration_sec=duration, content_type="audio/wav"
        )
