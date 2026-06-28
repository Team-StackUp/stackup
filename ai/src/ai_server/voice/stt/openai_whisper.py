from __future__ import annotations

import httpx
import structlog

from ai_server.voice.stt.base import (
    SttError,
    TranscriptionResult,
    TranscriptionSegment,
)
from ai_server.voice.stt.sanitize import sanitize_transcription

log = structlog.get_logger(__name__)


class OpenAiWhisperSttProvider:
    """OpenAI Whisper API (`/audio/transcriptions`) 호출.

    Mindlogic 게이트웨이가 STT 미지원이라 OpenAI 직접 호출. OPENAI_API_KEY 필요.
    response_format=verbose_json 으로 segment timestamps 와 logprob 회수.
    """

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str = "https://api.openai.com/v1",
        model: str = "whisper-1",
        language: str | None = "ko",
        timeout_sec: float = 60.0,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        if not api_key:
            raise ValueError("OpenAI API key 누락")
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._language = language
        self._timeout_sec = timeout_sec
        self._client = client

    @property
    def model_name(self) -> str | None:
        return self._model

    async def transcribe(
        self,
        *,
        audio_bytes: bytes,
        content_type: str,
        hint: str | None = None,
    ) -> TranscriptionResult:
        url = f"{self._base_url}/audio/transcriptions"
        filename = "audio." + _ext_for(content_type)
        files = {"file": (filename, audio_bytes, content_type)}
        data: dict[str, str] = {
            "model": self._model,
            "response_format": "verbose_json",
        }
        if self._language:
            data["language"] = self._language
        if hint:
            data["prompt"] = hint[:200]
        headers = {"Authorization": f"Bearer {self._api_key}"}

        try:
            if self._client is not None:
                resp = await self._client.post(
                    url, headers=headers, files=files, data=data
                )
            else:
                async with httpx.AsyncClient(timeout=self._timeout_sec) as client:
                    resp = await client.post(
                        url, headers=headers, files=files, data=data
                    )
        except httpx.HTTPError as exc:
            raise SttError(
                code="STT_UNAVAILABLE",
                message=f"OpenAI 호출 실패: {exc}",
                retriable=True,
            ) from exc

        if resp.status_code in (401, 403):
            raise SttError(
                code="STT_AUTH_FAILED",
                message=f"OpenAI 인증 실패: {resp.status_code}",
                retriable=False,
            )
        if resp.status_code >= 500:
            raise SttError(
                code="STT_UNAVAILABLE",
                message=f"OpenAI 5xx: {resp.status_code}",
                retriable=True,
            )
        if resp.status_code >= 400:
            raise SttError(
                code="STT_BAD_REQUEST",
                message=f"OpenAI {resp.status_code}: {resp.text[:200]}",
                retriable=False,
            )

        try:
            data_resp = resp.json()
        except ValueError as exc:
            raise SttError(
                code="STT_BAD_RESPONSE",
                message=f"JSON 파싱 실패: {exc}",
                retriable=True,
            ) from exc

        segments = []
        for seg in data_resp.get("segments") or []:
            segments.append(
                TranscriptionSegment(
                    start_sec=float(seg.get("start", 0.0)),
                    end_sec=float(seg.get("end", 0.0)),
                    text=str(seg.get("text", "")),
                    avg_logprob=(
                        float(seg["avg_logprob"]) if "avg_logprob" in seg else None
                    ),
                )
            )
        return sanitize_transcription(
            TranscriptionResult(
                text=str(data_resp.get("text", "")),
                language=data_resp.get("language"),
                duration_sec=(
                    float(data_resp["duration"]) if "duration" in data_resp else None
                ),
                segments=segments,
            )
        )


def _ext_for(content_type: str) -> str:
    ct = (content_type or "").lower()
    if "webm" in ct:
        return "webm"
    if "ogg" in ct:
        return "ogg"
    if "mpeg" in ct or "mp3" in ct:
        return "mp3"
    if "wav" in ct:
        return "wav"
    if "mp4" in ct or "m4a" in ct:
        return "m4a"
    return "bin"
