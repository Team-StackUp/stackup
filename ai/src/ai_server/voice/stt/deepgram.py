from __future__ import annotations

import httpx
import structlog

from ai_server.voice.stt.base import (
    SttError,
    TranscriptionResult,
    TranscriptionSegment,
)

log = structlog.get_logger(__name__)


class DeepgramSttProvider:
    """Deepgram /v1/listen STT 호출.

    Mindlogic 게이트웨이가 STT 미지원이라 Deepgram 직접 호출. DEEPGRAM_API_KEY 필요.
    응답에서 transcript + utterances(=문장 단위 segment) + words 활용.

    한국어 정확도 우선 시 model=whisper-large 권장 (whisper-1 동등).
    저비용 우선 시 nova-2 (한국어는 살짝 떨어짐).
    """

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str = "https://api.deepgram.com/v1",
        model: str = "whisper-large",
        language: str | None = "ko",
        timeout_sec: float = 60.0,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        if not api_key:
            raise ValueError("Deepgram API key 누락")
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._language = language
        self._timeout_sec = timeout_sec
        self._client = client

    async def transcribe(
        self,
        *,
        audio_bytes: bytes,
        content_type: str,
        hint: str | None = None,
    ) -> TranscriptionResult:
        params: dict[str, str] = {
            "model": self._model,
            "smart_format": "true",
            "punctuate": "true",
            "utterances": "true",
        }
        if self._language:
            params["language"] = self._language
        # Deepgram keywords 는 model=nova-* 에서만 지원 (Whisper 모델은 400).
        # hint 는 한국어 정확도가 모델 자체로 충분하므로 nova 계열에서만 사용.
        if hint and self._model.startswith("nova"):
            params["keywords"] = hint[:200]

        headers = {
            "Authorization": f"Token {self._api_key}",
            "Content-Type": content_type or "application/octet-stream",
        }
        url = f"{self._base_url}/listen"

        try:
            if self._client is not None:
                resp = await self._client.post(url, params=params, headers=headers, content=audio_bytes)
            else:
                async with httpx.AsyncClient(timeout=self._timeout_sec) as client:
                    resp = await client.post(url, params=params, headers=headers, content=audio_bytes)
        except httpx.HTTPError as exc:
            raise SttError(code="STT_UNAVAILABLE", message=f"Deepgram 호출 실패: {exc}", retriable=True) from exc

        if resp.status_code in (401, 403):
            raise SttError(code="STT_AUTH_FAILED", message=f"Deepgram 인증 실패: {resp.status_code}", retriable=False)
        if resp.status_code >= 500:
            raise SttError(code="STT_UNAVAILABLE", message=f"Deepgram 5xx: {resp.status_code}", retriable=True)
        if resp.status_code >= 400:
            raise SttError(
                code="STT_BAD_REQUEST",
                message=f"Deepgram {resp.status_code}: {resp.text[:200]}",
                retriable=False,
            )

        try:
            data = resp.json()
        except ValueError as exc:
            raise SttError(code="STT_BAD_RESPONSE", message=f"JSON 파싱 실패: {exc}", retriable=True) from exc

        metadata = data.get("metadata") or {}
        results = data.get("results") or {}
        channels = results.get("channels") or []
        if not channels:
            return TranscriptionResult(text="", language=self._language, duration_sec=None, segments=[])

        alt = (channels[0].get("alternatives") or [{}])[0]
        text = str(alt.get("transcript") or "")
        duration = metadata.get("duration")

        # utterances (문장/발화 단위) 를 우선 사용. 없으면 단일 segment fallback.
        segments: list[TranscriptionSegment] = []
        for utt in results.get("utterances") or []:
            segments.append(
                TranscriptionSegment(
                    start_sec=float(utt.get("start", 0.0)),
                    end_sec=float(utt.get("end", 0.0)),
                    text=str(utt.get("transcript") or ""),
                    avg_logprob=_confidence_to_logprob(utt.get("confidence")),
                )
            )
        if not segments and text:
            segments.append(
                TranscriptionSegment(
                    start_sec=0.0,
                    end_sec=float(duration) if duration is not None else 0.0,
                    text=text,
                    avg_logprob=_confidence_to_logprob(alt.get("confidence")),
                )
            )

        return TranscriptionResult(
            text=text,
            language=self._language,
            duration_sec=(float(duration) if duration is not None else None),
            segments=segments,
        )


def _confidence_to_logprob(confidence) -> float | None:
    """Deepgram confidence(0~1) 를 logprob 으로 변환 — exp(logprob) ≈ confidence.

    metrics.analyze() 가 pronunciation_accuracy 를 exp(avg_logprob) 평균으로 산정하므로
    logprob = ln(confidence) 변환만 해주면 그대로 정확도 근사값으로 작동.
    """
    if confidence is None:
        return None
    try:
        c = float(confidence)
    except (TypeError, ValueError):
        return None
    if c <= 0:
        return None
    import math

    return math.log(min(c, 1.0))
