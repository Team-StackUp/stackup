from __future__ import annotations

from ai_server.chain.feedback_generation_chain import _filter_highlights


def test_keeps_only_verbatim_substrings():
    strengths = "지원자는 동시성 제어를 명확히 이해하고 있습니다."
    weaknesses = "다만 인덱스 설계 근거가 부족합니다."
    # 본문에 그대로 있는 구절만 통과, 지어낸/바꿔쓴 구절은 탈락.
    out = _filter_highlights(
        ["동시성 제어를 명확히 이해", "인덱스 설계 근거가 부족", "존재하지 않는 칭찬"],
        strengths,
        weaknesses,
    )
    assert out == ["동시성 제어를 명확히 이해", "인덱스 설계 근거가 부족"]


def test_dedup_and_cap():
    body = "가가 나나 다다 라라 마마 바바 사사 가가"
    out = _filter_highlights(
        ["가가", "가가", "나나", "다다", "라라", "마마", "바바", "사사"], body, None, cap=6
    )
    assert out == ["가가", "나나", "다다", "라라", "마마", "바바"]  # 중복 제거 + 6개 상한


def test_empty_when_no_match_or_no_body():
    assert _filter_highlights(["무엇이든"], None, None) == []
    assert _filter_highlights([], "본문", "본문") == []
    assert _filter_highlights(["x"], "전혀 다른 본문", "") == []
