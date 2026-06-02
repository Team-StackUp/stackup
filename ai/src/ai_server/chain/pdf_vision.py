from __future__ import annotations

import asyncio
import base64

import structlog
from langchain_core.messages import HumanMessage

from ai_server.chain.prompts.pdf_vision import VISION_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.observability.llm_logging_callback import CoreAiLogCallback

log = structlog.get_logger(__name__)


def _render_pdf_to_pngs(data: bytes, max_pages: int, dpi: int) -> list[bytes]:
    import fitz  # pymupdf

    images: list[bytes] = []
    with fitz.open(stream=data, filetype="pdf") as doc:
        for i, page in enumerate(doc):
            if i >= max_pages:
                break
            pix = page.get_pixmap(dpi=dpi)
            images.append(pix.tobytes("png"))
    return images


# 게이트웨이 멀티모달 모델로 PDF 페이지 이미지에서 텍스트를 추출한다 (호출 1회).
class LlmVisionPdfReader:
    def __init__(self, llm, *, max_pages: int = 5, dpi: int = 150) -> None:
        self._llm = llm
        self._max_pages = max_pages
        self._dpi = dpi

    async def extract_text(self, pdf_bytes: bytes) -> str:
        images = await asyncio.to_thread(
            _render_pdf_to_pngs, pdf_bytes, self._max_pages, self._dpi
        )
        if not images:
            return ""
        content: list[dict] = [{"type": "text", "text": VISION_PROMPT}]
        for png in images:
            b64 = base64.b64encode(png).decode("ascii")
            content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:image/png;base64,{b64}"},
                }
            )
        resp = await self._llm.ainvoke([HumanMessage(content=content)])
        text = resp.content
        if isinstance(text, list):  # 일부 provider 는 content 를 블록 리스트로 반환
            text = "".join(b.get("text", "") for b in text if isinstance(b, dict))
        return text if isinstance(text, str) else ""


def build_vision_pdf_reader(
    settings: Settings, core_client: CoreClient | None = None
) -> LlmVisionPdfReader:
    from langchain_openai import ChatOpenAI

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="analyze.pdf_vision",
                default_model=settings.llm_pro_model,
            )
        )
    llm = ChatOpenAI(
        model=settings.llm_pro_model,  # 멀티모달(gemini-3.1-pro) — 게이트웨이 경유
        temperature=0.0,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        callbacks=callbacks,
    )
    return LlmVisionPdfReader(
        llm,
        max_pages=settings.pdf_vision_max_pages,
        dpi=settings.pdf_vision_dpi,
    )
