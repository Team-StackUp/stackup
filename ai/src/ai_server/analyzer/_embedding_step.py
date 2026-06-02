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


# 문맥 프리픽스에 넣을 요약 최대 길이 (청크 비대화 방지).
_MAX_SUMMARY_CHARS = 200


def _contextualize(chunk_text: str, heading_path: str, summary: str) -> str:
    """Contextual Retrieval: 고립된 청크가 문서 맥락을 잃지 않도록
    [문서요약 > 헤딩경로] 프리픽스를 붙인다 (Anthropic 기법의 결정적 변형 — LLM 호출 없음).
    """
    parts: list[str] = []
    s = " ".join((summary or "").split())
    if s:
        if len(s) > _MAX_SUMMARY_CHARS:
            s = s[:_MAX_SUMMARY_CHARS].rstrip() + "…"
        parts.append(s)
    if heading_path:
        parts.append(heading_path)
    if not parts:
        return chunk_text
    return f"[{' > '.join(parts)}]\n\n{chunk_text}"


async def chunk_embed_and_upsert(
    *,
    document_id: int,
    markdown: str,
    chunker: MarkdownChunker,
    embedder: EmbeddingProvider,
    core_client: CoreClient,
    summary: str = "",
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

    # 임베딩·저장 대상은 문맥이 보강된 텍스트 (검색 정합도↑ + grounding↑).
    contextualized = [_contextualize(c.text, c.heading_path, summary) for c in chunks]

    try:
        vectors = await embedder.embed(contextualized)
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
            chunk_text=contextualized[i],
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
