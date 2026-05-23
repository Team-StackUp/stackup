import pytest

from ai_server.rag.embedder import MockEmbeddingProvider, build_embedding_provider


@pytest.mark.asyncio
async def test_mock_embed_returns_fixed_dim_vectors() -> None:
    emb = MockEmbeddingProvider(dim=8)
    vectors = await emb.embed(["a", "b", "ccc"])
    assert len(vectors) == 3
    for v in vectors:
        assert len(v) == 8
        for x in v:
            assert -1.0 <= x <= 1.0


@pytest.mark.asyncio
async def test_mock_embed_is_deterministic() -> None:
    emb = MockEmbeddingProvider(dim=16)
    a = await emb.embed(["같은 입력"])
    b = await emb.embed(["같은 입력"])
    assert a == b


@pytest.mark.asyncio
async def test_mock_embed_different_input_yields_different_vector() -> None:
    emb = MockEmbeddingProvider(dim=16)
    a = await emb.embed(["A"])
    b = await emb.embed(["B"])
    assert a[0] != b[0]


def test_mock_provider_exposes_dim_and_model() -> None:
    emb = MockEmbeddingProvider(dim=1536, model="mock-test")
    assert emb.dim == 1536
    assert emb.model == "mock-test"


def test_constructor_rejects_non_positive_dim() -> None:
    with pytest.raises(ValueError):
        MockEmbeddingProvider(dim=0)
    with pytest.raises(ValueError):
        MockEmbeddingProvider(dim=-1)


def test_factory_returns_mock_for_mock_provider() -> None:
    emb = build_embedding_provider(provider="mock", dim=64, model="any")
    assert isinstance(emb, MockEmbeddingProvider)
    assert emb.dim == 64


def test_factory_raises_for_unimplemented_providers() -> None:
    with pytest.raises(NotImplementedError):
        build_embedding_provider(provider="openai", dim=1536, model="x")
    with pytest.raises(NotImplementedError):
        build_embedding_provider(provider="ollama", dim=768, model="x")


def test_factory_rejects_unknown_provider() -> None:
    with pytest.raises(ValueError):
        build_embedding_provider(provider="unknown", dim=64, model="x")


# --- Gemini provider ---


def test_factory_rejects_gemini_without_api_key() -> None:
    with pytest.raises(ValueError):
        build_embedding_provider(
            provider="gemini",
            dim=1536,
            model="gemini-embedding-001",
            gemini_api_key="",
        )


def test_factory_builds_gemini_provider_with_api_key() -> None:
    from ai_server.rag.embedder import GeminiEmbeddingProvider

    emb = build_embedding_provider(
        provider="gemini",
        dim=1536,
        model="gemini-embedding-001",
        gemini_api_key="fake-key",
    )
    assert isinstance(emb, GeminiEmbeddingProvider)
    assert emb.dim == 1536
    assert emb.model == "gemini-embedding-001"


@pytest.mark.asyncio
async def test_gemini_embed_returns_vector_list_from_sdk_response() -> None:
    from types import SimpleNamespace
    from unittest.mock import AsyncMock, MagicMock, patch

    from ai_server.rag.embedder import GeminiEmbeddingProvider

    # SDK 응답 형태 모사: resp.embeddings[i].values
    fake_resp = SimpleNamespace(
        embeddings=[
            SimpleNamespace(values=[0.1, 0.2, 0.3]),
            SimpleNamespace(values=[0.4, 0.5, 0.6]),
        ]
    )
    fake_aio = MagicMock()
    fake_aio.models.embed_content = AsyncMock(return_value=fake_resp)
    fake_client = MagicMock()
    fake_client.aio = fake_aio

    with patch("google.genai.Client", return_value=fake_client):
        emb = GeminiEmbeddingProvider(
            api_key="fake", model="gemini-embedding-001", dim=3
        )
    out = await emb.embed(["chunk A", "chunk B"])
    assert out == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]
    fake_aio.models.embed_content.assert_awaited_once()
    kwargs = fake_aio.models.embed_content.await_args.kwargs
    assert kwargs["model"] == "gemini-embedding-001"
    assert kwargs["contents"] == ["chunk A", "chunk B"]


@pytest.mark.asyncio
async def test_gemini_embed_empty_input_returns_empty_without_sdk_call() -> None:
    from unittest.mock import MagicMock, patch

    from ai_server.rag.embedder import GeminiEmbeddingProvider

    fake_client = MagicMock()
    with patch("google.genai.Client", return_value=fake_client):
        emb = GeminiEmbeddingProvider(
            api_key="fake", model="gemini-embedding-001", dim=8
        )
    assert await emb.embed([]) == []
    fake_client.aio.models.embed_content.assert_not_called()


@pytest.mark.asyncio
async def test_gemini_embed_wraps_sdk_exception_as_retriable() -> None:
    from unittest.mock import AsyncMock, MagicMock, patch

    from ai_server.rag.embedder import EmbeddingError, GeminiEmbeddingProvider

    fake_aio = MagicMock()
    fake_aio.models.embed_content = AsyncMock(side_effect=RuntimeError("rate limit"))
    fake_client = MagicMock()
    fake_client.aio = fake_aio

    with patch("google.genai.Client", return_value=fake_client):
        emb = GeminiEmbeddingProvider(
            api_key="fake", model="gemini-embedding-001", dim=4
        )

    with pytest.raises(EmbeddingError) as exc_info:
        await emb.embed(["x"])
    assert exc_info.value.code == "GEMINI_FAILED"
    assert exc_info.value.retriable is True
