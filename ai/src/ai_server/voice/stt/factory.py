from __future__ import annotations

import structlog

from ai_server.config.settings import Settings
from ai_server.voice.stt.base import SttProvider
from ai_server.voice.stt.mock import MockSttProvider
from ai_server.voice.stt.openai_whisper import OpenAiWhisperSttProvider

log = structlog.get_logger(__name__)


def build_stt_provider(settings: Settings) -> SttProvider:
    provider = (settings.stt_provider or "mock").lower()
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
