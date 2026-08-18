from __future__ import annotations

import ipaddress
import socket
from typing import Callable
from urllib.parse import urlsplit

import structlog

log = structlog.get_logger(__name__)

# host -> IP 문자열 목록. 테스트에서 실제 DNS 를 타지 않도록 주입 가능하게 둔다.
Resolver = Callable[[str], list[str]]

_ALLOWED_SCHEMES = ("http", "https")
_REDIRECT_STATUSES = (301, 302, 303, 307, 308)


class BlockedUrlError(Exception):
    """SSRF 가드가 막은 URL."""

    def __init__(self, *, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


def default_resolver(host: str) -> list[str]:
    infos = socket.getaddrinfo(host, None, proto=socket.IPPROTO_TCP)
    return [info[4][0] for info in infos]


def is_blocked_address(raw_ip: str) -> bool:
    """루프백·사설·링크로컬(클라우드 메타데이터)·멀티캐스트·예약·와일드카드 대역인지."""
    try:
        ip = ipaddress.ip_address(raw_ip)
    except ValueError:
        # 해석할 수 없는 주소는 신뢰하지 않는다.
        return True
    return (
        ip.is_private  # 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, ::1, fc00::/7
        or ip.is_loopback
        or ip.is_link_local
        or ip.is_multicast
        or ip.is_reserved
        or ip.is_unspecified
    )


def assert_public_http_url(url: str, *, resolver: Resolver = default_resolver) -> None:
    """공개 http(s) 주소가 아니면 :class:`BlockedUrlError`.

    이 프로세스는 docker 네트워크 안에서 Core·PostgreSQL·RabbitMQ·MinIO 에 닿고, 배포
    호스트에서는 클라우드 메타데이터(169.254.169.254)에도 닿는다. Core 에도 같은 검증이
    있지만(WebResumeUrlValidator) DNS rebinding·리다이렉트로 우회되므로, **실제 소켓을 여는
    직전에** 여기서 한 번 더 확인해야 막힌다.
    """
    parts = urlsplit(url.strip())
    scheme = (parts.scheme or "").lower()
    if scheme not in _ALLOWED_SCHEMES:
        raise BlockedUrlError(
            code="INVALID_WEB_URL",
            message=f"locator must be http(s) URL, got: {url!r}",
        )
    # user:pass@host 는 파서 차이를 이용한 호스트 위장에 쓰인다.
    if parts.username or parts.password:
        raise BlockedUrlError(
            code="INVALID_WEB_URL",
            message="URL 에 사용자 정보를 포함할 수 없음",
        )
    host = parts.hostname
    if not host:
        raise BlockedUrlError(code="INVALID_WEB_URL", message="URL 에 호스트가 없음")

    try:
        addresses = resolver(host)
    except OSError as exc:
        raise BlockedUrlError(
            code="WEB_HOST_UNRESOLVED",
            message=f"호스트를 해석할 수 없음: {host}",
        ) from exc
    if not addresses:
        raise BlockedUrlError(
            code="WEB_HOST_UNRESOLVED",
            message=f"호스트를 해석할 수 없음: {host}",
        )

    for address in addresses:
        if is_blocked_address(address):
            # 어떤 내부 주소로 해석됐는지는 로그에만 남긴다.
            log.warning("web.url.blocked", host=host, resolved=address)
            raise BlockedUrlError(
                code="BLOCKED_WEB_URL",
                message="내부 네트워크 주소는 가져올 수 없음",
            )


def is_redirect(status_code: int) -> bool:
    return status_code in _REDIRECT_STATUSES
