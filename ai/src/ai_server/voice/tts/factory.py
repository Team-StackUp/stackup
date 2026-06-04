from __future__ import annotations

import structlog

from ai_server.config.settings import Settings
from ai_server.voice.tts.base import TtsProvider
from ai_server.voice.tts.gateway import GatewayTtsProvider
from ai_server.voice.tts.gemini import GeminiTtsProvider
from ai_server.voice.tts.mock import MockTtsProvider
from ai_server.voice.tts.openai_tts import OpenAiTtsProvider

log = structlog.get_logger(__name__)


def build_tts_provider(settings: Settings) -> TtsProvider:
    """TTS 공급자 선택. auto → gateway(충남대 키) > gemini(직접 키) > openai, 없으면 mock.

    gateway 를 우선해 직접 GEMINI_API_KEY 의 429 부하를 던다(게이트웨이가 Gemini TTS 로 라우팅).
    """
    provider = (settings.tts_provider or "auto").lower()

    if provider == "auto":
        if settings.llm_api_key:
            provider = "gateway"
        elif settings.gemini_api_key:
            provider = "gemini"
        elif settings.openai_api_key:
            provider = "openai"
        else:
            provider = "mock"

    if provider == "gateway":
        if not settings.llm_api_key:
            log.warn("tts.fallback_to_mock", reason="LLM_API_KEY 누락")
            return MockTtsProvider()
        return GatewayTtsProvider(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            model=settings.gemini_tts_model,
            voice=settings.gemini_tts_voice,
            timeout_sec=settings.gemini_tts_timeout_sec,
        )

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
