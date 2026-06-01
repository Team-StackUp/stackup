from typing import Literal

from pydantic import BaseModel, Field

from ai_server.model._config import camel_config

InterviewMode = Literal["PERSONALITY", "TECHNICAL", "INTEGRATED"]


class FeedbackMessageItem(BaseModel):
    """세션 시퀀스 한 줄 (Core 가 통째로 동봉)."""
    model_config = camel_config()

    id: int
    sequence_number: int
    role: Literal["INTERVIEWER", "INTERVIEWEE", "SYSTEM"]
    content: str
    parent_message_id: int | None = None


class GenerateFeedbackRequest(BaseModel):
    """Core 가 세션 COMPLETED commit 후 발행."""
    model_config = camel_config()

    session_id: int
    mode: InterviewMode
    job_category: Literal["FRONTEND", "BACKEND", "INFRA", "DBA"]
    total_question_count: int | None = None
    end_reason: Literal["USER_REQUEST", "MAX_QUESTIONS_REACHED"] | None = None
    messages: list[FeedbackMessageItem] = Field(default_factory=list)
    context_document_ids: list[int] = Field(default_factory=list)


class FeedbackCallbackPayload(BaseModel):
    """AI → Core 종합 피드백. 점수는 0~100 (NULL 허용)."""
    model_config = camel_config()

    session_id: int
    overall_score: float | None = None
    technical_accuracy: float | None = None
    logic_score: float | None = None
    communication_score: float | None = None
    strengths_summary: str | None = None
    weaknesses_summary: str | None = None
    improvement_keywords: list[str] = Field(default_factory=list)
    report_s3_key: str | None = None
