from __future__ import annotations

import asyncio

import httpx
import structlog
import trafilatura

from ai_server.analyzer.sources.base import ExtractedSource, SourceExtractor

log = structlog.get_logger(__name__)


class WebFetchError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable


# 라이브러리로 본문 추출
class WebSourceExtractor(SourceExtractor):
    def __init__(
        self,
        *,
        timeout_sec: float = 20.0,
        max_html_bytes: int = 2_000_000,
        enable_render_fallback: bool = True,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._timeout_sec = timeout_sec
        self._max_html_bytes = max_html_bytes
        self._enable_render_fallback = enable_render_fallback
        self._client = client

    async def extract(self, locator: str) -> ExtractedSource:
        url = locator.strip()
        if not (url.startswith("http://") or url.startswith("https://")):
            raise WebFetchError(
                code="INVALID_WEB_URL",
                message=f"locator must be http(s) URL, got: {locator!r}",
                retriable=False,
            )

        html, final_url, content_type = await self._fetch_html(url)
        text = await asyncio.to_thread(_extract_main_text, html, final_url)
        rendered = False

        # 본문이 비면 JS 렌더링 SPA(React 포폴 등)일 가능성 → Playwright 로 렌더 후 재추출.
        if not text.strip() and self._enable_render_fallback:
            rendered_html = await self._render(url)
            if rendered_html:
                html = rendered_html
                text = await asyncio.to_thread(_extract_main_text, html, final_url)
                rendered = True

        if not text.strip():
            raise WebFetchError(
                code="EMPTY_WEB_BODY",
                message="추출된 본문이 비어 있음 — JS 렌더링 페이지이거나 비어있는 문서일 가능성",
                retriable=False,
            )

        return ExtractedSource(
            text=text,
            source_type="WEB",
            metadata={
                "locator": url,
                "final_url": final_url,
                "content_type": content_type,
                "html_bytes": len(html.encode("utf-8")),
                "text_chars": len(text),
                "rendered": rendered,
            },
        )

    async def _render(self, url: str) -> str | None:
        """헤드리스 chromium 으로 페이지를 렌더해 최종 HTML 을 반환.
        playwright 미설치/브라우저 미설치/렌더 실패는 None 으로 graceful."""
        try:
            from playwright.async_api import async_playwright
        except ImportError:
            log.warning("web.render.playwright_unavailable", url=url)
            return None
        try:
            async with async_playwright() as p:
                browser = await p.chromium.launch(headless=True)
                try:
                    page = await browser.new_page()
                    await page.goto(
                        url,
                        wait_until="networkidle",
                        timeout=int(self._timeout_sec * 1000),
                    )
                    return await page.content()
                finally:
                    await browser.close()
        except Exception as exc:  # noqa: BLE001
            log.warning("web.render.failed", url=url, error=str(exc))
            return None

    async def _fetch_html(self, url: str) -> tuple[str, str, str]:
        if self._client is not None:
            return await self._do_fetch(self._client, url)
        async with httpx.AsyncClient(
            timeout=self._timeout_sec,
            follow_redirects=True,
            headers={
                "User-Agent": "StackUp-AI/1.0 (+resume web extractor)",
                "Accept": "text/html,application/xhtml+xml",
            },
        ) as client:
            return await self._do_fetch(client, url)

    async def _do_fetch(
        self,
        client: httpx.AsyncClient,
        url: str,
    ) -> tuple[str, str, str]:
        try:
            resp = await client.get(url)
        except httpx.HTTPError as exc:
            raise WebFetchError(
                code="WEB_FETCH_FAILED",
                message=f"HTTP 요청 실패: {exc}",
                retriable=True,
            ) from exc

        if resp.status_code >= 400:
            raise WebFetchError(
                code="WEB_HTTP_STATUS",
                message=f"HTTP {resp.status_code}",
                retriable=resp.status_code >= 500,
            )

        content_type = resp.headers.get("content-type", "")
        if "html" not in content_type.lower():
            raise WebFetchError(
                code="WEB_NOT_HTML",
                message=f"HTML이 아님 (content-type={content_type})",
                retriable=False,
            )

        raw = resp.content
        if len(raw) > self._max_html_bytes:
            raise WebFetchError(
                code="WEB_HTML_TOO_LARGE",
                message=f"HTML이 한도({self._max_html_bytes}B)를 초과: {len(raw)}B",
                retriable=False,
            )

        html = resp.text
        return html, str(resp.url), content_type


def _extract_main_text(html: str, url: str) -> str:
    result = trafilatura.extract(
        html,
        url=url,
        include_comments=False,
        include_tables=True,
        favor_recall=True,
    )
    return (result or "").strip()
