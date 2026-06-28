from __future__ import annotations

from ai_server.analyzer.sources.base import (
    ExtractedSource,
    SourceExtractor,
    SourceType,
)


# inline 텍스트(자소서 문항 마크다운 등)를 그대로 ExtractedSource 로 감싼다.
# locator 자체가 본문 — S3/URL fetch 없음. 자소서처럼 Core 가 본문을 직접 실어 보낼 때 사용.
class TextSourceExtractor(SourceExtractor):

    def __init__(self, *, source_type: SourceType = "COVER_LETTER") -> None:
        self._source_type = source_type

    async def extract(self, locator: str) -> ExtractedSource:
        return ExtractedSource(
            text=locator or "",
            source_type=self._source_type,
            metadata={"length": len(locator or "")},
        )
