from __future__ import annotations

from ai_server.messaging.consumers.feedback_consumer import _session_rag_query
from ai_server.model.messages.feedback import FeedbackMessageItem


def _msg(mid: int, role: str, content: str, seq: int | None = None) -> FeedbackMessageItem:
    return FeedbackMessageItem(id=mid, sequence_number=seq or mid, role=role, content=content)


def test_joins_substantive_answers_excludes_short_confirmations() -> None:
    msgs = [
        _msg(1, "INTERVIEWER", "인덱스를 어떻게 튜닝했나요?"),
        _msg(2, "INTERVIEWEE", "복합 인덱스를 user_id, created_at 으로 잡아 풀스캔을 제거했습니다."),
        _msg(3, "INTERVIEWER", "그럼 커버링 인덱스였나요?"),
        _msg(4, "INTERVIEWEE", "네 맞습니다."),  # 짧은 확인 → 제외
        _msg(5, "INTERVIEWER", "캐시는요?"),
        _msg(6, "INTERVIEWEE", "Redis 로 조회 캐시를 붙여 p95 를 40ms 로 낮췄습니다."),
    ]
    q = _session_rag_query(msgs)
    assert "복합 인덱스" in q
    assert "Redis" in q
    assert "네 맞습니다" not in q  # 짧은 확인 답변은 질의에서 빠진다


def test_not_only_last_answer() -> None:
    # 마지막 답변 하나만 쓰던 편향 제거 — 앞선 실질 답변도 질의에 포함.
    msgs = [
        _msg(1, "INTERVIEWER", "Q1"),
        _msg(2, "INTERVIEWEE", "쿼리 튜닝으로 75% 단축했습니다."),
        _msg(3, "INTERVIEWER", "Q2"),
        _msg(4, "INTERVIEWEE", "마지막으로 모니터링을 붙였습니다."),
    ]
    q = _session_rag_query(msgs)
    assert "쿼리 튜닝" in q and "모니터링" in q


def test_all_short_falls_back_to_last_answer() -> None:
    msgs = [
        _msg(1, "INTERVIEWER", "Q"),
        _msg(2, "INTERVIEWEE", "네 맞습니다."),
        _msg(3, "INTERVIEWER", "Q2"),
        _msg(4, "INTERVIEWEE", "아니요."),
    ]
    q = _session_rag_query(msgs)
    assert q == "아니요."  # 전부 확인형이면 마지막 답변 폴백


def test_no_interviewee_is_empty() -> None:
    msgs = [_msg(1, "INTERVIEWER", "Q"), _msg(2, "SYSTEM", "S")]
    assert _session_rag_query(msgs) == ""


def test_caps_length() -> None:
    long = "가" * 5000
    msgs = [_msg(1, "INTERVIEWEE", long)]
    assert len(_session_rag_query(msgs)) == 2000
