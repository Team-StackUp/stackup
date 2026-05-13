from __future__ import annotations

import structlog
from aio_pika import connect_robust
from aio_pika.abc import AbstractRobustChannel, AbstractRobustConnection

log = structlog.get_logger(__name__)


class RabbitConnection:
    """Thin wrapper around aio-pika's connect_robust.

    Holds one connection + one channel. Auto-reconnect is provided by
    aio-pika; we just expose lifecycle hooks for FastAPI lifespan.
    """

    def __init__(self, url: str, prefetch: int) -> None:
        self._url = url
        self._prefetch = prefetch
        self._connection: AbstractRobustConnection | None = None
        self._channel: AbstractRobustChannel | None = None

    async def open(self) -> None:
        log.info("rabbit.connect.start", url_masked=_mask(self._url))
        self._connection = await connect_robust(self._url)
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=self._prefetch)
        self._channel = channel  # type: ignore[assignment]
        log.info("rabbit.connect.ok", prefetch=self._prefetch)

    async def close(self) -> None:
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()
            log.info("rabbit.connect.closed")

    @property
    def channel(self) -> AbstractRobustChannel:
        if self._channel is None:
            raise RuntimeError("RabbitConnection not opened")
        return self._channel


def _mask(url: str) -> str:
    # amqp://user:pass@host:port/  →  amqp://***@host:port/
    if "@" not in url:
        return url
    scheme, rest = url.split("://", 1) if "://" in url else ("", url)
    creds, hostpart = rest.split("@", 1)
    return f"{scheme}://***@{hostpart}" if scheme else f"***@{hostpart}"
