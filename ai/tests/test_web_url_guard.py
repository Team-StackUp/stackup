from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest

from ai_server.analyzer.sources.url_guard import (
    BlockedUrlError,
    assert_public_http_url,
    is_blocked_address,
)
from ai_server.analyzer.sources.web import WebFetchError, WebSourceExtractor

# DNS 를 타지 않는 해석기. example.com 계열만 공개 IP, 나머지는 입력을 그대로 IP 로 본다.
_PUBLIC = "93.184.216.34"


def _resolver(host: str) -> list[str]:
    if host.endswith("example.com"):
        return [_PUBLIC]
    if host == "rebind.test":
        return ["10.1.2.3"]  # 공개 도메인이 사설 IP 로 해석되는 경우
    return [host]


def _guard(url: str) -> None:
    assert_public_http_url(url, resolver=_resolver)


class TestBlockedAddress:
    @pytest.mark.parametrize(
        "ip",
        [
            "127.0.0.1",
            "10.0.0.5",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",  # 클라우드 메타데이터
            "0.0.0.0",
            "::1",
            "fc00::1",
            "224.0.0.1",  # 멀티캐스트
            "not-an-ip",  # 해석 불가 → 신뢰하지 않는다
        ],
    )
    def test_blocks_non_public(self, ip: str) -> None:
        assert is_blocked_address(ip) is True

    @pytest.mark.parametrize("ip", [_PUBLIC, "1.1.1.1", "2606:4700::1111"])
    def test_allows_public(self, ip: str) -> None:
        assert is_blocked_address(ip) is False


class TestAssertPublicHttpUrl:
    def test_accepts_public_https(self) -> None:
        _guard("https://example.com/portfolio")

    @pytest.mark.parametrize(
        "url",
        [
            "file:///etc/passwd",
            "ftp://example.com/x",
            "gopher://example.com/",
            "example.com",
            "//example.com/x",
        ],
    )
    def test_rejects_bad_scheme(self, url: str) -> None:
        with pytest.raises(BlockedUrlError) as exc:
            _guard(url)
        assert exc.value.code == "INVALID_WEB_URL"

    def test_rejects_userinfo(self) -> None:
        with pytest.raises(BlockedUrlError) as exc:
            _guard("https://evil@example.com/")
        assert exc.value.code == "INVALID_WEB_URL"

    @pytest.mark.parametrize(
        "url",
        [
            "http://127.0.0.1:8080/api/internal/documents/1",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/",
            "http://[::1]/",
        ],
    )
    def test_rejects_internal_addresses(self, url: str) -> None:
        with pytest.raises(BlockedUrlError) as exc:
            _guard(url)
        assert exc.value.code == "BLOCKED_WEB_URL"

    # 이름이 아니라 해석된 주소로 판단해야 막힌다.
    def test_rejects_public_name_resolving_to_private(self) -> None:
        with pytest.raises(BlockedUrlError) as exc:
            _guard("https://rebind.test/")
        assert exc.value.code == "BLOCKED_WEB_URL"

    def test_rejects_unresolvable_host(self) -> None:
        def failing(host: str) -> list[str]:
            raise OSError("nope")

        with pytest.raises(BlockedUrlError) as exc:
            assert_public_http_url("https://nope.invalid/", resolver=failing)
        assert exc.value.code == "WEB_HOST_UNRESOLVED"


def _redirect_client(*hops: tuple[int, str | None]) -> MagicMock:
    """hop 목록을 순서대로 반환하는 클라이언트. (status, location) — location=None 이면 본문 응답."""
    responses = []
    for status, location in hops:
        resp = MagicMock(spec=httpx.Response)
        resp.status_code = status
        body = (
            "<html><body><article><p>본문 텍스트가 충분히 길게 있습니다.</p>"
            "</article></body></html>"
        )
        resp.headers = {"content-type": "text/html"} | (
            {"location": location} if location else {}
        )
        resp.text = body
        resp.content = body.encode("utf-8")
        resp.url = "https://example.com/final"
        responses.append(resp)
    client = MagicMock()
    client.get = AsyncMock(side_effect=responses)
    return client


class TestRedirectGuard:
    # 공개 URL → 내부 주소 리다이렉트. 자동 추적을 켜두면 내부 응답을 그대로 요약해 돌려준다.
    @pytest.mark.asyncio
    async def test_blocks_redirect_to_internal_address(self) -> None:
        client = _redirect_client((302, "http://169.254.169.254/latest/meta-data/"))
        extractor = WebSourceExtractor(client=client, resolver=_resolver)

        with pytest.raises(WebFetchError) as exc:
            await extractor.extract("https://example.com/start")

        assert exc.value.code == "BLOCKED_WEB_URL"
        assert exc.value.retriable is False

    @pytest.mark.asyncio
    async def test_follows_public_redirect(self) -> None:
        client = _redirect_client((302, "https://example.com/final"), (200, None))
        extractor = WebSourceExtractor(client=client, resolver=_resolver)

        result = await extractor.extract("https://example.com/start")

        assert "본문 텍스트" in result.text

    # 상대 Location 도 절대 URL 로 합친 뒤 검증해야 한다.
    @pytest.mark.asyncio
    async def test_resolves_relative_location_before_checking(self) -> None:
        client = _redirect_client((302, "/moved"), (200, None))
        extractor = WebSourceExtractor(client=client, resolver=_resolver)

        result = await extractor.extract("https://example.com/start")

        assert "본문 텍스트" in result.text
        assert client.get.await_args_list[1].args[0] == "https://example.com/moved"

    @pytest.mark.asyncio
    async def test_rejects_redirect_loop(self) -> None:
        hops = [(302, "https://example.com/loop")] * 8
        client = _redirect_client(*hops)
        extractor = WebSourceExtractor(
            client=client, resolver=_resolver, max_redirects=3
        )

        with pytest.raises(WebFetchError) as exc:
            await extractor.extract("https://example.com/start")

        assert exc.value.code == "WEB_TOO_MANY_REDIRECTS"
