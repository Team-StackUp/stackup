from __future__ import annotations

from abc import ABC, abstractmethod


# 스토리지 키 전용 에러 
class StorageKeyError(ValueError):


class ObjectStorage(ABC):

    @abstractmethod
    async def get_bytes(self, key: str) -> bytes: ...

    @abstractmethod
    async def put_bytes(
        self,
        key: str,
        data: bytes,
        *,
        content_type: str | None = None,
    ) -> None: ...

    @abstractmethod
    async def put_text(
        self,
        key: str,
        text: str,
        *,
        content_type: str = "text/markdown; charset=utf-8",
    ) -> None: ...

    @abstractmethod
    async def exists(self, key: str) -> bool: ...
