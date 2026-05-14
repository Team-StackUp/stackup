from __future__ import annotations

from dataclasses import dataclass

import structlog

from ai_server.analyzer.sources.base import SourceExtractor
from ai_server.analyzer.sources.web import WebFetchError
from ai_server.chain.document_analysis_chain import DocumentAnalyzer
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


# 웹포폴 -> 라이브러리로 텍스트만 - > LLM -> 마크다운 
class WebResumeAnalyzer:
    def __init__(
        self,
        *,
        extractor: SourceExtractor,
        chain: DocumentAnalyzer,
        storage: ObjectStorage,
        analyzed_key_template: str,
    ) -> None:
        self._extractor = extractor
        self._chain = chain
        self._storage = storage
        self._analyzed_key_template = analyzed_key_template

    async def analyze(
        self,
        *,
        resume_id: int,
        url: str,
    ) -> WebResumeAnalysisResult:
        log.info("web_resume.extract.start", resume_id=resume_id, url=url)
        try:
            source = await self._extractor.extract(url)
        except WebFetchError as err:
            raise WebResumeAnalyzeError(
                code=err.code,
                message=err.message,
                retriable=err.retriable,
            ) from err

        log.info(
            "web_resume.llm.start",
            resume_id=resume_id,
            text_chars=len(source.text),
        )
        analysis = await self._chain.analyze(
            text=source.text,
            source_type=source.source_type,
        )

        out_key = self._analyzed_key_template.format(resume_id=resume_id)
        await self._storage.put_text(out_key, analysis.markdown)
        log.info(
            "web_resume.markdown.saved",
            resume_id=resume_id,
            key=out_key,
            md_chars=len(analysis.markdown),
        )

        return WebResumeAnalysisResult(
            summary=analysis.summary,
            tech_stack=list(analysis.tech_stack),
            document_path=out_key,
        )
