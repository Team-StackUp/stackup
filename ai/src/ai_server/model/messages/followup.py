from typing import Literal

from pydantic import BaseModel, Field

from ai_server.model._config import camel_config

InterviewMode = Literal["PERSONALITY", "TECHNICAL", "INTEGRATED"]


class GenerateFollowupRequest(BaseModel):
    """Core 가 답변 commit 후 발행."""
    model_config = camel_config()

    session_id: int
    parent_message_id: int                  # 직전 질문 메시지 ID
    answer_message_id: int                  # 답변 메시지 ID
    previous_question: str
    answer_text: str
    mode: InterviewMode
    job_category: Literal["FRONTEND", "BACKEND", "INFRA", "DBA"]
    context_document_ids: list[int] = Field(default_factory=list)


class AnswerEvaluation(BaseModel):
    """답변 평가 (US-19). LLM 이 specificity/logic/structure 채움."""
    model_config = camel_config()

    specificity: float                      # 0~5
    logic: float                            # 0~5
    structure: Literal["FULL_STAR", "PARTIAL_STAR", "NONE"]


class FollowupCallbackPayload(BaseModel):
    model_config = camel_config()

    session_id: int
    kind: Literal["FOLLOWUP"] = "FOLLOWUP"
    parent_message_id: int
    followup_question: str
    answer_evaluation: AnswerEvaluation | None = None
