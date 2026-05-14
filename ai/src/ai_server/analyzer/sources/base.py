from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, Literal

from pydantic import BaseModel, Field

SourceType = Literal["PDF", "REPOSITORY", "WEB"]


# 모든 Source Extractor가 공통으로 반환하는 결과 모델 
class ExtractedSource(BaseModel):
    text: str
    source_type: SourceType
    metadata: dict[str, Any] = Field(default_factory=dict)


# 단일 입력 형태에 대한 Adapter
class SourceExtractor(ABC):

    @abstractmethod
    async def extract(self, locator: str) -> ExtractedSource:
