from __future__ import annotations

import structlog

from ai_server.config.settings import Settings
from ai_server.voice.stt.base import SttProvider
from ai_server.voice.stt.deepgram import DeepgramSttProvider
from ai_server.voice.stt.mock import MockSttProvider
from ai_server.voice.stt.openai_whisper import OpenAiWhisperSttProvider

log = structlog.get_logger(__name__)


def build_stt_provider(settings: Settings) -> SttProvider:
    """STT 공급자 선택.

    명시적 우선순위:
      1. STT_PROVIDER 가 명시되면 그 값을 따름.
      2. "auto" 인 경우 DEEPGRAM_API_KEY → OPENAI_API_KEY → mock 순.
      3. 키 누락 시에는 안전한 mock fallback (서비스 부팅은 항상 성공).
    """

    provider = (settings.stt_provider or "auto").lower()

    if provider == "auto":
        if settings.deepgram_api_key:
            provider = "deepgram"
        elif settings.openai_api_key:
            provider = "openai_whisper"
        else:
            provider = "mock"

    if provider == "deepgram":
        if not settings.deepgram_api_key:
            log.warn("stt.fallback_to_mock", reason="DEEPGRAM_API_KEY 누락")
            return MockSttProvider()
        return DeepgramSttProvider(
            api_key=settings.deepgram_api_key,
            base_url=settings.deepgram_base_url,
            model=settings.deepgram_model,
            language=settings.deepgram_language or None,
            timeout_sec=settings.deepgram_timeout_sec,
        )

    if provider == "openai_whisper":
        if not settings.openai_api_key:
            log.warn("stt.fallback_to_mock", reason="OPENAI_API_KEY 누락")
            return MockSttProvider()
        return OpenAiWhisperSttProvider(
            api_key=settings.openai_api_key,
            base_url=settings.openai_base_url,
            model=settings.whisper_model,
            language=settings.whisper_language or None,
            timeout_sec=settings.whisper_timeout_sec,
        )

    if provider == "mock":
        return MockSttProvider()

    log.warn("stt.unknown_provider_fallback_mock", provider=provider)
    return MockSttProvider()
