from typing import Literal

from pydantic import BaseModel

from ai_server.model._config import camel_config


class GenerateTtsRequest(BaseModel):
    """Core 가 질문 메시지 commit 후 발행. AI 가 text 를 TTS 합성."""

    model_config = camel_config()

    session_id: int
    message_id: int  # interview_messages.id (INTERVIEWER 질문)
    text: str
    mode: Literal["PERSONALITY", "TECHNICAL", "INTEGRATED"]
    job_category: Literal["FRONTEND", "BACKEND", "INFRA", "DBA"]


class TtsCallbackPayload(BaseModel):
    """AI → Core. 합성 결과(S3 키) 또는 실패. Core TtsCallbackPayload 와 호환."""

    model_config = camel_config()

    session_id: int
    message_id: int
    status: Literal["SUCCEEDED", "FAILED"]
    audio_key: str | None = None  # camelCase 직렬화 → audioKey
    duration_sec: float | None = None
    error_code: str | None = None
