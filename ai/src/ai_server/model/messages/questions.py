from typing import Literal

from pydantic import BaseModel

from ai_server.model._config import camel_config

InterviewMode = Literal["PERSONALITY", "TECHNICAL", "INTEGRATED"]
JobCategory = Literal["FRONTEND", "BACKEND", "INFRA", "DBA"]
QuestionCategory = Literal[
    "CS_FUNDAMENTAL",
    "PROJECT_DEEP_DIVE",
    "TECH_CHOICE",
    "BEHAVIORAL",
]
CallbackKind = Literal["POOL", "FOLLOWUP"]


class DocumentContext(BaseModel):
    """Core 가 generate.questions envelope 에 담아 보내는 문서 컨텍스트."""
    model_config = camel_config()

    document_id: int
    source_type: str  # RESUME | REPOSITORY | WEB
    summary: str | None = None
    tech_stack: list[str] = []
    markdown: str | None = None


class GenerateQuestionsRequest(BaseModel):
    model_config = camel_config()

    session_id: int
    mode: InterviewMode
    job_category: JobCategory
    documents: list[DocumentContext] = []
    max_questions: int = 10


class GeneratedQuestion(BaseModel):
    """LLM 응답 단위. category 는 Spring 측 enum 과 동기."""
    model_config = camel_config()

    category: QuestionCategory
    question: str


class QuestionPoolCallbackPayload(BaseModel):
    model_config = camel_config()

    session_id: int
    kind: CallbackKind = "POOL"
    questions: list[GeneratedQuestion] = []
