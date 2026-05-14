from __future__ import annotations

from dataclasses import dataclass

import structlog

from ai_server.analyzer.sources.base import SourceExtractor
from ai_server.chain.document_analysis_chain import DocumentAnalyzer
from ai_server.storage.base import ObjectStorage

log = structlog.get_logger(__name__)


class ResumeAnalyzeError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


@dataclass(frozen=True)
class ResumeAnalysisResult:
    summary: str
    tech_stack: list[str]
    document_path: str


# 스토리지에서 가져오고 LLM을 통해 분석함
class ResumeAnalyzer:
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
        file_path: str,
    ) -> ResumeAnalysisResult:
        log.info(
            "resume.extract.start",
            resume_id=resume_id,
            file_path=file_path,
        )
        source = await self._extractor.extract(file_path)

        if not source.text.strip():
            raise ResumeAnalyzeError(
                code="EMPTY_PDF_TEXT",
                message="추출된 텍스트가 비어 있음 — 이미지 전용 PDF이거나 파일 손상 가능",
                retriable=False,
            )

        log.info(
            "resume.llm.start",
            resume_id=resume_id,
            text_chars=len(source.text),
            source_type=source.source_type,
        )
        analysis = await self._chain.analyze(
            text=source.text,
            source_type=source.source_type,
        )

        out_key = self._analyzed_key_template.format(resume_id=resume_id)
        await self._storage.put_text(out_key, analysis.markdown)
        log.info(
            "resume.markdown.saved",
            resume_id=resume_id,
            key=out_key,
            md_chars=len(analysis.markdown),
        )

        return ResumeAnalysisResult(
            summary=analysis.summary,
            tech_stack=list(analysis.tech_stack),
            document_path=out_key,
        )
