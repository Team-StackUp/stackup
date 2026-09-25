from __future__ import annotations

import structlog

from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.voice.tts.base import TtsProvider
from ai_server.voice.tts.gateway import GatewayTtsProvider
from ai_server.voice.tts.gemini import GeminiTtsProvider
from ai_server.voice.tts.logging_provider import LoggingTtsProvider
from ai_server.voice.tts.mock import MockTtsProvider

log = structlog.get_logger(__name__)


def build_tts_provider(
    settings: Settings, core_client: CoreClient | None = None
) -> TtsProvider:
    """TTS 공급자 선택. auto → gateway(충남대 키) > gemini(직접 키), 없으면 mock.

    gateway 를 우선해 직접 GEMINI_API_KEY 의 429 부하를 던다(게이트웨이가 Gemini TTS 로 라우팅).
    core_client 가 있으면 호출 로깅 데코레이터로 감싼다(mock 은 외부 호출이 아니라 제외).
    """
    return _wrap(_select(settings), core_client)


def _wrap(provider: TtsProvider, core_client: CoreClient | None) -> TtsProvider:
    if core_client is None or isinstance(provider, MockTtsProvider):
        return provider
    return LoggingTtsProvider(provider, core_client)


def _select(settings: Settings) -> TtsProvider:
    provider = (settings.tts_provider or "auto").lower()

    if provider == "auto":
        if settings.llm_api_key:
            provider = "gateway"
        elif settings.gemini_api_key:
            provider = "gemini"
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

    if provider == "mock":
        return MockTtsProvider()

    log.warn("tts.unknown_provider_fallback_mock", provider=provider)
    return MockTtsProvider()
