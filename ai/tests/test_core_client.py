from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from ai_server.core.client import (
    CoreEmbeddingUpsertError,
    CoreTokenError,
    EmbeddingChunkPayload,
    EmbeddingSearchHit,
    HttpCoreClient,
)


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


# ----------- upsert_embeddings -----------


def _make_put_client(
    *,
    status: int = 200,
    json_body: dict | None = None,
    raise_exc: Exception | None = None,
) -> MagicMock:
    client = MagicMock()
    resp = MagicMock(spec=httpx.Response)
    resp.status_code = status
    resp.text = ""
    resp.json = (
        MagicMock(return_value=json_body)
        if json_body is not None
        else MagicMock(side_effect=ValueError("no json"))
    )
    if raise_exc is not None:
        client.put = AsyncMock(side_effect=raise_exc)
    else:
        client.put = AsyncMock(return_value=resp)
    return client


def _payloads(n: int = 2, dim: int = 4) -> list[EmbeddingChunkPayload]:
    return [
        EmbeddingChunkPayload(
            chunk_index=i, chunk_text=f"chunk {i}", embedding=[0.1] * dim
        )
        for i in range(n)
    ]


@pytest.mark.asyncio
async def test_upsert_embeddings_happy_path_returns_count() -> None:
    client = _make_put_client(status=200, json_body={"upserted": 2})
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)
    n = await core.upsert_embeddings(
        document_id=88,
        model="mock",
        dim=4,
        chunks=_payloads(2, 4),
    )
    assert n == 2
    client.put.assert_awaited_once()
    args, kwargs = client.put.call_args
    assert args[0] == "/api/internal/documents/88/embeddings"
    body = kwargs["json"]
    assert body["dim"] == 4
    assert body["model"] == "mock"
    assert len(body["chunks"]) == 2
    assert body["chunks"][0] == {
        "chunkIndex": 0,
        "chunkText": "chunk 0",
        "embedding": [0.1, 0.1, 0.1, 0.1],
    }


@pytest.mark.asyncio
async def test_upsert_embeddings_falls_back_to_len_when_no_upserted_field() -> None:
    client = _make_put_client(status=200, json_body={})
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)
    n = await core.upsert_embeddings(
        document_id=1, model="m", dim=4, chunks=_payloads(3, 4)
    )
    assert n == 3  # 응답에 카운트 없으면 보낸 만큼 적용 가정


@pytest.mark.asyncio
async def test_upsert_embeddings_404_translates_to_document_not_found() -> None:
    client = _make_put_client(status=404)
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)
    with pytest.raises(CoreEmbeddingUpsertError) as exc_info:
        await core.upsert_embeddings(
            document_id=99, model="m", dim=4, chunks=_payloads()
        )
    assert exc_info.value.code == "DOCUMENT_NOT_FOUND"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_upsert_embeddings_5xx_retriable() -> None:
    client = _make_put_client(status=503)
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)
    with pytest.raises(CoreEmbeddingUpsertError) as exc_info:
        await core.upsert_embeddings(
            document_id=1, model="m", dim=4, chunks=_payloads()
        )
    assert exc_info.value.code == "CORE_UNAVAILABLE"
    assert exc_info.value.retriable is True


@pytest.mark.asyncio
async def test_upsert_embeddings_401_non_retriable_auth() -> None:
    client = _make_put_client(status=401)
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)
    with pytest.raises(CoreEmbeddingUpsertError) as exc_info:
        await core.upsert_embeddings(
            document_id=1, model="m", dim=4, chunks=_payloads()
        )
    assert exc_info.value.code == "CORE_AUTH_FAILED"


@pytest.mark.asyncio
async def test_upsert_embeddings_httpx_error_retriable() -> None:
    client = _make_put_client(raise_exc=httpx.ConnectError("dns fail"))
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)
    with pytest.raises(CoreEmbeddingUpsertError) as exc_info:
        await core.upsert_embeddings(
            document_id=1, model="m", dim=4, chunks=_payloads()
        )
    assert exc_info.value.code == "CORE_UNAVAILABLE"
    assert exc_info.value.retriable is True


# ----------- search_embeddings -----------


def _make_post_client(
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
    resp.json = (
        MagicMock(return_value=json_body)
        if json_body is not None
        else MagicMock(side_effect=ValueError("no json"))
    )
    if raise_exc is not None:
        client.post = AsyncMock(side_effect=raise_exc)
    else:
        client.post = AsyncMock(return_value=resp)
    return client


@pytest.mark.asyncio
async def test_search_embeddings_uses_latest_core_contract() -> None:
    client = _make_post_client(
        json_body={
            "hits": [
                {
                    "documentId": 7,
                    "chunkIndex": 2,
                    "chunkText": "Spring transaction boundaries",
                    "distance": 0.17,
                }
            ]
        }
    )
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)

    hits = await core.search_embeddings(
        user_id=7,
        query_embedding=[0.1, 0.2],
        document_ids=[7, 8],
        top_k=3,
    )

    assert hits == [
        EmbeddingSearchHit(
            document_id=7,
            chunk_index=2,
            chunk_text="Spring transaction boundaries",
            distance=0.17,
        )
    ]
    client.post.assert_awaited_once_with(
        "/api/internal/embeddings/search",
        json={
            # userId 는 Core 가 검색 범위를 소유 문서로 제한하는 데 쓴다 — 빠지면 400.
            "userId": 7,
            "queryEmbedding": [0.1, 0.2],
            "documentIds": [7, 8],
            "topK": 3,
        },
    )


@pytest.mark.asyncio
async def test_search_embeddings_includes_query_text_for_hybrid() -> None:
    client = _make_post_client(json_body={"hits": []})
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)

    await core.search_embeddings(
        user_id=7,
        query_embedding=[0.1],
        query_text="gRPC 동시성 처리",
        document_ids=[7],
        top_k=20,
    )

    body = client.post.await_args.kwargs["json"]
    assert body["queryText"] == "gRPC 동시성 처리"
    assert body["topK"] == 20


@pytest.mark.parametrize("status", [400, 401, 403, 404, 500])
@pytest.mark.asyncio
async def test_search_embeddings_non_2xx_returns_empty(status: int) -> None:
    client = _make_post_client(status=status, text="bad")
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)

    assert await core.search_embeddings(user_id=7, query_embedding=[0.1]) == []


@pytest.mark.asyncio
async def test_search_embeddings_http_error_returns_empty() -> None:
    client = _make_post_client(raise_exc=httpx.ConnectError("dns fail"))
    core = HttpCoreClient(base_url="http://core:38010", api_key="k", client=client)

    assert await core.search_embeddings(user_id=7, query_embedding=[0.1]) == []
