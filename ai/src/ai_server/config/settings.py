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
    ai_queue_feedback: str = "ai.generate.feedback"
    ai_queue_voice: str = "ai.analyze.voice"
    ai_queue_tts: str = "ai.generate.tts"
    ai_queue_prefetch: int = 10
    ai_callback_exchange: str = "stackup.ai-to-core"
    ai_callback_routing_analysis: str = "callback.analysis"
    ai_callback_routing_questions: str = "callback.questions"
    ai_callback_routing_feedback: str = "callback.feedback"
    ai_callback_routing_voice: str = "callback.voice"
    ai_callback_routing_tts: str = "callback.tts"
    # AI -> RealTime 직접 발행 (분석 단계 진행 상황). Core 를 거치지 않는 휘발성 알림.
    ai_realtime_exchange: str = "stackup.realtime"
    ai_realtime_routing_user: str = "realtime.user.notify"
    feedback_rag_top_k: int = 5
    # 리랭킹: 하이브리드 검색으로 후보 N개(candidate_k)를 가져와 LLM 으로 재정렬 후 top_k 주입.
    rerank_enabled: bool = True
    rerank_candidate_k: int = 20
    # 꼬리질문 RAG 저지연: 리랭커(LLM 호출, 종종 실패)를 건너뛰고 검색 순서를 그대로 쓴다.
    followup_rerank_enabled: bool = False
    # RAG 컨텍스트 구성(임베딩+검색+리랭크) 전체 상한. 초과 시 (none) 으로 폴백해 첫 토큰을 막지 않는다.
    followup_rag_timeout_sec: float = 1.5
    # 질문 풀 초기 크기. Core 의 applyPool 이 questions[0] 만 INSERT 하므로 1 로 고정해 토큰 낭비 차단.
    # 후속 작업에서 풀 저장 도입 시 늘리기 (예: 5).
    questions_initial_pool_size: int = 1

    # STT (음성 답변). "auto" 면 deepgram > openai_whisper > mock 순으로 키 보유 여부에 따라 자동 선택.
    stt_provider: Literal["auto", "mock", "openai_whisper", "deepgram"] = "auto"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    whisper_model: str = "whisper-1"
    whisper_language: str = "ko"
    whisper_timeout_sec: float = 60.0
    deepgram_api_key: str = ""
    deepgram_base_url: str = "https://api.deepgram.com/v1"
    deepgram_model: str = "whisper-large"  # 한국어 정확도 우선; 저비용 우선 시 nova-2.
    deepgram_language: str = "ko"
    deepgram_timeout_sec: float = 60.0

    # 스트리밍 STT (실시간 음성 답변). "auto" 면 DEEPGRAM_API_KEY 보유 시 deepgram_live, 없으면 mock.
    live_stt_provider: Literal["auto", "mock", "deepgram_live"] = "auto"
    deepgram_live_url: str = "wss://api.deepgram.com/v1/listen"
    deepgram_live_model: str = "nova-2"  # 스트리밍은 nova-2(저지연). 한국어 지원.
    deepgram_live_language: str = "ko"
    deepgram_live_endpointing_ms: int = 800  # 무음 800ms → utterance end
    voice_stream_internal_path: str = "/internal/voice/stream"

    # TTS (질문 음성화). "auto" 면 gemini > openai 순으로 키 보유 시 선택, 둘 다 없으면 mock.
    # Deepgram/OpenAI TTS 는 한국어 미지원이라 기본은 Gemini TTS(GEMINI_API_KEY 재사용).
    tts_provider: Literal["auto", "mock", "openai", "gemini"] = "auto"
    openai_tts_model: str = "gpt-4o-mini-tts"
    openai_tts_voice: str = "alloy"
    openai_tts_timeout_sec: float = 30.0
    # Gemini TTS — 한국어 지원. raw PCM(L16) 반환 → 저장 시 WAV 로 감싼다.
    gemini_tts_model: str = "gemini-2.5-flash-preview-tts"
    gemini_tts_voice: str = "Kore"
    gemini_tts_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    gemini_tts_timeout_sec: float = 30.0
    tts_audio_key_template: str = "interview/tts/{session_id}/{message_id}.mp3"
    # 음성 분석
    voice_filler_pattern: str = r"(?:음+|어+|그+|아+)"
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

    # 꼬리질문용 Flash 모델 (저지연 < 3s)
    llm_flash_model: str = "gemini-3.1-flash-lite"
    llm_flash_temperature: float = 0.4
    llm_flash_max_tokens: int = 512

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
    # 한 임베딩 요청당 청크 수. 크면 분당 토큰 한도(429)에 걸리기 쉬우니 적당히 쪼갠다.
    embedding_batch_size: int = 32
    # 429(RESOURCE_EXHAUSTED) 시 지수 백오프 재시도 횟수·기본 지연(초).
    embedding_max_retries: int = 5
    embedding_retry_base_delay_sec: float = 2.0

    # PDF Vision (이미지/스캔 PDF 폴백 — 게이트웨이 멀티모달)
    pdf_vision_max_pages: int = 5
    pdf_vision_dpi: int = 150

    gemini_api_key: str = ""


def get_settings() -> Settings:
    return Settings()
