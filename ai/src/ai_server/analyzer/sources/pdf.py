from __future__ import annotations

import asyncio
import io

from pypdf import PdfReader

from ai_server.analyzer.sources.base import ExtractedSource, SourceExtractor
from ai_server.storage.base import ObjectStorage


def _extract_pdf_text(data: bytes) -> str:
    reader = PdfReader(io.BytesIO(data))
    parts: list[str] = []
    for page in reader.pages:
        parts.append(page.extract_text() or "")
    return "\n\n".join(p for p in parts if p).strip()


# PDF 를 읽어 페이지 텍스트를 이어붙인다. 
class PdfSourceExtractor(SourceExtractor):

    def __init__(self, storage: ObjectStorage) -> None:
        self._storage = storage

    async def extract(self, locator: str) -> ExtractedSource:
        data = await self._storage.get_bytes(locator)
        text = await asyncio.to_thread(_extract_pdf_text, data)
        return ExtractedSource(
            text=text,
            source_type="PDF",
            metadata={"locator": locator, "bytes": len(data)},
        )
