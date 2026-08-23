"""feedback_report 렌더러 단위 테스트 — LLM 미호출 순수 함수."""

from ai_server.chain.feedback_generation_chain import FeedbackResult
from ai_server.chain.feedback_report import render_feedback_report
from ai_server.model.messages.feedback import (
    AnswerCoachingItem,
    GenerateFeedbackRequest,
    PanelBreakdownItem,
)


def _request(**overrides) -> GenerateFeedbackRequest:
    base = {
        "session_id": 50,
        "mode": "TECHNICAL",
        "job_category": "BACKEND",
        "total_question_count": 2,
        "end_reason": "MAX_QUESTIONS_REACHED",
        "messages": [
            {
                "id": 100,
                "sequence_number": 1,
                "role": "INTERVIEWER",
                "content": "트랜잭션 ACID 를 설명해 주세요.",
            },
            {
                "id": 101,
                "sequence_number": 2,
                "role": "INTERVIEWEE",
                "content": "**사용자답변원문** — 원자성 일관성",
                "parent_message_id": 100,
            },
        ],
    }
    base.update(overrides)
    return GenerateFeedbackRequest.model_validate(base)


def _result(**overrides) -> FeedbackResult:
    base = dict(
        overall_score=85.0,
        technical_accuracy=82.5,
        logic_score=88.0,
        communication_score=None,
        strengths_summary="ACID 4요소를 명확히 답변.",
        weaknesses_summary="구체적 사례 부족.",
        improvement_keywords=["MVCC"],
        study_plan=["격리 수준 4단계 정리"],
        highlights=["명확히 답변"],
        panel_breakdown=[
            PanelBreakdownItem(
                evaluator="기술",
                dimension="기술 정확도",
                score=82.0,
                strength="개념 정확",
                weakness="사례 부족",
                detail="정의는 정확했으나 실무 예시가 없었다.",
                score_rationale="예시 부재 감점",
            )
        ],
    )
    base.update(overrides)
    return FeedbackResult(**base)


def test_renders_full_report_sections():
    md = render_feedback_report(
        req=_request(
            voice_analysis_summary={
                "analyzed_message_count": 2,
                "average_speaking_rate_wpm": 132.5,
                "total_silence_duration_sec": 4.2,
                "filler_word_counts": {"음": 3, "like": 0},
            }
        ),
        result=_result(),
        answer_coaching=[
            AnswerCoachingItem(
                message_id=101,
                model_answer="- **원자성**: 전부 반영되거나 전부 취소",
                answer_rewrite="ACID 각 요소를 예시와 함께 설명합니다.",
                coaching_comment="정의에 실무 예시를 더하세요.",
            )
        ],
    )

    assert md.startswith("# 면접 피드백 리포트\n")
    assert "- 면접 유형: 기술 면접 · 백엔드" in md
    assert "- 종료 사유: 질문 수 도달" in md
    # 점수 표: 85.0 → "85", 82.5 는 유지, None 은 —.
    assert "| 종합 | 85 |" in md
    assert "| 기술 정확도 | 82.5 |" in md
    assert "| 전달력 | — |" in md
    # 패널.
    assert "### 기술 — 기술 정확도 (82점)" in md
    assert "> 점수 근거: 예시 부재 감점" in md
    # 요약·키워드·플랜·하이라이트.
    assert "## 강점 요약" in md and "ACID 4요소를 명확히 답변." in md
    assert "- MVCC" in md
    assert "1. 격리 수준 4단계 정리" in md
    assert "> 명확히 답변" in md
    # 코칭: 질문 텍스트는 싣고(GFM 계약 필드 원문 유지), 사용자 답변 원문은 싣지 않는다.
    assert "### Q1. 트랜잭션 ACID 를 설명해 주세요." in md
    assert "- **원자성**: 전부 반영되거나 전부 취소" in md
    assert "사용자답변원문" not in md
    # 음성 요약: 0회 간투어는 제외.
    assert "- 평균 말속도: 132 WPM" in md
    assert "- 간투어: 음 3회" in md
    assert "like" not in md


def test_minimal_result_omits_empty_sections():
    md = render_feedback_report(
        req=_request(total_question_count=None, end_reason=None, messages=[]),
        result=FeedbackResult(),
        answer_coaching=[],
    )

    assert "# 면접 피드백 리포트" in md
    assert "| 종합 | — |" in md  # 점수 표는 항상 노출(— 표기)
    assert "## 평가위원 패널" not in md
    assert "## 답변별 코칭" not in md
    assert "## 음성 전달력 요약" not in md
    assert "- 질문 수:" not in md


def test_coaching_question_fallback_when_message_unknown():
    md = render_feedback_report(
        req=_request(),
        result=_result(),
        answer_coaching=[
            AnswerCoachingItem(message_id=999, coaching_comment="한 줄 코칭")
        ],
    )
    assert "### Q. (질문 미확인)" in md
    assert "**코칭 한 줄**: 한 줄 코칭" in md
