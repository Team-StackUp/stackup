from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

import structlog
from fastapi import FastAPI

from ai_server.api.health import router as health_router
from ai_server.config.settings import Settings, get_settings
from ai_server.messaging.runner import MessagingRuntime

log = structlog.get_logger(__name__)


def _build_lifespan(settings: Settings):
    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
        runtime = MessagingRuntime(settings)
        app.state.messaging = runtime
        try:
            await runtime.start()
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

    return app


app = create_app()
