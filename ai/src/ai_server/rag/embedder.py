from __future__ import annotations

import asyncio
import hashlib
import random
import struct
from typing import Protocol

import structlog

log = structlog.get_logger(__name__)


class EmbeddingError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


# Gemini 가 한도 초과(429)일 때만 백오프 재시도한다. 다른 오류(인증·잘못된 입력 등)는
# 재시도해도 동일하므로 즉시 실패시킨다. SDK ClientError 는 .code(HTTP)·.status 를 노출한다.
def _is_rate_limited(exc: Exception) -> bool:
    if getattr(exc, "code", None) == 429:
        return True
    if getattr(exc, "status", None) == "RESOURCE_EXHAUSTED":
        return True
    return "RESOURCE_EXHAUSTED" in str(exc)


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
    # 한 요청에 너무 많은 청크를 담으면 분당 토큰 한도(TPM)에 걸려 429 가 난다.
    # batch_size 로 쪼개 순차 호출하고, 429 는 지수 백오프로 재시도한다.
    _MAX_BACKOFF_SEC = 30.0

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        dim: int,
        batch_size: int = 32,
        max_retries: int = 5,
        retry_base_delay_sec: float = 2.0,
    ) -> None:
        if not api_key:
            raise ValueError("GEMINI_API_KEY 누락 — provider=gemini 사용 불가")
        if dim <= 0:
            raise ValueError(f"dim must be > 0, got {dim}")
        from google import genai

        self._client = genai.Client(api_key=api_key)
        self._model = model
        self._dim = dim
        self._batch_size = max(1, batch_size)
        self._max_retries = max(0, max_retries)
        self._retry_base_delay = max(0.0, retry_base_delay_sec)

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

        config = genai_types.EmbedContentConfig(
            # 인덱싱은 RETRIEVAL_DOCUMENT, 검색 쿼리는 RETRIEVAL_QUERY 로
            # 분리해야 Gemini embedding 의 코사인 정합도가 최적화된다.
            task_type=task_type,
            output_dimensionality=self._dim,
        )

        vectors: list[list[float]] = []
        for start in range(0, len(texts), self._batch_size):
            batch = texts[start : start + self._batch_size]
            resp = await self._embed_batch_with_retry(batch, config)
            vectors.extend(list(e.values) for e in resp.embeddings)
        return vectors

    async def _embed_batch_with_retry(self, batch: list[str], config: object) -> object:
        attempt = 0
        while True:
            try:
                return await self._client.aio.models.embed_content(
                    model=self._model,
                    contents=batch,
                    config=config,
                )
            except Exception as exc:
                rate_limited = _is_rate_limited(exc)
                if rate_limited and attempt < self._max_retries:
                    delay = min(
                        self._retry_base_delay * (2**attempt), self._MAX_BACKOFF_SEC
                    )
                    delay += random.uniform(0.0, self._retry_base_delay * 0.1)
                    log.warning(
                        "embed.gemini.rate_limited",
                        attempt=attempt + 1,
                        max_retries=self._max_retries,
                        delay_sec=round(delay, 2),
                    )
                    await asyncio.sleep(delay)
                    attempt += 1
                    continue
                raise EmbeddingError(
                    code="GEMINI_RATE_LIMITED" if rate_limited else "GEMINI_FAILED",
                    message=f"Gemini embedding 호출 실패: {exc}",
                    retriable=True,
                ) from exc


def build_embedding_provider(
    *,
    provider: str,
    dim: int,
    model: str,
    gemini_api_key: str = "",
    batch_size: int = 32,
    max_retries: int = 5,
    retry_base_delay_sec: float = 2.0,
) -> EmbeddingProvider:
    if provider == "mock":
        return MockEmbeddingProvider(dim=dim, model=model)
    if provider == "gemini":
        return GeminiEmbeddingProvider(
            api_key=gemini_api_key,
            model=model,
            dim=dim,
            batch_size=batch_size,
            max_retries=max_retries,
            retry_base_delay_sec=retry_base_delay_sec,
        )
    if provider == "openai":
        raise NotImplementedError("openai embedding provider 미구현 — 후속 PR에서 추가")
    if provider == "ollama":
        raise NotImplementedError("ollama embedding provider 미구현 — 후속 PR에서 추가")
    raise ValueError(f"Unsupported EMBEDDING_PROVIDER={provider!r}")
