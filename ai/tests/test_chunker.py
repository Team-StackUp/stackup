import pytest

from ai_server.rag.chunker import MarkdownChunker


def test_split_short_text_into_single_chunk() -> None:
    chunker = MarkdownChunker(chunk_size=200, chunk_overlap=20)
    chunks = chunker.split("# 짧은 문서\n한 문장.")
    assert len(chunks) == 1
    assert chunks[0].index == 0
    assert "짧은 문서" in chunks[0].text


def test_split_long_text_into_multiple_chunks_with_overlap() -> None:
    # 충분히 길어서 chunk_size 초과
    body = "## 개요\n" + ("문장. " * 200)
    chunker = MarkdownChunker(chunk_size=200, chunk_overlap=50)
    chunks = chunker.split(body)
    assert len(chunks) >= 2
    assert chunks[0].index == 0
    assert chunks[-1].index == len(chunks) - 1
    for c in chunks:
        assert len(c.text) <= 200 + 50  # splitter 특성상 약간의 여유


def test_empty_text_returns_no_chunks() -> None:
    chunker = MarkdownChunker(chunk_size=100, chunk_overlap=10)
    assert chunker.split("") == []
    assert chunker.split("   \n\t ") == []


def test_invalid_chunk_size_raises() -> None:
    with pytest.raises(ValueError):
        MarkdownChunker(chunk_size=0, chunk_overlap=0)


def test_overlap_must_be_less_than_chunk_size() -> None:
    with pytest.raises(ValueError):
        MarkdownChunker(chunk_size=100, chunk_overlap=100)
    with pytest.raises(ValueError):
        MarkdownChunker(chunk_size=100, chunk_overlap=-1)


def test_chunk_indices_are_sequential() -> None:
    chunker = MarkdownChunker(chunk_size=50, chunk_overlap=10)
    chunks = chunker.split("abc " * 200)
    indices = [c.index for c in chunks]
    assert indices == list(range(len(chunks)))


def test_heading_path_is_captured_from_markdown_headers() -> None:
    md = (
        "# 이력서\n"
        "## 주요 경험\n"
        "### 결제 시스템\n"
        "분산 락으로 동시성을 해결했다.\n"
        "## 기술\n"
        "Kafka 를 사용했다.\n"
    )
    chunker = MarkdownChunker(chunk_size=500, chunk_overlap=50)
    chunks = chunker.split(md)
    paths = {c.heading_path for c in chunks}
    # 헤딩 경로가 섹션별로 부여됨
    assert any("결제 시스템" in p for p in paths)
    assert any("기술" in p for p in paths)
    # 헤딩 경로는 상위>하위 형태
    deep = next(c for c in chunks if "결제 시스템" in c.heading_path)
    assert deep.heading_path.startswith("이력서 > 주요 경험")


def test_no_headers_yields_empty_heading_path() -> None:
    chunker = MarkdownChunker(chunk_size=200, chunk_overlap=20)
    chunks = chunker.split("헤딩 없는 평범한 본문 문장입니다.")
    assert len(chunks) == 1
    assert chunks[0].heading_path == ""


def test_chunk_indices_sequential_across_multiple_sections() -> None:
    md = "## A\n" + ("문장. " * 100) + "\n## B\n" + ("다른. " * 100)
    chunker = MarkdownChunker(chunk_size=150, chunk_overlap=30)
    chunks = chunker.split(md)
    assert len(chunks) >= 2
    assert [c.index for c in chunks] == list(range(len(chunks)))
