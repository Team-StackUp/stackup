from typing import Literal

from pydantic import BaseModel, Field

from ai_server.model._config import camel_config
from ai_server.model.messages.questions import GenerationStatus

InterviewMode = Literal["PERSONALITY", "TECHNICAL", "INTEGRATED", "JOB_TAILORED"]


class MessageEvaluation(BaseModel):
    """답변별 평가 (꼬리질문 단계에서 채점·영속된 값). 피드백에서 롤업 재활용."""

    model_config = camel_config()

    specificity: float | None = None
    logic: float | None = None
    structure: str | None = None
    correctness: float | None = None


class FeedbackMessageItem(BaseModel):
    """세션 시퀀스 한 줄 (Core 가 통째로 동봉)."""

    model_config = camel_config()

    id: int
    sequence_number: int
    role: Literal["INTERVIEWER", "INTERVIEWEE", "SYSTEM"]
    content: str
    parent_message_id: int | None = None
    # 질문 유형(SELF_INTRODUCTION/CS_FUNDAMENTAL/…). 첫인상 평가에서 자기소개 식별에 사용.
    category: str | None = None
    # 질문(INTERVIEWER) 메시지에만 채워짐. 답변이 짚어야 할 핵심(평가 기준).
    expected_signal: str | None = None
    # 답변(INTERVIEWEE) 메시지에만 채워짐. 피드백 종합 채점의 근거.
    evaluation: MessageEvaluation | None = None


class VoiceAnalysisSummary(BaseModel):
    """Aggregated voice metrics supplied by Core in generate.feedback."""

    model_config = camel_config()

    analyzed_message_count: int | None = None
    average_speaking_rate_wpm: float | None = None
    total_silence_duration_sec: float | None = None
    filler_word_counts: dict[str, int] = Field(default_factory=dict)


class GenerateFeedbackRequest(BaseModel):
    """Core 가 세션 COMPLETED commit 후 발행."""

    model_config = camel_config()

    session_id: int
    mode: InterviewMode
    job_category: Literal["FRONTEND", "BACKEND", "INFRA", "DBA"]
    total_question_count: int | None = None
    # 종료 사유(USER_REQUEST/MAX_QUESTIONS_REACHED/POOL_EXHAUSTED 등). 프롬프트 컨텍스트로만
    # 쓰이므로 자유 문자열로 둬 Core 가 사유를 추가해도 피드백 파싱이 깨지지 않게 한다.
    end_reason: str | None = None
    messages: list[FeedbackMessageItem] = Field(default_factory=list)
    context_document_ids: list[int] = Field(default_factory=list)
    voice_analysis_summary: VoiceAnalysisSummary | None = None
    # 자기소개 답변 단독의 음성 메트릭. 첫인상 평가에서 세션 평균 대신 사용. 없으면 None.
    self_intro_voice_analysis: VoiceAnalysisSummary | None = None
    # 다직군 패널 가중: 사용된 일반질문의 직군별 개수. 비면 단일 직군 평가.
    domain_question_counts: dict[str, int] = Field(default_factory=dict)
    # 직무 맞춤(JOB_TAILORED) 모드 전용. 회사명 + 채용공고(JD). '직무 적합도' 평가의 근거.
    target_company_name: str | None = None
    target_job_description: str | None = None
    # 시도 상관관계(F5). Core 가 발행마다 발급 — 콜백에 그대로 에코해 Core 가 대체된
    # 이전 시도의 지연 FAILED 를 구분할 수 있게 한다. 구버전 요청은 None.
    attempt_id: str | None = None


class PanelBreakdownItem(BaseModel):
    """멀티 면접관 패널의 평가위원 1명 결과(분해 표시·저장용)."""

    model_config = camel_config()

    evaluator: str  # 라벨: 기술/인성/논리/전달
    dimension: str  # 평가축 이름
    score: float | None = None
    strength: str | None = None
    weakness: str | None = None
    # 구체적 근거·예시(답변 인용)를 담은 2~4문장 상세 평가.
    detail: str | None = None
    # 이 점수를 준 근거(감점/가점 요인) 간단 설명.
    score_rationale: str | None = None


class AnswerCoachingItem(BaseModel):
    """질문별 복기 1건 (AI → Core). 해당 답변(INTERVIEWEE) 메시지에 기록된다."""

    model_config = camel_config()

    message_id: int
    model_answer: str | None = None
    answer_rewrite: str | None = None
    coaching_comment: str | None = None


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
    # 패널을 통합한 학습 방향/다음 단계 액션 아이템.
    study_plan: list[str] = Field(default_factory=list)
    # 강조 표시용 핵심 구절(강점·개선 본문에서 발췌). 프론트가 부분 문자열 매칭해 하이라이트.
    highlights: list[str] = Field(default_factory=list)
    # 평가위원별 분해(패널). 비어 있으면 단일/레거시 경로.
    panel_breakdown: list[PanelBreakdownItem] = Field(default_factory=list)
    # 질문별 복기(답변 메시지별 모범 답안·리라이트·코칭). 비면 복기 없음.
    answer_coaching: list[AnswerCoachingItem] = Field(default_factory=list)
    report_s3_key: str | None = None
    # 실패 신호 (questions/analysis 콜백과 동일 규약). status 미명시(구버전)는 OK 취급.
    status: GenerationStatus = "OK"
    error_code: str | None = None
    error_message: str | None = None
    retriable: bool | None = None
    # 요청의 attemptId 에코(F5) — Core 가 stale FAILED(대체된 이전 시도)를 걸러내는 근거.
    attempt_id: str | None = None
