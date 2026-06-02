from __future__ import annotations

from dataclasses import dataclass
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


class CoreEmbeddingUpsertError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


@dataclass(frozen=True)
class EmbeddingChunkPayload:
    chunk_index: int
    chunk_text: str
    embedding: list[float]


@dataclass(frozen=True)
class EmbeddingSearchHit:
    document_id: int
    chunk_index: int
    chunk_text: str
    distance: float


# 코어 서버 API 호출용
class CoreClient(Protocol):
    async def fetch_github_token(self, user_id: int) -> str: ...

    async def upsert_embeddings(
        self,
        *,
        document_id: int,
        model: str,
        dim: int,
        chunks: list[EmbeddingChunkPayload],
    ) -> int: ...

    async def search_embeddings(
        self,
        *,
        query_embedding: list[float],
        query_text: str | None = None,
        document_ids: list[int] | None = None,
        top_k: int = 5,
    ) -> list[EmbeddingSearchHit]: ...

    async def record_ai_log(
        self,
        *,
        request_type: str,
        model_name: str | None,
        input_tokens: int | None,
        output_tokens: int | None,
        latency_ms: int | None,
        status: str,
        user_id: int | None = None,
        session_id: int | None = None,
        error_message: str | None = None,
    ) -> None: ...


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

    async def upsert_embeddings(
        self,
        *,
        document_id: int,
        model: str,
        dim: int,
        chunks: list[EmbeddingChunkPayload],
    ) -> int:
        """`analyzed_documents.id` 한 건에 대한 chunk + embedding을 Core가
        pgvector(document_embeddings)에 idempotent upsert.

        body:
          { "model": "...", "dim": 1536,
            "chunks": [{ "chunkIndex": 0, "chunkText": "...", "embedding": [...] }, ...] }

        반환: upsert된 chunk 수.
        """
        body = {
            "model": model,
            "dim": dim,
            "chunks": [
                {
                    "chunkIndex": c.chunk_index,
                    "chunkText": c.chunk_text,
                    "embedding": c.embedding,
                }
                for c in chunks
            ],
        }
        path = f"/api/internal/documents/{document_id}/embeddings"
        if self._client is not None:
            return await self._do_upsert(self._client, path, body)
        async with self._build_client() as client:
            return await self._do_upsert(client, path, body)

    async def _do_upsert(
        self,
        client: httpx.AsyncClient,
        path: str,
        body: dict,
    ) -> int:
        try:
            resp = await client.put(path, json=body)
        except httpx.HTTPError as exc:
            raise CoreEmbeddingUpsertError(
                code="CORE_UNAVAILABLE",
                message=f"Core API 호출 실패: {exc}",
                retriable=True,
            ) from exc

        status = resp.status_code
        if status == 404:
            raise CoreEmbeddingUpsertError(
                code="DOCUMENT_NOT_FOUND",
                message=f"Core에 document 없음: {path}",
                retriable=False,
            )
        if status in (401, 403):
            raise CoreEmbeddingUpsertError(
                code="CORE_AUTH_FAILED",
                message=f"Core internal 인증 실패: {status}",
                retriable=False,
            )
        if status >= 500:
            raise CoreEmbeddingUpsertError(
                code="CORE_UNAVAILABLE",
                message=f"Core 5xx: {status}",
                retriable=True,
            )
        if status >= 400:
            raise CoreEmbeddingUpsertError(
                code="CORE_BAD_REQUEST",
                message=f"Core {status}: {resp.text[:200]}",
                retriable=False,
            )

        try:
            data = resp.json()
        except ValueError as exc:
            raise CoreEmbeddingUpsertError(
                code="CORE_BAD_RESPONSE",
                message=f"Core 응답 JSON 파싱 실패: {exc}",
                retriable=True,
            ) from exc

        count = data.get("upserted") if isinstance(data, dict) else None
        if not isinstance(count, int):
            return len(body["chunks"])
        return count

    async def search_embeddings(
        self,
        *,
        query_embedding: list[float],
        query_text: str | None = None,
        document_ids: list[int] | None = None,
        top_k: int = 5,
    ) -> list[EmbeddingSearchHit]:
        """임베딩 검색. query_text 가 주어지면 Core 가 벡터+BM25 RRF 하이브리드로,
        없으면 pgvector cosine 단독으로 topK 반환. 실패 시 빈 리스트 (RAG 보강용이므로 fatal 아님)."""
        body: dict = {
            "queryEmbedding": query_embedding,
            "documentIds": list(document_ids or []),
            "topK": top_k,
        }
        if query_text:
            body["queryText"] = query_text
        path = "/api/internal/embeddings/search"
        try:
            if self._client is not None:
                resp = await self._client.post(path, json=body)
            else:
                async with self._build_client() as client:
                    resp = await client.post(path, json=body)
        except httpx.HTTPError as exc:
            log.warn("core.embedding.search.failed", error=str(exc))
            return []

        if resp.status_code >= 400:
            log.warn(
                "core.embedding.search.non_2xx",
                status=resp.status_code,
                body=resp.text[:200],
            )
            return []
        try:
            data = resp.json()
        except ValueError:
            return []
        hits = data.get("hits") if isinstance(data, dict) else None
        if not isinstance(hits, list):
            return []
        return [
            EmbeddingSearchHit(
                document_id=int(h.get("documentId", 0)),
                chunk_index=int(h.get("chunkIndex", 0)),
                chunk_text=str(h.get("chunkText", "")),
                distance=float(h.get("distance", 0.0)),
            )
            for h in hits
        ]

    async def record_ai_log(
        self,
        *,
        request_type: str,
        model_name: str | None,
        input_tokens: int | None,
        output_tokens: int | None,
        latency_ms: int | None,
        status: str,
        user_id: int | None = None,
        session_id: int | None = None,
        error_message: str | None = None,
    ) -> None:
        """`ai_request_logs` 에 fire-and-forget INSERT. 실패해도 raise 하지 않음 — 관측용."""
        path = "/api/internal/ai-logs"
        body = {
            "userId": user_id,
            "sessionId": session_id,
            "requestType": request_type,
            "modelName": model_name,
            "inputTokens": input_tokens,
            "outputTokens": output_tokens,
            "latencyMs": latency_ms,
            "status": status,
            "errorMessage": error_message,
        }
        try:
            if self._client is not None:
                await self._client.post(path, json=body)
                return
            async with self._build_client() as client:
                await client.post(path, json=body)
        except Exception as exc:
            log.warn("core.ai_log.failed", error=str(exc), request_type=request_type)
