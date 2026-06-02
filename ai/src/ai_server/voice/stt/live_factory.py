from __future__ import annotations

import structlog

from ai_server.config.settings import Settings
from ai_server.voice.stt.live import LiveSttProvider
from ai_server.voice.stt.mock_live import MockLiveSttProvider

log = structlog.get_logger(__name__)


def build_live_stt_provider(settings: Settings) -> LiveSttProvider:
    provider = (settings.live_stt_provider or "auto").lower()
    if provider == "auto":
        provider = "deepgram_live" if settings.deepgram_api_key else "mock"

    if provider == "deepgram_live":
        if not settings.deepgram_api_key:
            log.warn("live_stt.fallback_to_mock", reason="DEEPGRAM_API_KEY 누락")
            return MockLiveSttProvider()
        from ai_server.voice.stt.deepgram_live import DeepgramLiveSttProvider

        return DeepgramLiveSttProvider(
            api_key=settings.deepgram_api_key,
            url=settings.deepgram_live_url,
            model=settings.deepgram_live_model,
            language=settings.deepgram_live_language,
            endpointing_ms=settings.deepgram_live_endpointing_ms,
        )
    return MockLiveSttProvider()
