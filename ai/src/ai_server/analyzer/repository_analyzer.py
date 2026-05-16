from __future__ import annotations

from dataclasses import dataclass

import structlog

from ai_server.analyzer.sources.github_repo import (
    GitHubRepoSourceExtractor,
    RepositoryFetchError,
)
from ai_server.chain.document_analysis_chain import DocumentAnalyzer
from ai_server.core.client import CoreClient, CoreTokenError
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


# 코어서버에서 토큰 받아오고 레포 가져온다
# 이후 LLM 분석하고 마크다운 저장 
class RepositoryAnalyzer:
    def __init__(
        self,
        *,
        extractor: GitHubRepoSourceExtractor,
        core_client: CoreClient,
        chain: DocumentAnalyzer,
        storage: ObjectStorage,
        analyzed_key_template: str,
    ) -> None:
        self._extractor = extractor
        self._core_client = core_client
        self._chain = chain
        self._storage = storage
        self._analyzed_key_template = analyzed_key_template

    async def analyze(
        self,
        *,
        repository_id: int,
        repo_full_name: str,
        default_branch: str = "main",
        user_id: int | None,
    ) -> RepositoryAnalysisResult:
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
                code=err.code,
                message=err.message,
                retriable=err.retriable,
            ) from err

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
                code=err.code,
                message=err.message,
                retriable=err.retriable,
            ) from err

        if not source.text.strip():
            raise RepositoryAnalyzeError(
                code="EMPTY_REPO_CONTENT",
                message="레포에서 추출된 텍스트가 비어 있음",
                retriable=False,
            )

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

        return RepositoryAnalysisResult(
            summary=analysis.summary,
            tech_stack=list(analysis.tech_stack),
            document_path=out_key,
        )
