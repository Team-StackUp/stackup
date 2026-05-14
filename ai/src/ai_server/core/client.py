from __future__ import annotations

from typing import Protocol

import httpx
import structlog

log = structlog.get_logger(__name__)


class CoreTokenError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


# 코어 서버 API 호출용 
class CoreClient(Protocol):
    async def fetch_github_token(self, user_id: int) -> str: ...


class HttpCoreClient:
    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        timeout_sec: float = 10.0,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        if not base_url:
            raise ValueError("Core internal base_url 누락")
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._timeout_sec = timeout_sec
        self._client = client  

    async def fetch_github_token(self, user_id: int) -> str:
        path = f"/api/internal/users/{user_id}/github-token"
        if self._client is not None:
            return await self._do_fetch(self._client, path)
        async with self._build_client() as client:
            return await self._do_fetch(client, path)

    def _build_client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self._base_url,
            headers={"X-Internal-API-Key": self._api_key},
            timeout=self._timeout_sec,
        )

    async def _do_fetch(self, client: httpx.AsyncClient, path: str) -> str:
        try:
            resp = await client.get(path)
        except httpx.HTTPError as exc:
            raise CoreTokenError(
                code="CORE_UNAVAILABLE",
                message=f"Core API 호출 실패: {exc}",
                retriable=True,
            ) from exc

        status = resp.status_code
        if status == 404:
            raise CoreTokenError(
                code="USER_NOT_FOUND",
                message=f"Core에 사용자 또는 token 없음: {path}",
                retriable=False,
            )
        if status in (401, 403):
            raise CoreTokenError(
                code="CORE_AUTH_FAILED",
                message=f"Core internal 인증 실패: {status}",
                retriable=False,
            )
        if status >= 500:
            raise CoreTokenError(
                code="CORE_UNAVAILABLE",
                message=f"Core 5xx: {status}",
                retriable=True,
            )
        if status >= 400:
            raise CoreTokenError(
                code="CORE_BAD_REQUEST",
                message=f"Core {status}: {resp.text[:200]}",
                retriable=False,
            )

        try:
            data = resp.json()
        except ValueError as exc:
            raise CoreTokenError(
                code="CORE_BAD_RESPONSE",
                message=f"Core 응답 JSON 파싱 실패: {exc}",
                retriable=True,
            ) from exc

        token = data.get("accessToken") or data.get("access_token")
        if not token or not isinstance(token, str):
            raise CoreTokenError(
                code="CORE_TOKEN_EMPTY",
                message="Core 응답에 accessToken 없음",
                retriable=True,
            )
        return token
