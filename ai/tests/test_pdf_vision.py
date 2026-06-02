from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

import pytest

from ai_server.chain.pdf_vision import LlmVisionPdfReader


@pytest.mark.asyncio
async def test_vision_reader_sends_images_and_returns_text() -> None:
    fake_llm = SimpleNamespace(
        ainvoke=AsyncMock(return_value=SimpleNamespace(content="추출된 텍스트"))
    )
    reader = LlmVisionPdfReader(fake_llm, max_pages=3, dpi=100)

    with patch(
        "ai_server.chain.pdf_vision._render_pdf_to_pngs",
        return_value=[b"png-a", b"png-b"],
    ):
        out = await reader.extract_text(b"PDF")

    assert out == "추출된 텍스트"
    # LLM 에 텍스트 프롬프트 + 이미지 2장이 한 메시지로 전달됨
    msgs = fake_llm.ainvoke.await_args.args[0]
    content = msgs[0].content
    assert content[0]["type"] == "text"
    image_blocks = [c for c in content if c["type"] == "image_url"]
    assert len(image_blocks) == 2
    assert image_blocks[0]["image_url"]["url"].startswith("data:image/png;base64,")


@pytest.mark.asyncio
async def test_vision_reader_empty_when_no_pages() -> None:
    fake_llm = SimpleNamespace(ainvoke=AsyncMock())
    reader = LlmVisionPdfReader(fake_llm)
    with patch("ai_server.chain.pdf_vision._render_pdf_to_pngs", return_value=[]):
        out = await reader.extract_text(b"PDF")
    assert out == ""
    fake_llm.ainvoke.assert_not_awaited()


@pytest.mark.asyncio
async def test_vision_reader_handles_block_list_content() -> None:
    # 일부 provider 는 content 를 [{type,text}] 블록 리스트로 반환
    fake_llm = SimpleNamespace(
        ainvoke=AsyncMock(
            return_value=SimpleNamespace(
                content=[
                    {"type": "text", "text": "조각1 "},
                    {"type": "text", "text": "조각2"},
                ]
            )
        )
    )
    reader = LlmVisionPdfReader(fake_llm)
    with patch("ai_server.chain.pdf_vision._render_pdf_to_pngs", return_value=[b"png"]):
        out = await reader.extract_text(b"PDF")
    assert out == "조각1 조각2"
