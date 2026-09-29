from typing import Literal

from pydantic import BaseModel, Field

from ai_server.model._config import camel_config
from ai_server.model.messages.questions import GenerationStatus
from ai_server.model.messages.job_category import JobCategory

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
    followup_message_id: int  # Core 가 선INSERT 한 placeholder 질문 메시지 id
    previous_question: str
    answer_text: str
    mode: InterviewMode
    job_category: JobCategory
    context_document_ids: list[int] = Field(default_factory=list)
    parent_category: str | None = None  # 직전 질문 카테고리 (루브릭 선택)
    # 직전 질문이 기대하는 핵심(평가 관점). correctness/specificity 충족도 채점에 사용.
    parent_expected_signal: str | None = None
    history: list[HistoryItem] = Field(default_factory=list)  # 최근 대화 (중복 회피)


class AnswerEvaluation(BaseModel):
    """답변 평가 (US-19). LLM 이 specificity/logic/structure/correctness 채움.

    **모든 점수가 nullable 이다.** null 은 "0점"이 아니라 "채점 대상 아님"이고, 집계에서
    제외된다(`_build_score_basis` 의 `_mean`). 이 구분이 필요한 경우가 둘 있다:

    - 지원자가 정직하게 "모르겠습니다" 라고 한 경우(DONT_KNOW). 0점을 주면 **틀린 답을
      길게 늘어놓은 것과 같은 취급**이 된다. 면접에서 모름을 인정하는 것은 오답이 아니다.
    - 확인형 질문에 "네 맞습니다" 로 답한 경우. 평가할 내용이 없다.

    전에는 specificity/logic 이 required 였다. 프롬프트는 이미 이 두 경우에 null 을
    지시하고 있었는데, LLM 이 그대로 따르면 `model_validate_json` 이 터지고 그 예외를
    파서가 삼켜 **평가 전체가 조용히 사라졌다**(structure 까지). 진짜 파싱 실패와
    구분되지도 않았다.
    """

    model_config = camel_config()

    # null = 채점 대상 아님(모름/확인형 단답). 0 과 다르다.
    specificity: float | None = None  # 0~5
    logic: float | None = None  # 0~5
    structure: Literal["FULL_STAR", "PARTIAL_STAR", "NONE"]
    # 답변이 자료(RAG)와 사실적으로 맞는지. 검색 컨텍스트 없으면 null.
    correctness: float | None = None  # 0~5


class FollowupCallbackPayload(BaseModel):
    model_config = camel_config()

    session_id: int
    kind: Literal["FOLLOWUP"] = "FOLLOWUP"
    parent_message_id: int
    answer_message_id: int  # 평가가 달릴 답변 메시지 (Core 가 평가 영속에 사용)
    # 생성 실패(status=FAILED) 시 빈 문자열 — Core 는 status 를 먼저 확인해야 한다.
    followup_question: str = ""
    answer_evaluation: AnswerEvaluation | None = None
    # 답변 의도: NORMAL | DONT_KNOW | CLARIFICATION. Core 가 흐름 분기에 사용.
    answer_intent: str = "NORMAL"
    followup_message_id: int  # placeholder UPDATE 대상
    status: GenerationStatus = "OK"
    error_code: str | None = None
    error_message: str | None = None
    retriable: bool | None = None
