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
    ai_queue_repository: str = "ai.analyze.repository"
    ai_queue_web: str = "ai.analyze.web"
    ai_queue_questions: str = "ai.generate.questions"
    ai_queue_followup: str = "ai.generate.followup"
    ai_queue_prefetch: int = 10
    ai_callback_exchange: str = "stackup.ai-to-core"
    ai_callback_routing_analysis: str = "callback.analysis"
    ai_callback_routing_questions: str = "callback.questions"
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
    analyzed_repository_md_key_template: str = (
        "analyzed/repository/{repository_id}/summary.md"
    )
    analyzed_web_resume_md_key_template: str = (
        "analyzed/web-resume/{resume_id}/summary.md"
    )

    # Core 서버 internal API (사용자별 GitHub access_token 조회 등)
    core_internal_base_url: str = "http://localhost:38010"
    core_internal_api_key: str = ""
    core_internal_timeout_sec: float = 10.0

    # GitHub repo 분석용
    github_api_base_url: str = "https://api.github.com"
    # 사용자별 token은 Core에서 받음. 아래 token은 fallback (public repo 한정).
    github_fallback_token: str = ""
    repo_max_source_files: int = 8
    repo_max_source_file_bytes: int = 50_000
    repo_fetch_timeout_sec: float = 30.0

    # 웹 이력서 fetch
    web_fetch_timeout_sec: float = 20.0
    web_max_html_bytes: int = 2_000_000  # 2MB 상한

    # 임베딩 관련
    embedding_provider: Literal["mock", "gemini", "openai", "ollama"] = "mock"
    embedding_model: str = "gemini-embedding-001"
    embedding_dim: int = 1536
    embedding_chunk_size: int = 1000
    embedding_chunk_overlap: int = 200
    embedding_batch_size: int = 32 

    gemini_api_key: str = ""


def get_settings() -> Settings:
    return Settings()
