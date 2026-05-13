from unittest.mock import AsyncMock, patch

import pytest
from fastapi import FastAPI

from ai_server.config.settings import Settings
from ai_server.main import _build_lifespan


@pytest.mark.asyncio
async def test_lifespan_calls_stop_when_start_fails() -> None:
    """start() 가 도중에 raise 해도 stop() 이 호출되어 좀비 connection 을 막아야 한다."""
    runtime_mock = AsyncMock()
    runtime_mock.start.side_effect = RuntimeError("queue declare failed")

    with patch("ai_server.main.MessagingRuntime", return_value=runtime_mock):
        lifespan = _build_lifespan(Settings())
        app = FastAPI()
        with pytest.raises(RuntimeError, match="queue declare failed"):
            async with lifespan(app):
                pass

    runtime_mock.start.assert_awaited_once()
    runtime_mock.stop.assert_awaited_once()


@pytest.mark.asyncio
async def test_lifespan_calls_stop_on_normal_shutdown() -> None:
    runtime_mock = AsyncMock()

    with patch("ai_server.main.MessagingRuntime", return_value=runtime_mock):
        lifespan = _build_lifespan(Settings())
        app = FastAPI()
        async with lifespan(app):
            assert app.state.messaging is runtime_mock

    runtime_mock.start.assert_awaited_once()
    runtime_mock.stop.assert_awaited_once()
