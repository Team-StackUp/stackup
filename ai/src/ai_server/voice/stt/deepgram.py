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


class DeepgramSttProvider:
    """Deepgram /v1/listen STT 호출.

    Mindlogic 게이트웨이가 STT 미지원이라 Deepgram 직접 호출. DEEPGRAM_API_KEY 필요.
    응답에서 transcript + utterances(=문장 단위 segment) + words 활용.

    기본 nova-2. whisper-large 는 운영 음성 30회 측정에서 23%(7/30)가 20초 read
    타임아웃이었고 파일에 따라 60% 까지 갔다 — nova-2 는 같은 조건 28/28 성공에
    응답도 2~3배 빠르다. nova-3 는 더 빠르지만 없는 말을 지어내고 영문 철자가 깨져 제외.
    """

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str = "https://api.deepgram.com/v1",
        model: str = "nova-2",
        language: str | None = "ko",
        timeout_sec: float = 30.0,
        connect_timeout_sec: float = 5.0,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        if not api_key:
            raise ValueError("Deepgram API key 누락")
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._language = language
        # connect 를 따로 둔다 — 일괄 timeout 이면 연결이 막혔을 때도 read 한도만큼(운영 60초)
        # 붙잡고 있다가 실패했다. 정상 호출은 p50 3.6초라 read 30초로도 여유가 크다.
        self._timeout = httpx.Timeout(timeout_sec, connect=connect_timeout_sec)
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
        params: dict[str, str] = {
            "model": self._model,
            "smart_format": "true",
            "punctuate": "true",
            "utterances": "true",
        }
        if self._language:
            params["language"] = self._language
        # hint(직전 질문 본문)는 **보내지 않는다.** Deepgram keywords 로 실어 봤지만
        # 한국어에서는 아무 효과가 없었다. 같은 오디오로 힌트 유/무 5회씩 측정한 결과
        # 두 조건의 출력 분포가 동일했다(각각 297자 4회 + 285자 1회) — 차이처럼 보이던 것은
        # nova-2 자체의 비결정성이었다. 단어 하나 + 강도(`백오프:10`)로 줄여도 같았다.
        # 효과가 없는데 매 호출마다 질문 200자를 외부로 보내고, 언젠가 Deepgram 이 이 값을
        # 실제로 반영하기 시작하면 **답변 전사가 질문 어휘 쪽으로 끌려간다** — 채점하는
        # 서비스에서 그건 사용자가 하지 않은 말로 감점되는 것이다.
        # hint 인자는 인터페이스에 남겨 둔다(SttProvider 공통, 다른 제공자가 쓸 수 있다).

        headers = {
            "Authorization": f"Token {self._api_key}",
            "Content-Type": content_type or "application/octet-stream",
        }
        url = f"{self._base_url}/listen"

        try:
            if self._client is not None:
                resp = await self._client.post(
                    url, params=params, headers=headers, content=audio_bytes
                )
            else:
                async with httpx.AsyncClient(timeout=self._timeout) as client:
                    resp = await client.post(
                        url, params=params, headers=headers, content=audio_bytes
                    )
        except httpx.HTTPError as exc:
            # 타임아웃 예외는 str() 이 빈 문자열이라 타입을 같이 남겨야 connect/read 를 구분한다.
            detail = str(exc)
            raise SttError(
                code="STT_UNAVAILABLE",
                message=(
                    f"Deepgram 호출 실패: {type(exc).__name__}"
                    + (f": {detail}" if detail else "")
                ),
                retriable=True,
            ) from exc

        if resp.status_code in (401, 403):
            raise SttError(
                code="STT_AUTH_FAILED",
                message=f"Deepgram 인증 실패: {resp.status_code}",
                retriable=False,
            )
        if resp.status_code >= 500:
            raise SttError(
                code="STT_UNAVAILABLE",
                message=f"Deepgram 5xx: {resp.status_code}",
                retriable=True,
            )
        if resp.status_code >= 400:
            raise SttError(
                code="STT_BAD_REQUEST",
                message=f"Deepgram {resp.status_code}: {resp.text[:200]}",
                retriable=False,
            )

        try:
            data = resp.json()
        except ValueError as exc:
            raise SttError(
                code="STT_BAD_RESPONSE",
                message=f"JSON 파싱 실패: {exc}",
                retriable=True,
            ) from exc

        metadata = data.get("metadata") or {}
        results = data.get("results") or {}
        channels = results.get("channels") or []
        if not channels:
            return TranscriptionResult(
                text="", language=self._language, duration_sec=None, segments=[]
            )

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

        return sanitize_transcription(
            TranscriptionResult(
                text=text,
                language=self._language,
                duration_sec=(float(duration) if duration is not None else None),
                segments=segments,
            )
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
