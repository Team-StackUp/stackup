from __future__ import annotations

import pytest

from ai_server.messaging.consumers.feedback_consumer import (
    _collect_coachable_pairs,
    _is_short_confirmation,
)
from ai_server.model.messages.feedback import FeedbackMessageItem


@pytest.mark.parametrize(
    "text",
    [
        "네 맞습니다.",
        "네, 맞습니다",
        "아뇨 그건 아닙니다.",
        "아니요.",
        "예 그렇습니다.",
        "맞습니다.",
    ],
)
def test_detects_short_confirmation(text: str) -> None:
    assert _is_short_confirmation(text) is True


@pytest.mark.parametrize(
    "text",
    [
        # 충분히 긴 정정/부연은 일반 답변 — 코칭 대상으로 남긴다.
        "아뇨, 그 부분은 캐시 무효화 전략이 달라서 조인 대신 임시 테이블을 썼습니다.",
        # 짧지만 확인형 단답이 아닌 실질 답변.
        "시간 복잡도는 O(n log n) 입니다.",
        "인덱스를 풀스캔하지 않도록 복합 인덱스를 새로 만들었습니다.",
        "",
    ],
)
def test_keeps_substantive_answers(text: str) -> None:
    assert _is_short_confirmation(text) is False


def test_coaching_pairs_skip_short_confirmation() -> None:
    msgs = [
        FeedbackMessageItem(
            id=1,
            sequence_number=1,
            role="INTERVIEWER",
            content="그럼 인덱스를 직접 만드셨나요?",
            category="TECH_CHOICE",
        ),
        FeedbackMessageItem(
            id=2,
            sequence_number=2,
            role="INTERVIEWEE",
            content="네 맞습니다.",
            parent_message_id=1,
        ),
        FeedbackMessageItem(
            id=3,
            sequence_number=3,
            role="INTERVIEWER",
            content="어떤 컬럼으로 복합 인덱스를 구성했나요?",
            category="TECH_CHOICE",
        ),
        FeedbackMessageItem(
            id=4,
            sequence_number=4,
            role="INTERVIEWEE",
            content="조회 조건인 user_id 와 created_at 으로 복합 인덱스를 잡았습니다.",
            parent_message_id=3,
        ),
    ]
    pairs = _collect_coachable_pairs(msgs)
    answer_ids = [a.id for _, a in pairs]
    assert answer_ids == [4]  # 단답(2)은 제외, 실질 답변(4)만 코칭
