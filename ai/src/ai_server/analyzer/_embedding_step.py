# 공통 임베딩 모듈 
from __future__ import annotations

import structlog

from ai_server.core.client import (
    CoreClient,
    CoreEmbeddingUpsertError,
    EmbeddingChunkPayload,
)
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import EmbeddingError, EmbeddingProvider

log = structlog.get_logger(__name__)


class EmbeddingStepError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


async def chunk_embed_and_upsert(
    *,
    document_id: int,
    markdown: str,
    chunker: MarkdownChunker,
    embedder: EmbeddingProvider,
    core_client: CoreClient,
    log_prefix: str = "analyze",
) -> int:
    chunks = chunker.split(markdown)
    log.info(
        f"{log_prefix}.chunk.done",
        document_id=document_id,
        chunk_count=len(chunks),
    )

    if not chunks:
        return 0

    try:
        vectors = await embedder.embed([c.text for c in chunks])
    except EmbeddingError as err:
        raise EmbeddingStepError(
            code=err.code, message=err.message, retriable=err.retriable
        ) from err

    if len(vectors) != len(chunks):
        raise EmbeddingStepError(
            code="EMBED_COUNT_MISMATCH",
            message=(f"embedder가 chunk {len(chunks)}개 중 {len(vectors)}개만 반환"),
            retriable=True,
        )

    payloads = [
        EmbeddingChunkPayload(
            chunk_index=chunks[i].index,
            chunk_text=chunks[i].text,
            embedding=vectors[i],
        )
        for i in range(len(chunks))
    ]

    try:
        upserted = await core_client.upsert_embeddings(
            document_id=document_id,
            model=embedder.model,
            dim=embedder.dim,
            chunks=payloads,
        )
    except CoreEmbeddingUpsertError as err:
        raise EmbeddingStepError(
            code=err.code, message=err.message, retriable=err.retriable
        ) from err

    log.info(
        f"{log_prefix}.embed.upserted",
        document_id=document_id,
        chunk_count=upserted,
        model=embedder.model,
        dim=embedder.dim,
    )
    return upserted
