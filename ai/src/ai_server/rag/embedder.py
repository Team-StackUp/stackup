from __future__ import annotations

import hashlib
import struct
from typing import Protocol


class EmbeddingError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


# 구현체는 바꿔서 사용할 수 있음
class EmbeddingProvider(Protocol):
    @property
    def dim(self) -> int: ...

    @property
    def model(self) -> str: ...

    async def embed(
        self, texts: list[str], *, task_type: str = "RETRIEVAL_DOCUMENT"
    ) -> list[list[float]]: ...


# 우선 mock 구현체
class MockEmbeddingProvider:
    def __init__(self, *, dim: int = 1536, model: str = "mock") -> None:
        if dim <= 0:
            raise ValueError(f"dim must be > 0, got {dim}")
        self._dim = dim
        self._model = model

    @property
    def dim(self) -> int:
        return self._dim

    @property
    def model(self) -> str:
        return self._model

    async def embed(
        self, texts: list[str], *, task_type: str = "RETRIEVAL_DOCUMENT"
    ) -> list[list[float]]:
        # mock 은 task_type 을 무시한다 (시그니처 호환만 유지).
        return [self._embed_one(t) for t in texts]

    def _embed_one(self, text: str) -> list[float]:
        # [-1, 1] 범위로 매핑 진행
        digest = hashlib.sha256(text.encode("utf-8")).digest()
        repeats = (self._dim * 4 + len(digest) - 1) // len(digest)
        blob = (digest * repeats)[: self._dim * 4]
        ints = struct.unpack(f">{self._dim}I", blob)
        scale = 2.0 / 0xFFFFFFFF
        return [v * scale - 1.0 for v in ints]


# Gemini Embedding 을 사용합니다.
# 이건 충대키로 안되니 키 발급 필요함
class GeminiEmbeddingProvider:
    def __init__(self, *, api_key: str, model: str, dim: int) -> None:
        if not api_key:
            raise ValueError("GEMINI_API_KEY 누락 — provider=gemini 사용 불가")
        if dim <= 0:
            raise ValueError(f"dim must be > 0, got {dim}")
        from google import genai

        self._client = genai.Client(api_key=api_key)
        self._model = model
        self._dim = dim

    @property
    def dim(self) -> int:
        return self._dim

    @property
    def model(self) -> str:
        return self._model

    async def embed(
        self, texts: list[str], *, task_type: str = "RETRIEVAL_DOCUMENT"
    ) -> list[list[float]]:
        if not texts:
            return []
        from google.genai import types as genai_types

        try:
            resp = await self._client.aio.models.embed_content(
                model=self._model,
                contents=texts,
                config=genai_types.EmbedContentConfig(
                    # 인덱싱은 RETRIEVAL_DOCUMENT, 검색 쿼리는 RETRIEVAL_QUERY 로
                    # 분리해야 Gemini embedding 의 코사인 정합도가 최적화된다.
                    task_type=task_type,
                    output_dimensionality=self._dim,
                ),
            )
        except Exception as exc:
            raise EmbeddingError(
                code="GEMINI_FAILED",
                message=f"Gemini embedding 호출 실패: {exc}",
                retriable=True,
            ) from exc

        return [list(e.values) for e in resp.embeddings]


def build_embedding_provider(
    *,
    provider: str,
    dim: int,
    model: str,
    gemini_api_key: str = "",
) -> EmbeddingProvider:
    if provider == "mock":
        return MockEmbeddingProvider(dim=dim, model=model)
    if provider == "gemini":
        return GeminiEmbeddingProvider(api_key=gemini_api_key, model=model, dim=dim)
    if provider == "openai":
        raise NotImplementedError("openai embedding provider 미구현 — 후속 PR에서 추가")
    if provider == "ollama":
        raise NotImplementedError("ollama embedding provider 미구현 — 후속 PR에서 추가")
    raise ValueError(f"Unsupported EMBEDDING_PROVIDER={provider!r}")
