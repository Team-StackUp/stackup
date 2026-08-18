from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from ai_server.analyzer.sources.web import WebFetchError, WebSourceExtractor


# DNS 를 타지 않는다 — example.com 계열은 공개 IP, 그 외 리터럴은 그대로.
def _fake_resolver(host: str) -> list[str]:
    if host.endswith("example.com"):
        return ["93.184.216.34"]
    return [host]


def _make_client(
    *,
    status: int = 200,
    content_type: str = "text/html; charset=utf-8",
    body: (
        str | bytes
    ) = "<html><body><h1>주요 내용</h1><p>본문 텍스트입니다.</p></body></html>",
    final_url: str = "https://example.com/r",
    raise_exc: Exception | None = None,
) -> MagicMock:
    client = MagicMock()
    resp = MagicMock(spec=httpx.Response)
    resp.status_code = status
    resp.headers = {"content-type": content_type}
    resp.text = body if isinstance(body, str) else body.decode("utf-8", errors="ignore")
    resp.content = body.encode("utf-8") if isinstance(body, str) else body
    resp.url = final_url
    if raise_exc is not None:
        client.get = AsyncMock(side_effect=raise_exc)
    else:
        client.get = AsyncMock(return_value=resp)
    return client


@pytest.mark.asyncio
async def test_extract_returns_main_body_text() -> None:
    html = (
        "<html><head><title>이력서</title></head>"
        "<body><nav>nav</nav><article><h1>김OO</h1>"
        "<p>백엔드 개발자. Spring Boot 3년차.</p></article>"
        "<footer>foot</footer></body></html>"
    )
    client = _make_client(body=html)
    extractor = WebSourceExtractor(client=client, resolver=_fake_resolver)
    result = await extractor.extract("https://example.com/r")

    assert result.source_type == "WEB"
    assert "백엔드 개발자" in result.text
    assert "김OO" in result.text
    assert result.metadata["final_url"] == "https://example.com/r"
    assert result.metadata["text_chars"] > 0


@pytest.mark.asyncio
async def test_rejects_non_http_locator() -> None:
    extractor = WebSourceExtractor(client=_make_client(), resolver=_fake_resolver)
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("ftp://example.com/x")
    assert exc_info.value.code == "INVALID_WEB_URL"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_raises_on_http_error_status() -> None:
    client = _make_client(status=503)
    extractor = WebSourceExtractor(client=client, resolver=_fake_resolver)
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("https://example.com/r")
    assert exc_info.value.code == "WEB_HTTP_STATUS"
    assert exc_info.value.retriable is True  # 5xx → retriable


@pytest.mark.asyncio
async def test_raises_on_4xx_as_non_retriable() -> None:
    client = _make_client(status=404)
    extractor = WebSourceExtractor(client=client, resolver=_fake_resolver)
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("https://example.com/r")
    assert exc_info.value.code == "WEB_HTTP_STATUS"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_rejects_non_html_content_type() -> None:
    client = _make_client(content_type="application/pdf")
    extractor = WebSourceExtractor(client=client, resolver=_fake_resolver)
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("https://example.com/r")
    assert exc_info.value.code == "WEB_NOT_HTML"


@pytest.mark.asyncio
async def test_rejects_oversized_html() -> None:
    big = b"<html>" + b"a" * 1024 + b"</html>"
    client = _make_client(body=big)
    extractor = WebSourceExtractor(
        client=client, max_html_bytes=512, resolver=_fake_resolver
    )
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("https://example.com/r")
    assert exc_info.value.code == "WEB_HTML_TOO_LARGE"


@pytest.mark.asyncio
async def test_raises_on_empty_body() -> None:
    client = _make_client(body="<html><body></body></html>")
    extractor = WebSourceExtractor(
        client=client, enable_render_fallback=False, resolver=_fake_resolver
    )
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("https://example.com/r")
    assert exc_info.value.code == "EMPTY_WEB_BODY"
    assert exc_info.value.retriable is False


@pytest.mark.asyncio
async def test_empty_body_falls_back_to_render() -> None:
    # 1차 fetch = JS 셸(본문 없음) → 렌더 폴백으로 본문 확보
    client = _make_client(body='<html><body><div id="root"></div></body></html>')
    extractor = WebSourceExtractor(client=client, resolver=_fake_resolver)
    rendered_html = (
        "<html><body><article><h1>김OO</h1>"
        "<p>프론트엔드 개발자. React 포트폴리오.</p></article></body></html>"
    )
    extractor._render = AsyncMock(return_value=rendered_html)

    result = await extractor.extract("https://example.com/spa")
    assert "프론트엔드 개발자" in result.text
    assert result.metadata["rendered"] is True
    extractor._render.assert_awaited_once()


@pytest.mark.asyncio
async def test_render_fallback_returning_none_raises_empty() -> None:
    client = _make_client(body='<html><body><div id="root"></div></body></html>')
    extractor = WebSourceExtractor(client=client, resolver=_fake_resolver)
    extractor._render = AsyncMock(return_value=None)  # 렌더 실패/불가
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("https://example.com/spa")
    assert exc_info.value.code == "EMPTY_WEB_BODY"


@pytest.mark.asyncio
async def test_raises_on_httpx_error_as_retriable() -> None:
    client = _make_client(raise_exc=httpx.ConnectError("dns fail"))
    extractor = WebSourceExtractor(client=client, resolver=_fake_resolver)
    with pytest.raises(WebFetchError) as exc_info:
        await extractor.extract("https://example.com/r")
    assert exc_info.value.code == "WEB_FETCH_FAILED"
    assert exc_info.value.retriable is True
