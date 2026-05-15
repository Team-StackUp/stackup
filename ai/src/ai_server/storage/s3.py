from __future__ import annotations

import asyncio
from typing import Any

import boto3
from botocore.client import BaseClient
from botocore.exceptions import ClientError

from ai_server.storage.base import ObjectStorage, StorageKeyError


# S3 호환 스토리지 (MinIO 포함)
class S3Storage(ObjectStorage):

    def __init__(
        self,
        *,
        bucket: str,
        endpoint_url: str | None = None,
        access_key: str | None = None,
        secret_key: str | None = None,
        region: str = "us-east-1",
        client: BaseClient | None = None,
    ) -> None:
        if not bucket:
            raise ValueError("bucket name required")
        self._bucket = bucket
        if client is not None:
            self._client = client
        else:
            self._client = boto3.client(
                "s3",
                endpoint_url=endpoint_url or None,
                aws_access_key_id=access_key or None,
                aws_secret_access_key=secret_key or None,
                region_name=region,
            )

    @property
    def bucket(self) -> str:
        return self._bucket

    @property
    def client(self) -> BaseClient:
        return self._client

    @staticmethod
    def _validate_key(key: str) -> None:
        if not key or key.startswith("/") or "\x00" in key:
            raise StorageKeyError(f"invalid storage key: {key!r}")

    async def get_bytes(self, key: str) -> bytes:
        self._validate_key(key)
        resp = await asyncio.to_thread(
            self._client.get_object, Bucket=self._bucket, Key=key
        )
        body = resp["Body"]
        return await asyncio.to_thread(body.read)

    async def put_bytes(
        self,
        key: str,
        data: bytes,
        *,
        content_type: str | None = None,
    ) -> None:
        self._validate_key(key)
        kwargs: dict[str, Any] = {
            "Bucket": self._bucket,
            "Key": key,
            "Body": data,
        }
        if content_type:
            kwargs["ContentType"] = content_type
        await asyncio.to_thread(self._client.put_object, **kwargs)

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
            self._validate_key(key)
        except StorageKeyError:
            return False
        try:
            await asyncio.to_thread(
                self._client.head_object, Bucket=self._bucket, Key=key
            )
            return True
        except ClientError as exc:
            code = exc.response.get("Error", {}).get("Code", "")
            if code in ("404", "NoSuchKey", "NotFound"):
                return False
            raise
