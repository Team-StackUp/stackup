from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # Application
    app_name: str = "stackup-ai-server"
    app_version: str = "0.1.0"
    debug: bool = False

    # RabbitMQ
    rabbitmq_url: str = "amqp://stackup:stackup@localhost:5672/"

    # S3 / MinIO
    s3_endpoint_url: str = "http://localhost:9000"
    s3_access_key: str = ""
    s3_secret_key: str = ""
    s3_bucket_name: str = "stackup"

    # LLM API Keys
    openai_api_key: str = ""
    google_api_key: str = ""


def get_settings() -> Settings:
    return Settings()
