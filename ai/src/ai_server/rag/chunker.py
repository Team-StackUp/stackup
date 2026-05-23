from __future__ import annotations

from dataclasses import dataclass

from langchain_text_splitters import RecursiveCharacterTextSplitter


@dataclass(frozen=True)
class Chunk:
    index: int
    text: str


# md를 잘라냄. size와 overlap은 설정에서 가져다 씀
class MarkdownChunker:
    def __init__(self, *, chunk_size: int, chunk_overlap: int) -> None:
        if chunk_size <= 0:
            raise ValueError(f"chunk_size must be > 0, got {chunk_size}")
        if chunk_overlap < 0 or chunk_overlap >= chunk_size:
            raise ValueError(
                f"chunk_overlap must be in [0, chunk_size), got {chunk_overlap}"
            )
        self._splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            length_function=len,
        )

    def split(self, markdown: str) -> list[Chunk]:
        if not markdown or not markdown.strip():
            return []
        parts = self._splitter.split_text(markdown)
        return [Chunk(index=i, text=text) for i, text in enumerate(parts)]
