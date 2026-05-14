from typing import Literal

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
    rabbitmq_url: str = "amqp://stackup:stackup@localhost:38050/"

    # AI consumer (큐 이름, prefetch, 콜백 라우팅 등)
    ai_queue_resume: str = "ai.analyze.resume"
    ai_queue_prefetch: int = 10
    ai_callback_exchange: str = "stackup.ai-to-core"
    ai_callback_routing_analysis: str = "callback.analysis"
    ai_publisher_name: str = "ai-server"
    ai_idempotency_lru_size: int = 1024

    storage_backend: Literal["local", "s3"] = "s3"
    storage_local_root: str = "./var/storage"

    s3_endpoint_url: str = "http://localhost:38060"
    s3_access_key: str = ""
    s3_secret_key: str = ""
    s3_bucket_name: str = "stackup"
    s3_region: str = "us-east-1"

    # 일단 충대 API 키 사용
    llm_api_key: str = ""
    llm_base_url: str = "https://factchat-cloud.mindlogic.ai/v1/gateway"
    llm_pro_model: str = "gemini-3.1-pro-preview"
    llm_pro_temperature: float = 0.2

    analyzed_resume_md_key_template: str = "analyzed/resume/{resume_id}/summary.md"


def get_settings() -> Settings:
    return Settings()
