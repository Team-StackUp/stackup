from __future__ import annotations

import pytest

from ai_server.voice.stt.base import TranscriptionResult, TranscriptionSegment
from ai_server.voice.stt.sanitize import (
    sanitize_transcription,
    strip_stt_hallucinations,
)


@pytest.mark.parametrize(
    "raw, expected",
    [
        # 실제 세션 82 케이스: 답변 끝 무음에서 방송 클로징 환각.
        (
            "그런 식으로 타임아웃을 잡았던 것이 생각납니다. MBC 뉴스 김재경입니다.",
            "그런 식으로 타임아웃을 잡았던 것이 생각납니다.",
        ),
        ("정리하겠습니다. KBS 뉴스 홍길동입니다", "정리하겠습니다."),
        ("답변 마치겠습니다. 시청해주셔서 감사합니다.", "답변 마치겠습니다."),
        (
            "이상입니다. 구독과 좋아요 부탁드립니다.",
            "이상입니다.",
        ),
        ("좋은 경험이었습니다. 다음 영상에서 만나요", "좋은 경험이었습니다."),
        ("That is my answer. Thanks for watching!", "That is my answer."),
        # CTA 동사가 있는 진짜 구독 유도 환각은 제거.
        ("이상입니다. 구독과 좋아요 눌러주세요.", "이상입니다."),
        ("정리하겠습니다. 구독 부탁드립니다.", "정리하겠습니다."),
        # 누락 보강(봐주셔서/오늘 영상은 여기까지/도움이 되셨다면)
        ("답변 마칩니다. 끝까지 봐주셔서 감사합니다.", "답변 마칩니다."),
        ("이상입니다. 오늘 영상은 여기까지입니다.", "이상입니다."),
    ],
)
def test_strips_known_hallucinations(raw: str, expected: str) -> None:
    assert strip_stt_hallucinations(raw) == expected


@pytest.mark.parametrize(
    "text",
    [
        "감사합니다.",  # 단독 인사는 정상 답변 — 건드리지 않는다.
        "뉴스 피드를 보여주는 앱을 만들었습니다.",  # '뉴스' 일반 명사
        "구독자 수를 늘리는 추천 알고리즘을 구현했습니다.",  # '구독' 일반 명사
        "다음 영상 처리 파이프라인을 설계했습니다.",  # '다음 영상' 일반 명사구
        "Explain 으로 실행 계획을 분석했습니다.",
        # 회의적 리뷰가 잡은 오탐 회귀 케이스 — CTA 동사 없는 기능 설명은 보존.
        "유튜브 클론에서 구독과 좋아요 기능을 리액트로 구현했습니다.",
        "좋아요와 구독 수를 집계하는 배치를 만들었습니다.",
        "푸시 알림 설정 화면을 구현했습니다.",
        "구독 모델 기반 결제 시스템을 설계했습니다.",
    ],
)
def test_keeps_legitimate_answers(text: str) -> None:
    assert strip_stt_hallucinations(text) == text


def test_empty_and_none_safe() -> None:
    assert strip_stt_hallucinations("") == ""
    assert strip_stt_hallucinations("   ") == "   "


def test_sanitize_transcription_drops_hallucination_segment() -> None:
    result = TranscriptionResult(
        text="타임아웃을 잡았습니다. MBC 뉴스 김재경입니다.",
        language="ko",
        duration_sec=30.0,
        segments=[
            TranscriptionSegment(start_sec=0.0, end_sec=4.0, text="타임아웃을 잡았습니다."),
            TranscriptionSegment(
                start_sec=29.0, end_sec=30.0, text="MBC 뉴스 김재경입니다."
            ),
        ],
    )

    cleaned = sanitize_transcription(result)

    assert cleaned.text == "타임아웃을 잡았습니다."
    assert len(cleaned.segments) == 1
    assert cleaned.segments[0].text == "타임아웃을 잡았습니다."
    # 환각이 없는 결과는 동일 객체를 그대로 반환(불필요한 재할당 방지).
    clean_in = TranscriptionResult(text="정상 답변입니다.", language="ko", duration_sec=2.0, segments=[])
    assert sanitize_transcription(clean_in) is clean_in
