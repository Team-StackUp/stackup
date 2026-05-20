from unittest.mock import MagicMock

import pytest
from botocore.exceptions import ClientError

from ai_server.storage.base import StorageKeyError
from ai_server.storage.s3 import S3Storage


def _make_storage() -> tuple[S3Storage, MagicMock]:
    client = MagicMock()
    storage = S3Storage(bucket="test-bucket", client=client)
    return storage, client


@pytest.mark.asyncio
async def test_get_bytes_calls_s3_get_object() -> None:
    storage, client = _make_storage()
    body = MagicMock()
    body.read.return_value = b"hello"
    client.get_object.return_value = {"Body": body}

    out = await storage.get_bytes("a/b/c.pdf")

    client.get_object.assert_called_once_with(Bucket="test-bucket", Key="a/b/c.pdf")
    body.read.assert_called_once()
    assert out == b"hello"


@pytest.mark.asyncio
async def test_put_bytes_passes_content_type() -> None:
    storage, client = _make_storage()
    await storage.put_bytes("k.bin", b"x", content_type="application/octet-stream")

    client.put_object.assert_called_once()
    kwargs = client.put_object.call_args.kwargs
    assert kwargs["Bucket"] == "test-bucket"
    assert kwargs["Key"] == "k.bin"
    assert kwargs["Body"] == b"x"
    assert kwargs["ContentType"] == "application/octet-stream"


@pytest.mark.asyncio
async def test_put_bytes_omits_content_type_when_none() -> None:
    storage, client = _make_storage()
    await storage.put_bytes("k.bin", b"x")

    kwargs = client.put_object.call_args.kwargs
    assert "ContentType" not in kwargs


@pytest.mark.asyncio
async def test_put_text_encodes_utf8_with_markdown_content_type() -> None:
    storage, client = _make_storage()
    await storage.put_text("doc.md", "# 한글")

    kwargs = client.put_object.call_args.kwargs
    assert kwargs["Body"] == "# 한글".encode("utf-8")
    assert "markdown" in kwargs["ContentType"]


@pytest.mark.asyncio
async def test_exists_returns_true_when_head_succeeds() -> None:
    storage, client = _make_storage()
    client.head_object.return_value = {}
    assert await storage.exists("doc.md") is True
    client.head_object.assert_called_once_with(Bucket="test-bucket", Key="doc.md")


@pytest.mark.asyncio
async def test_exists_returns_false_on_404() -> None:
    storage, client = _make_storage()
    client.head_object.side_effect = ClientError(
        {"Error": {"Code": "404", "Message": "Not Found"}},
        "HeadObject",
    )
    assert await storage.exists("missing.md") is False


@pytest.mark.asyncio
async def test_exists_reraises_unexpected_client_error() -> None:
    storage, client = _make_storage()
    client.head_object.side_effect = ClientError(
        {"Error": {"Code": "AccessDenied", "Message": "nope"}},
        "HeadObject",
    )
    with pytest.raises(ClientError):
        await storage.exists("forbidden.md")


@pytest.mark.asyncio
async def test_rejects_absolute_key() -> None:
    storage, client = _make_storage()
    with pytest.raises(StorageKeyError):
        await storage.put_bytes("/etc/passwd", b"x")
    client.put_object.assert_not_called()


@pytest.mark.asyncio
async def test_rejects_empty_key() -> None:
    storage, _ = _make_storage()
    with pytest.raises(StorageKeyError):
        await storage.get_bytes("")


@pytest.mark.asyncio
async def test_exists_false_on_invalid_key() -> None:
    storage, client = _make_storage()
    assert await storage.exists("/abs") is False
    client.head_object.assert_not_called()


def test_constructor_requires_bucket() -> None:
    with pytest.raises(ValueError):
        S3Storage(bucket="", client=MagicMock())
