from typing import Literal

from pydantic import BaseModel, Field

from ai_server.model._config import camel_config


class AnalyzeVoiceRequest(BaseModel):
    """Core 가 음성 답변 업로드 commit 후 발행."""
    model_config = camel_config()

    session_id: int
    message_id: int                         # interview_messages.id (placeholder, STT 후 content 채움)
    parent_question_message_id: int | None = None
    audio_s3_key: str
    content_type: str
    previous_question_text: str | None = None
    interview_type: Literal["PERSONALITY", "TECHNICAL", "LIVE_CODING", "INTEGRATED"]
    job_category: Literal["FRONTEND", "BACKEND", "INFRA", "DBA"]


class VoiceCallbackPayload(BaseModel):
    """AI → Core. STT 결과 + 음성 지표."""
    model_config = camel_config()

    session_id: int
    interview_message_id: int
    transcript: str | None = None
    speaking_rate_wpm: float | None = None
    silence_duration_sec: float | None = None
    filler_word_counts: dict[str, int] = Field(default_factory=dict)
    pronunciation_accuracy: float | None = None
    error_code: str | None = None           # 실패 시 코드 (Core 가 message FAILED 마킹)
