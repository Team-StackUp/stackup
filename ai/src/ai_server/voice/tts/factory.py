from __future__ import annotations

import structlog

from ai_server.config.settings import Settings
from ai_server.voice.tts.base import TtsProvider
from ai_server.voice.tts.gemini import GeminiTtsProvider
from ai_server.voice.tts.mock import MockTtsProvider
from ai_server.voice.tts.openai_tts import OpenAiTtsProvider

log = structlog.get_logger(__name__)


def build_tts_provider(settings: Settings) -> TtsProvider:
    """TTS 공급자 선택. auto → gemini(한국어) > openai 키 보유 순, 둘 다 없으면 mock."""
    provider = (settings.tts_provider or "auto").lower()

    if provider == "auto":
        if settings.gemini_api_key:
            provider = "gemini"
        elif settings.openai_api_key:
            provider = "openai"
        else:
            provider = "mock"

    if provider == "gemini":
        if not settings.gemini_api_key:
            log.warn("tts.fallback_to_mock", reason="GEMINI_API_KEY 누락")
            return MockTtsProvider()
        return GeminiTtsProvider(
            api_key=settings.gemini_api_key,
            base_url=settings.gemini_tts_base_url,
            model=settings.gemini_tts_model,
            voice=settings.gemini_tts_voice,
            timeout_sec=settings.gemini_tts_timeout_sec,
        )

    if provider == "openai":
        if not settings.openai_api_key:
            log.warn("tts.fallback_to_mock", reason="OPENAI_API_KEY 누락")
            return MockTtsProvider()
        return OpenAiTtsProvider(
            api_key=settings.openai_api_key,
            base_url=settings.openai_base_url,
            model=settings.openai_tts_model,
            timeout_sec=settings.openai_tts_timeout_sec,
        )

    if provider == "mock":
        return MockTtsProvider()

    log.warn("tts.unknown_provider_fallback_mock", provider=provider)
    return MockTtsProvider()
