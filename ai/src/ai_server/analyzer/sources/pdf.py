from __future__ import annotations

import asyncio
import io
from typing import Protocol

import structlog
from pypdf import PdfReader

from ai_server.analyzer.sources.base import ExtractedSource, SourceExtractor
from ai_server.storage.base import ObjectStorage

log = structlog.get_logger(__name__)


def _extract_pdf_text(data: bytes) -> str:
    reader = PdfReader(io.BytesIO(data))
    parts: list[str] = []
    for page in reader.pages:
        parts.append(page.extract_text() or "")
    return "\n\n".join(p for p in parts if p).strip()


# 이미지/스캔 PDF 의 텍스트를 비전 모델로 추출하는 어댑터 (구현체는 chain/pdf_vision).
class VisionPdfReader(Protocol):
    async def extract_text(self, pdf_bytes: bytes) -> str: ...


# PDF 를 읽어 페이지 텍스트를 이어붙인다.
# 텍스트 레이어가 비거나 너무 짧으면(스캔/이미지 PDF) vision_reader 로 폴백.
class PdfSourceExtractor(SourceExtractor):

    def __init__(
        self,
        storage: ObjectStorage,
        *,
        vision_reader: VisionPdfReader | None = None,
        min_text_chars: int = 50,
    ) -> None:
        self._storage = storage
        self._vision_reader = vision_reader
        self._min_text_chars = min_text_chars

    async def extract(self, locator: str) -> ExtractedSource:
        data = await self._storage.get_bytes(locator)
        text = await asyncio.to_thread(_extract_pdf_text, data)
        used_vision = False

        # 텍스트가 빈약하면(스캔/이미지 PDF) 비전 모델로 추출 시도.
        if len(text.strip()) < self._min_text_chars and self._vision_reader is not None:
            try:
                vision_text = await self._vision_reader.extract_text(data)
            except Exception as exc:  # noqa: BLE001
                log.warning("pdf.vision.failed", locator=locator, error=str(exc))
                vision_text = ""
            if vision_text.strip():
                text = vision_text.strip()
                used_vision = True

        return ExtractedSource(
            text=text,
            source_type="PDF",
            metadata={"locator": locator, "bytes": len(data), "vision": used_vision},
        )
