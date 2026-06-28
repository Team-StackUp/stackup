"""STT 환각(hallucination) 제거.

Whisper/Deepgram 등 STT 모델은 발화 끝 무음·잡음 구간에서, 학습 데이터(유튜브/방송)
에 흔한 정형 문구를 환각으로 덧붙인다. 대표적으로 방송 클로징("MBC 뉴스 OOO입니다"),
구독 유도("구독과 좋아요 부탁드립니다"), 시청 인사("시청해주셔서 감사합니다"),
자막 크레딧 등이다. 이런 문구는 IT 면접 답변에 정상적으로 등장하지 않으므로,
고유 패턴만 보수적으로 제거한다.

보수성 원칙: 면접 답변에 정상적으로 나올 수 있는 표현(단독 "감사합니다",
"뉴스 앱" 같은 일반 명사구)은 건드리지 않는다. 방송사명·시청/구독 유도 등
환각 고유 신호가 있을 때만 제거한다.
"""

from __future__ import annotations

import re
from dataclasses import replace

from ai_server.voice.stt.base import TranscriptionResult

# 방송사 클로징: "<방송사> 뉴스 <이름>입니다" (예: "MBC 뉴스 김재경입니다").
_BROADCASTERS = (
    r"MBC|KBS|SBS|YTN|JTBC|MBN|TBS|OBS|EBS|채널\s*A|TV\s*조선|연합뉴스(?:TV)?|"
    r"뉴스데스크|뉴스룸"
)

_HALLUCINATION_PATTERNS = [
    re.compile(p, re.IGNORECASE)
    for p in (
        # 방송 뉴스 클로징
        rf"(?:{_BROADCASTERS})\s*뉴스\s*[가-힣]{{2,5}}\s*입니다",
        rf"이상\s*(?:{_BROADCASTERS})?\s*뉴스(?:였|입니다|였습니다)",
        # 시청 인사
        r"시청\s*해\s*주(?:셔서|시고)\s*감사합니다",
        r"시청해주셔서\s*감사합니다",
        r"오늘도\s*시청해\s*주셔서[^.\n]*",
        r"영상\s*을?\s*시청해\s*주셔서[^.\n]*",
        # 구독/좋아요 유도
        r"구독\s*(?:과|,|및|\s)*\s*좋아요[^.\n]*(?:부탁\s*드립니다|눌러\s*주세요|부탁해요)?",
        r"좋아요\s*(?:와|,|및|\s)*\s*구독[^.\n]*(?:부탁\s*드립니다|눌러\s*주세요)?",
        r"구독\s*(?:과|,)?\s*알림\s*설정[^.\n]*",
        # 다음 영상/편 인사
        r"다음\s*(?:영상|시간|편|시간에)\s*(?:에서?)?\s*(?:뵙겠습니다|만나요|만나뵙겠습니다|봬요|뵐게요)",
        # 자막 크레딧
        r"(?:한글\s*)?자막\s*(?:제공|by|:)[^.\n]*",
        # 영어 환각
        r"thank\s+you\s+(?:so\s+much\s+|very\s+much\s+)?for\s+watching",
        r"thanks\s+for\s+watching",
        r"please\s+(?:like\s+and\s+|don'?t\s+forget\s+to\s+)?subscribe",
        r"like\s+and\s+subscribe",
    )
]

# 실제 내용(글자/숫자) 보유 여부 — 환각 제거 후 문장부호만 남은 segment 판별.
_HAS_CONTENT = re.compile(r"[0-9A-Za-z가-힣]")

# 환각 제거 후 정돈: 중복 공백, 문장부호 앞 공백, 연속 문장부호.
_CLEANUP_SPACE = re.compile(r"[ \t]{2,}")
_CLEANUP_PUNCT_SPACE = re.compile(r"\s+([.,!?])")
_CLEANUP_DUP_PUNCT = re.compile(r"([.?!])(?:\s*[.?!])+")


def strip_stt_hallucinations(text: str) -> str:
    """텍스트에서 알려진 STT 환각 문구를 제거하고 잔여 공백/문장부호를 정돈한다."""
    if not text:
        return text
    cleaned = text
    for pattern in _HALLUCINATION_PATTERNS:
        cleaned = pattern.sub(" ", cleaned)
    if cleaned == text:
        return text
    cleaned = _CLEANUP_SPACE.sub(" ", cleaned)
    cleaned = _CLEANUP_PUNCT_SPACE.sub(r"\1", cleaned)
    cleaned = _CLEANUP_DUP_PUNCT.sub(r"\1", cleaned)
    return cleaned.strip()


def sanitize_transcription(result: TranscriptionResult) -> TranscriptionResult:
    """TranscriptionResult 의 본문과 segment 에서 환각을 제거한다.

    - 본문 text: 환각 문구 제거.
    - segment: 환각만으로 이루어진 segment 는 제거(무음 구간 blip), 일부만 환각인
      segment 는 정화된 텍스트로 교체. segment 제거 시 무음(silence)·발음 메트릭이
      실제 무음을 반영하도록 자연스럽게 보정된다.
    """
    cleaned_text = strip_stt_hallucinations(result.text)
    cleaned_segments = []
    for seg in result.segments:
        seg_text = strip_stt_hallucinations(seg.text)
        # 환각만으로 이루어져 문장부호/공백만 남은 segment 는 제거.
        if not _HAS_CONTENT.search(seg_text):
            continue
        cleaned_segments.append(
            seg if seg_text == seg.text else replace(seg, text=seg_text)
        )
    if cleaned_text == result.text and len(cleaned_segments) == len(result.segments):
        return result
    return replace(result, text=cleaned_text, segments=cleaned_segments)
