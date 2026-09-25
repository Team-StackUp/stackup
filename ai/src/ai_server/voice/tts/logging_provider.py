from __future__ import annotations

from ai_server.core.client import CoreClient
from ai_server.observability.ai_call_log import measure_ai_call
from ai_server.voice.tts.base import TtsProvider, TtsResult


class LoggingTtsProvider(TtsProvider):
    """TTS 합성 호출을 `ai_request_logs` 에 남기는 데코레이터.

    consumer 가 아니라 provider 를 감싸는 이유: TTS 는 `generate.tts` consumer 말고도
    followup consumer 의 문장 단위 인라인 합성(Part B)에서 호출된다. consumer 쪽에만
    계측을 넣으면 라이브 경로가 통째로 빠진다.

    세션/사용자 맥락은 싣지 않는다 — `synthesize(text, *, voice)` 시그니처를 호출부마다
    바꿔야 해서 비용 대비 이득이 적다. 실패율·지연·모델별 사용량은 이것만으로 보인다.
    """

    def __init__(self, inner: TtsProvider, core_client: CoreClient | None) -> None:
        self._inner = inner
        self._core_client = core_client
        self.model_name = inner.model_name

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        async with measure_ai_call(
            self._core_client,
            request_type="tts.synthesize",
            model_name=self._inner.model_name,
        ):
            return await self._inner.synthesize(text, voice=voice)
