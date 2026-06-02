from __future__ import annotations

from dataclasses import dataclass

import structlog

from ai_server.analyzer._embedding_step import (
    EmbeddingStepError,
    chunk_embed_and_upsert,
)
from ai_server.analyzer._progress import ProgressEmitter
from ai_server.analyzer.sources.github_repo import (
    GitHubRepoSourceExtractor,
    RepositoryFetchError,
)
from ai_server.chain.document_analysis_chain import DocumentAnalyzer
from ai_server.core.client import CoreClient, CoreTokenError
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import EmbeddingProvider
from ai_server.storage.base import ObjectStorage

log = structlog.get_logger(__name__)


class RepositoryAnalyzeError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


@dataclass(frozen=True)
class RepositoryAnalysisResult:
    summary: str
    tech_stack: list[str]
    document_path: str
    embedding_chunk_count: int


# Core에서 사용자별 GitHub token 수령 → 레포 fetch → LLM 분석 → 마크다운 저장 → 청킹·임베딩
class RepositoryAnalyzer:
    def __init__(
        self,
        *,
        extractor: GitHubRepoSourceExtractor,
        core_client: CoreClient,
        chain: DocumentAnalyzer,
        storage: ObjectStorage,
        chunker: MarkdownChunker,
        embedder: EmbeddingProvider,
        analyzed_key_template: str,
    ) -> None:
        self._extractor = extractor
        self._core_client = core_client
        self._chain = chain
        self._storage = storage
        self._chunker = chunker
        self._embedder = embedder
        self._analyzed_key_template = analyzed_key_template

    async def analyze(
        self,
        *,
        repository_id: int,
        repo_full_name: str,
        default_branch: str = "main",
        user_id: int | None,
        analyzed_document_id: int,
        progress: ProgressEmitter | None = None,
    ) -> RepositoryAnalysisResult:
        async def emit(phase: str, message: str) -> None:
            if progress is not None:
                await progress(phase, message)

        if user_id is None:
            raise RepositoryAnalyzeError(
                code="MISSING_USER_ID",
                message="envelope.context.userId 없이는 GitHub token을 가져올 수 없음",
                retriable=False,
            )

        log.info(
            "repository.token.fetch",
            user_id=user_id,
            repository_id=repository_id,
        )
        try:
            access_token = await self._core_client.fetch_github_token(user_id)
        except CoreTokenError as err:
            raise RepositoryAnalyzeError(
                code=err.code, message=err.message, retriable=err.retriable
            ) from err

        await emit("EXTRACTING", "레포지토리 코드를 가져오는 중…")
        log.info(
            "repository.extract.start",
            repository_id=repository_id,
            repo_full_name=repo_full_name,
            default_branch=default_branch,
        )
        try:
            source = await self._extractor.extract(
                repo_full_name,
                access_token=access_token,
            )
        except RepositoryFetchError as err:
            raise RepositoryAnalyzeError(
                code=err.code, message=err.message, retriable=err.retriable
            ) from err

        if not source.text.strip():
            raise RepositoryAnalyzeError(
                code="EMPTY_REPO_CONTENT",
                message="레포에서 추출된 텍스트가 비어 있음",
                retriable=False,
            )

        await emit("SUMMARIZING", "AI가 코드를 분석·요약하는 중…")
        log.info(
            "repository.llm.start",
            repository_id=repository_id,
            text_chars=len(source.text),
        )
        analysis = await self._chain.analyze(
            text=source.text,
            source_type=source.source_type,
        )

        out_key = self._analyzed_key_template.format(repository_id=repository_id)
        await self._storage.put_text(out_key, analysis.markdown)
        log.info(
            "repository.markdown.saved",
            repository_id=repository_id,
            key=out_key,
            md_chars=len(analysis.markdown),
        )

        await emit("EMBEDDING", "분석 결과를 임베딩하는 중…")
        try:
            chunk_count = await chunk_embed_and_upsert(
                document_id=analyzed_document_id,
                markdown=analysis.markdown,
                chunker=self._chunker,
                embedder=self._embedder,
                core_client=self._core_client,
                summary=analysis.summary,
                log_prefix="repository",
            )
        except EmbeddingStepError as err:
            raise RepositoryAnalyzeError(
                code=err.code, message=err.message, retriable=err.retriable
            ) from err

        return RepositoryAnalysisResult(
            summary=analysis.summary,
            tech_stack=list(analysis.tech_stack),
            document_path=out_key,
            embedding_chunk_count=chunk_count,
        )
