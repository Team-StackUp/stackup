from __future__ import annotations

from dataclasses import dataclass

import structlog

from ai_server.analyzer._embedding_step import (
    EmbeddingStepError,
    chunk_embed_and_upsert,
)
from ai_server.analyzer.sources.base import SourceExtractor
from ai_server.analyzer.sources.web import WebFetchError
from ai_server.chain.document_analysis_chain import DocumentAnalyzer
from ai_server.core.client import CoreClient
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import EmbeddingProvider
from ai_server.storage.base import ObjectStorage

log = structlog.get_logger(__name__)


class WebResumeAnalyzeError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


@dataclass(frozen=True)
class WebResumeAnalysisResult:
    summary: str
    tech_stack: list[str]
    document_path: str
    embedding_chunk_count: int


# 웹포폴 -> 라이브러리로 텍스트만 -> LLM -> 마크다운 -> 청킹·임베딩·pgvector upsert
class WebResumeAnalyzer:
    def __init__(
        self,
        *,
        extractor: SourceExtractor,
        chain: DocumentAnalyzer,
        storage: ObjectStorage,
        chunker: MarkdownChunker,
        embedder: EmbeddingProvider,
        core_client: CoreClient,
        analyzed_key_template: str,
    ) -> None:
        self._extractor = extractor
        self._chain = chain
        self._storage = storage
        self._chunker = chunker
        self._embedder = embedder
        self._core_client = core_client
        self._analyzed_key_template = analyzed_key_template

    async def analyze(
        self,
        *,
        resume_id: int,
        url: str,
        analyzed_document_id: int,
    ) -> WebResumeAnalysisResult:
        log.info("web_resume.extract.start", resume_id=resume_id, url=url)
        try:
            source = await self._extractor.extract(url)
        except WebFetchError as err:
            raise WebResumeAnalyzeError(
                code=err.code, message=err.message, retriable=err.retriable
            ) from err

        log.info(
            "web_resume.llm.start",
            resume_id=resume_id,
            text_chars=len(source.text),
        )
        analysis = await self._chain.analyze(
            text=source.text, source_type=source.source_type
        )

        out_key = self._analyzed_key_template.format(resume_id=resume_id)
        await self._storage.put_text(out_key, analysis.markdown)
        log.info(
            "web_resume.markdown.saved",
            resume_id=resume_id,
            key=out_key,
            md_chars=len(analysis.markdown),
        )

        try:
            chunk_count = await chunk_embed_and_upsert(
                document_id=analyzed_document_id,
                markdown=analysis.markdown,
                chunker=self._chunker,
                embedder=self._embedder,
                core_client=self._core_client,
                summary=analysis.summary,
                log_prefix="web_resume",
            )
        except EmbeddingStepError as err:
            raise WebResumeAnalyzeError(
                code=err.code, message=err.message, retriable=err.retriable
            ) from err

        return WebResumeAnalysisResult(
            summary=analysis.summary,
            tech_stack=list(analysis.tech_stack),
            document_path=out_key,
            embedding_chunk_count=chunk_count,
        )
