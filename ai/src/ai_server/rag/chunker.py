from __future__ import annotations

from dataclasses import dataclass

from langchain_text_splitters import (
    MarkdownHeaderTextSplitter,
    RecursiveCharacterTextSplitter,
)


@dataclass(frozen=True)
class Chunk:
    index: int
    text: str
    # 이 청크가 속한 마크다운 헤딩 경로 (예: "주요 경험 > 결제 시스템").
    # Contextual Retrieval(문맥 프리픽스)에서 사용. 없으면 빈 문자열.
    heading_path: str = ""


# 마크다운 헤딩 기준 1차 분할 (## 개요 / ## 주요 경험 / ## 기술 ...).
_HEADERS_TO_SPLIT_ON = [("#", "h1"), ("##", "h2"), ("###", "h3")]

# 2차(크기) 분할의 separator 우선순위.
# 코드 펜스(```)·문단을 먼저 경계로 삼아 함수/코드블록 중간 절단을 최소화한다.
_SEPARATORS = ["\n```", "\n\n", "\n", " ", ""]


# md를 "헤딩 단위 → 크기 단위" 2단계로 잘라낸다.
# 한 청크 = 한 의미 단위(섹션)에 가깝고, 코드 블록은 가급적 통째로 유지.
class MarkdownChunker:
    def __init__(self, *, chunk_size: int, chunk_overlap: int) -> None:
        if chunk_size <= 0:
            raise ValueError(f"chunk_size must be > 0, got {chunk_size}")
        if chunk_overlap < 0 or chunk_overlap >= chunk_size:
            raise ValueError(
                f"chunk_overlap must be in [0, chunk_size), got {chunk_overlap}"
            )
        # strip_headers=False: 헤딩 라인을 본문에 남겨 LLM 가독성/맥락 유지.
        self._header_splitter = MarkdownHeaderTextSplitter(
            headers_to_split_on=_HEADERS_TO_SPLIT_ON,
            strip_headers=False,
        )
        self._body_splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            length_function=len,
            separators=_SEPARATORS,
        )

    def split(self, markdown: str) -> list[Chunk]:
        if not markdown or not markdown.strip():
            return []

        sections = self._header_splitter.split_text(markdown)
        chunks: list[Chunk] = []
        index = 0
        for section in sections:
            heading_path = _heading_path(section.metadata)
            for part in self._body_splitter.split_text(section.page_content):
                if not part.strip():
                    continue
                chunks.append(Chunk(index=index, text=part, heading_path=heading_path))
                index += 1
        return chunks


def _heading_path(metadata: dict[str, str]) -> str:
    return " > ".join(metadata[key] for key in ("h1", "h2", "h3") if metadata.get(key))
