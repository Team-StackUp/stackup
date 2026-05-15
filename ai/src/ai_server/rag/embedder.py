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

    async def embed(self, texts: list[str]) -> list[list[float]]: ...


# 결정 지연용 mock. 입력 텍스트의 SHA-256을 dim 길이로 펼쳐서 [-1, 1] vector 생성.
# 같은 입력은 항상 같은 vector → roundtrip·중복 테스트에 안전.
# 실제 임베딩 모델로 교체될 때까지 pgvector 컬럼 차원만 맞으면 e2e가 돈다.
# 
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

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._embed_one(t) for t in texts]

    def _embed_one(self, text: str) -> list[float]:
        # [-1, 1] 범위로 매핑 진행
        digest = hashlib.sha256(text.encode("utf-8")).digest()
        repeats = (self._dim * 4 + len(digest) - 1) // len(digest)
        blob = (digest * repeats)[: self._dim * 4]
        ints = struct.unpack(f">{self._dim}I", blob)
        scale = 2.0 / 0xFFFFFFFF
        return [v * scale - 1.0 for v in ints]


# 우선 mock 만 지원함. 다른 구현체는 추후 고려
def build_embedding_provider(
    *,
    provider: str,
    dim: int,
    model: str,
) -> EmbeddingProvider:
    if provider == "mock":
        return MockEmbeddingProvider(dim=dim, model=model)
    if provider == "openai":
        raise NotImplementedError("openai embedding provider 미구현 — 후속 PR에서 추가")
    if provider == "ollama":
        raise NotImplementedError("ollama embedding provider 미구현 — 후속 PR에서 추가")
    raise ValueError(f"Unsupported EMBEDDING_PROVIDER={provider!r}")
