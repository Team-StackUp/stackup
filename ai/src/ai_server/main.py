from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

import structlog
from fastapi import FastAPI

from ai_server.api.health import router as health_router
from ai_server.api.voice_stream import router as voice_stream_router
from ai_server.config.settings import Settings, get_settings
from ai_server.messaging.runner import MessagingRuntime
from ai_server.voice.stt.live_factory import build_live_stt_provider

log = structlog.get_logger(__name__)


def _build_lifespan(settings: Settings):
    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
        runtime = MessagingRuntime(settings)
        app.state.messaging = runtime
        try:
            await runtime.start()
            app.state.settings = settings
            app.state.live_stt_provider = build_live_stt_provider(settings)
            app.state.callback_publisher = runtime.publisher
            yield
        finally:
            await runtime.stop()

    return lifespan


def create_app(settings: Settings | None = None) -> FastAPI:
    if settings is None:
        settings = get_settings()

    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        lifespan=_build_lifespan(settings),
    )

    app.include_router(health_router)
    app.include_router(voice_stream_router)

    return app


app = create_app()
