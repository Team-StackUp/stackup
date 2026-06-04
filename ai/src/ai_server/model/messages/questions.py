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
    # 직군 다중 선택. 첫 항목이 대표 직군.
    job_categories: list[JobCategory] = []
    documents: list[DocumentContext] = []
    initial_question_count: int = 1
    max_questions: int = 10
    # 같은 유저가 최근 면접에서 받은 질문들. 의미 중복 회피에 사용.
    recent_questions: list[str] = []


class GeneratedQuestion(BaseModel):
    """LLM 응답 단위. category 는 Spring 측 enum 과 동기."""

    model_config = camel_config()

    category: QuestionCategory
    question: str
    # 질문이 근거한 자료 인용(PROJECT/TECH 는 필수). 라이브 화면에 힌트로 노출.
    target_evidence: str = ""
    # 좋은 답이 드러내야 할 것 — 내부 평가용. 라이브 비노출(정답 유출 방지).
    expected_signal: str = ""


class QuestionPoolCallbackPayload(BaseModel):
    model_config = camel_config()

    session_id: int
    kind: CallbackKind = "POOL"
    questions: list[GeneratedQuestion] = []
