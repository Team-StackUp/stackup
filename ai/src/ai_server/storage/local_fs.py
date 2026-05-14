from __future__ import annotations

import os
from pathlib import Path

import aiofiles
import aiofiles.os

from ai_server.storage.base import ObjectStorage, StorageKeyError


# local 일 때 사용되는 스토리지 시스템 
class LocalFilesystemStorage(ObjectStorage):

    def __init__(self, root_dir: str) -> None:
        self._root = Path(root_dir).resolve()

    @property
    def root(self) -> Path:
        return self._root

    def _resolve(self, key: str) -> Path:
        if not key or key.startswith("/") or "\x00" in key:
            raise StorageKeyError(f"invalid storage key: {key!r}")
        candidate = (self._root / key).resolve()
        root_str = str(self._root)
        cand_str = str(candidate)
        if cand_str != root_str and not cand_str.startswith(root_str + os.sep):
            raise StorageKeyError(f"key escapes storage root: {key!r}")
        return candidate

    async def get_bytes(self, key: str) -> bytes:
        path = self._resolve(key)
        async with aiofiles.open(path, "rb") as f:
            return await f.read()

    async def put_bytes(
        self,
        key: str,
        data: bytes,
        *,
        content_type: str | None = None,
    ) -> None:
        path = self._resolve(key)
        await aiofiles.os.makedirs(path.parent, exist_ok=True)
        async with aiofiles.open(path, "wb") as f:
            await f.write(data)

    async def put_text(
        self,
        key: str,
        text: str,
        *,
        content_type: str = "text/markdown; charset=utf-8",
    ) -> None:
        await self.put_bytes(key, text.encode("utf-8"), content_type=content_type)

    async def exists(self, key: str) -> bool:
        try:
            path = self._resolve(key)
        except StorageKeyError:
            return False
        return await aiofiles.os.path.exists(path)
