from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from ai_server.core.client import CoreTokenError, HttpCoreClient


def _make_client(
    *,
    status: int = 200,
    json_body: dict | None = None,
    text: str = "",
    raise_exc: Exception | None = None,
) -> MagicMock:
    client = MagicMock()
    resp = MagicMock(spec=httpx.Response)
    resp.status_code = status
    resp.text = text
    if json_body is None:
        resp.json = MagicMock(side_effect=ValueError("no json"))
    else:
        resp.json = MagicMock(return_value=json_body)
    if raise_exc is not None:
        client.get = AsyncMock(side_effect=raise_exc)
    else:
        client.get = AsyncMock(return_value=resp)
    return client


def _build(client: MagicMock) -> HttpCoreClient:
    return HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)


@pytest.mark.asyncio
async def test_returns_access_token_on_200_camel() -> None:
    client = _make_client(json_body={"accessToken": "tok-1"})
    core = _build(client)
    token = await core.fetch_github_token(user_id=42)
    assert token == "tok-1"
    client.get.assert_awaited_once_with("/api/internal/users/42/github-token")


@pytest.mark.asyncio
async def test_returns_access_token_on_snake_case_response() -> None:
    client = _make_client(json_body={"access_token": "tok-s"})
    core = _build(client)
    assert await core.fetch_github_token(user_id=1) == "tok-s"


@pytest.mark.asyncio
async def test_404_translates_to_user_not_found() -> None:
    client = _make_client(status=404)
    core = _build(client)
    with pytest.raises(CoreTokenError) as exc_info:
        await core.fetch_github_token(user_id=42)
    assert exc_info.value.code == "USER_NOT_FOUND"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_401_translates_to_core_auth_failed() -> None:
    client = _make_client(status=401)
    core = _build(client)
    with pytest.raises(CoreTokenError) as exc_info:
        await core.fetch_github_token(user_id=42)
    assert exc_info.value.code == "CORE_AUTH_FAILED"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_5xx_translates_to_retriable_unavailable() -> None:
    client = _make_client(status=503)
    core = _build(client)
    with pytest.raises(CoreTokenError) as exc_info:
        await core.fetch_github_token(user_id=42)
    assert exc_info.value.code == "CORE_UNAVAILABLE"
    assert exc_info.value.retriable is True


@pytest.mark.asyncio
async def test_other_4xx_non_retriable() -> None:
    client = _make_client(status=400, text="bad")
    core = _build(client)
    with pytest.raises(CoreTokenError) as exc_info:
        await core.fetch_github_token(user_id=42)
    assert exc_info.value.code == "CORE_BAD_REQUEST"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_httpx_error_translates_to_retriable() -> None:
    client = _make_client(raise_exc=httpx.ConnectError("dns fail"))
    core = _build(client)
    with pytest.raises(CoreTokenError) as exc_info:
        await core.fetch_github_token(user_id=42)
    assert exc_info.value.code == "CORE_UNAVAILABLE"
    assert exc_info.value.retriable is True


@pytest.mark.asyncio
async def test_empty_token_field_treated_as_retriable() -> None:
    client = _make_client(json_body={"accessToken": ""})
    core = _build(client)
    with pytest.raises(CoreTokenError) as exc_info:
        await core.fetch_github_token(user_id=42)
    assert exc_info.value.code == "CORE_TOKEN_EMPTY"
    assert exc_info.value.retriable is True


@pytest.mark.asyncio
async def test_invalid_json_translates_to_bad_response() -> None:
    client = _make_client(status=200, json_body=None)
    core = _build(client)
    with pytest.raises(CoreTokenError) as exc_info:
        await core.fetch_github_token(user_id=42)
    assert exc_info.value.code == "CORE_BAD_RESPONSE"


def test_constructor_requires_base_url() -> None:
    with pytest.raises(ValueError):
        HttpCoreClient(base_url="", api_key="x")
