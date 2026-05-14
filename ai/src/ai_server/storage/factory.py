from __future__ import annotations

from ai_server.config.settings import Settings
from ai_server.storage.base import ObjectStorage
from ai_server.storage.local_fs import LocalFilesystemStorage
from ai_server.storage.s3 import S3Storage


# 설정에 명시된 스토리지를 생성함 
def build_storage(settings: Settings) -> ObjectStorage:
    backend = settings.storage_backend
    if backend == "local":
        return LocalFilesystemStorage(root_dir=settings.storage_local_root)
    if backend == "s3":
        return S3Storage(
            bucket=settings.s3_bucket_name,
            endpoint_url=settings.s3_endpoint_url or None,
            access_key=settings.s3_access_key or None,
            secret_key=settings.s3_secret_key or None,
            region=settings.s3_region,
        )
    raise ValueError(f"Unsupported STORAGE_BACKEND={backend!r}")
