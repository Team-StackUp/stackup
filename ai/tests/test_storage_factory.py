from unittest.mock import MagicMock, patch

from ai_server.config.settings import Settings
from ai_server.storage.factory import build_storage
from ai_server.storage.local_fs import LocalFilesystemStorage
from ai_server.storage.s3 import S3Storage


def test_local_backend_returns_local_fs(tmp_path) -> None:
    s = Settings(
        _env_file=None,  # type: ignore[call-arg]
        storage_backend="local",
        storage_local_root=str(tmp_path),
    )
    storage = build_storage(s)
    assert isinstance(storage, LocalFilesystemStorage)
    assert storage.root == tmp_path.resolve()


def test_s3_backend_builds_with_endpoint_and_credentials() -> None:
    s = Settings(
        _env_file=None,  # type: ignore[call-arg]
        storage_backend="s3",
        s3_bucket_name="stackup",
        s3_endpoint_url="http://minio:9000",
        s3_access_key="ak",
        s3_secret_key="sk",
        s3_region="us-east-1",
    )
    with (
        patch("ai_server.storage.factory.boto3", create=True),
        patch(
            "ai_server.storage.s3.boto3.client", return_value=MagicMock()
        ) as boto3_client,
    ):
        storage = build_storage(s)

    assert isinstance(storage, S3Storage)
    assert storage.bucket == "stackup"
    boto3_client.assert_called_once_with(
        "s3",
        endpoint_url="http://minio:9000",
        aws_access_key_id="ak",
        aws_secret_access_key="sk",
        region_name="us-east-1",
    )
