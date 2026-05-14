from pathlib import Path

import pytest

from ai_server.storage.base import StorageKeyError
from ai_server.storage.local_fs import LocalFilesystemStorage


@pytest.mark.asyncio
async def test_put_then_get_roundtrip(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    await storage.put_bytes("nested/dir/file.bin", b"hello world")
    assert await storage.get_bytes("nested/dir/file.bin") == b"hello world"


@pytest.mark.asyncio
async def test_put_text_writes_utf8(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    await storage.put_text("note.md", "# 한글")
    assert await storage.get_bytes("note.md") == "# 한글".encode("utf-8")


@pytest.mark.asyncio
async def test_put_creates_parent_dirs(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    await storage.put_text("a/b/c/d.md", "x")
    assert (tmp_path / "a" / "b" / "c" / "d.md").exists()


@pytest.mark.asyncio
async def test_exists_returns_false_for_missing(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    assert await storage.exists("never/written.md") is False


@pytest.mark.asyncio
async def test_rejects_absolute_key(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    with pytest.raises(StorageKeyError):
        await storage.put_bytes("/etc/passwd", b"x")


@pytest.mark.asyncio
async def test_rejects_parent_traversal(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    with pytest.raises(StorageKeyError):
        await storage.get_bytes("../outside.txt")


@pytest.mark.asyncio
async def test_rejects_empty_key(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    with pytest.raises(StorageKeyError):
        await storage.put_bytes("", b"x")


@pytest.mark.asyncio
async def test_exists_returns_false_on_invalid_key(tmp_path: Path) -> None:
    storage = LocalFilesystemStorage(root_dir=str(tmp_path))
    assert await storage.exists("../escape.md") is False
