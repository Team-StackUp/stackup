from typing import Literal

from pydantic import BaseModel, Field

from ai_server.model._config import camel_config

InterviewMode = Literal["PERSONALITY", "TECHNICAL", "INTEGRATED"]


class HistoryItem(BaseModel):
    """대화 히스토리 한 줄 (중복 질문 회피용)."""

    model_config = camel_config()

    role: str  # INTERVIEWER | INTERVIEWEE | SYSTEM
    content: str


class GenerateFollowupRequest(BaseModel):
    """Core 가 답변 commit 후 발행."""

    model_config = camel_config()

    session_id: int
    parent_message_id: int  # 직전 질문 메시지 ID
    answer_message_id: int  # 답변 메시지 ID
    previous_question: str
    answer_text: str
    mode: InterviewMode
    job_category: Literal["FRONTEND", "BACKEND", "INFRA", "DBA"]
    context_document_ids: list[int] = Field(default_factory=list)
    parent_category: str | None = None  # 직전 질문 카테고리 (루브릭 선택)
    history: list[HistoryItem] = Field(default_factory=list)  # 최근 대화 (중복 회피)


class AnswerEvaluation(BaseModel):
    """답변 평가 (US-19). LLM 이 specificity/logic/structure/correctness 채움."""

    model_config = camel_config()

    specificity: float  # 0~5
    logic: float  # 0~5
    structure: Literal["FULL_STAR", "PARTIAL_STAR", "NONE"]
    # 답변이 자료(RAG)와 사실적으로 맞는지. 검색 컨텍스트 없으면 null.
    correctness: float | None = None  # 0~5


class FollowupCallbackPayload(BaseModel):
    model_config = camel_config()

    session_id: int
    kind: Literal["FOLLOWUP"] = "FOLLOWUP"
    parent_message_id: int
    answer_message_id: int  # 평가가 달릴 답변 메시지 (Core 가 평가 영속에 사용)
    followup_question: str
    answer_evaluation: AnswerEvaluation | None = None
