from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from ai_server.analyzer.sources.pdf import PdfSourceExtractor


def _fake_pdf_reader(page_texts: list[str]) -> MagicMock:
    reader = MagicMock()
    pages = []
    for t in page_texts:
        page = MagicMock()
        page.extract_text.return_value = t
        pages.append(page)
    reader.pages = pages
    return reader


@pytest.mark.asyncio
async def test_extract_reads_from_storage_and_joins_pages() -> None:
    storage = AsyncMock()
    storage.get_bytes.return_value = b"PDF-BYTES"

    fake_reader = _fake_pdf_reader(["page one", "page two"])
    with patch(
        "ai_server.analyzer.sources.pdf.PdfReader", return_value=fake_reader
    ) as reader_cls:
        extractor = PdfSourceExtractor(storage=storage)
        result = await extractor.extract("resumes/raw/1/x.pdf")

    storage.get_bytes.assert_awaited_once_with("resumes/raw/1/x.pdf")
    reader_cls.assert_called_once()
    assert "page one" in result.text
    assert "page two" in result.text
    assert result.source_type == "PDF"
    assert result.metadata["bytes"] == len(b"PDF-BYTES")
    assert result.metadata["locator"] == "resumes/raw/1/x.pdf"


@pytest.mark.asyncio
async def test_extract_skips_none_page_text() -> None:
    storage = AsyncMock()
    storage.get_bytes.return_value = b"PDF-BYTES"

    fake_reader = _fake_pdf_reader(["", "real content"])
    fake_reader.pages[0].extract_text.return_value = None  # pypdf may return None

    with patch("ai_server.analyzer.sources.pdf.PdfReader", return_value=fake_reader):
        extractor = PdfSourceExtractor(storage=storage)
        result = await extractor.extract("a.pdf")

    assert result.text == "real content"


@pytest.mark.asyncio
async def test_extract_returns_empty_on_empty_pdf() -> None:
    storage = AsyncMock()
    storage.get_bytes.return_value = b"PDF-BYTES"

    fake_reader = _fake_pdf_reader([])
    with patch("ai_server.analyzer.sources.pdf.PdfReader", return_value=fake_reader):
        extractor = PdfSourceExtractor(storage=storage)
        result = await extractor.extract("empty.pdf")

    assert result.text == ""
    assert result.source_type == "PDF"


@pytest.mark.asyncio
async def test_extract_falls_back_to_vision_when_text_sparse() -> None:
    storage = AsyncMock()
    storage.get_bytes.return_value = b"PDF-BYTES"
    vision = AsyncMock()
    vision.extract_text = AsyncMock(return_value="비전으로 추출한 이력서 본문")

    with patch("ai_server.analyzer.sources.pdf._extract_pdf_text", return_value=""):
        extractor = PdfSourceExtractor(storage=storage, vision_reader=vision)
        result = await extractor.extract("scanned.pdf")

    vision.extract_text.assert_awaited_once_with(b"PDF-BYTES")
    assert result.text == "비전으로 추출한 이력서 본문"
    assert result.metadata["vision"] is True


@pytest.mark.asyncio
async def test_extract_skips_vision_when_text_sufficient() -> None:
    storage = AsyncMock()
    storage.get_bytes.return_value = b"PDF-BYTES"
    vision = AsyncMock()
    vision.extract_text = AsyncMock(return_value="should not be used")
    long_text = "충분히 긴 텍스트 레이어가 있는 일반 PDF 입니다. " * 3

    with patch(
        "ai_server.analyzer.sources.pdf._extract_pdf_text", return_value=long_text
    ):
        extractor = PdfSourceExtractor(storage=storage, vision_reader=vision)
        result = await extractor.extract("normal.pdf")

    vision.extract_text.assert_not_awaited()
    assert "충분히 긴 텍스트 레이어" in result.text
    assert result.metadata["vision"] is False


@pytest.mark.asyncio
async def test_extract_vision_failure_falls_back_gracefully() -> None:
    storage = AsyncMock()
    storage.get_bytes.return_value = b"PDF-BYTES"
    vision = AsyncMock()
    vision.extract_text = AsyncMock(side_effect=RuntimeError("vision down"))

    with patch("ai_server.analyzer.sources.pdf._extract_pdf_text", return_value=""):
        extractor = PdfSourceExtractor(storage=storage, vision_reader=vision)
        result = await extractor.extract("scanned.pdf")

    assert result.text == ""  # 비전 실패 → 원래 텍스트(빈값)로 graceful
    assert result.metadata["vision"] is False
