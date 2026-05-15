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
