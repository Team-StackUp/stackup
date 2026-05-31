from __future__ import annotations

import math

from ai_server.voice.analysis.metrics import analyze
from ai_server.voice.stt.base import TranscriptionResult, TranscriptionSegment

FILLER_PATTERN = r"(?:음+|어+|그+|아+)"


def test_word_count_and_wpm():
    result = TranscriptionResult(
        text="저는 백엔드 개발자 입니다",
        language="ko",
        duration_sec=60.0,
        segments=[
            TranscriptionSegment(start_sec=0.0, end_sec=60.0, text="저는 백엔드 개발자 입니다",
                                 avg_logprob=-0.2),
        ],
    )
    m = analyze(result, filler_pattern=FILLER_PATTERN)
    # 4 words / 60s * 60 = 4 wpm (어절 기반)
    assert m.speaking_rate_wpm == 4.0
    # logprob exp(-0.2) ≈ 0.8187
    assert m.pronunciation_accuracy is not None
    assert abs(m.pronunciation_accuracy - math.exp(-0.2)) < 1e-3


def test_filler_count_normalized():
    result = TranscriptionResult(
        text="음 그게 어 음음 어어어 그러니까",
        language="ko",
        duration_sec=10.0,
        segments=[],
    )
    m = analyze(result, filler_pattern=FILLER_PATTERN)
    # 음(첫 글자) 2회, 어 2회, 그 2회 — 매칭은 음/어/그/아 패턴
    assert m.filler_word_counts.get("음", 0) >= 1
    assert m.filler_word_counts.get("어", 0) >= 1


def test_silence_includes_gaps_and_padding():
    result = TranscriptionResult(
        text="안녕 반갑",
        language="ko",
        duration_sec=10.0,
        segments=[
            TranscriptionSegment(start_sec=1.0, end_sec=3.0, text="안녕"),
            TranscriptionSegment(start_sec=5.0, end_sec=7.0, text="반갑"),
        ],
    )
    m = analyze(result, filler_pattern=FILLER_PATTERN)
    # 앞 1s + 중간 2s + 뒤 3s = 6s
    assert m.silence_duration_sec == 6.0


def test_empty_segments_returns_none_silence_and_accuracy():
    result = TranscriptionResult(text="", language=None, duration_sec=None, segments=[])
    m = analyze(result, filler_pattern=FILLER_PATTERN)
    assert m.speaking_rate_wpm is None
    assert m.silence_duration_sec is None
    assert m.pronunciation_accuracy is None
