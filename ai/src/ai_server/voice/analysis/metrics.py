"""음성 답변 정량 분석.

입력: STT 결과 (TranscriptionResult).
출력: WPM, 간투어 카운트, 무음(silence) 합계, 평균 logprob(=pronunciation_accuracy 근사).

- WPM: word_count / duration_minutes. 한국어는 공백 기반 토큰이 단어 단위가 아니므로
  대화 답변에는 '어절(공백 단위)' 기준이 실용적. 영어/혼용도 같은 식이 자연스러움.
- 간투어: 한국어 정규식 (settings.voice_filler_pattern). 음/어/그/아 같은 반복형.
- 무음(silence_duration_sec): Whisper segment 들의 빈틈(start_(i+1) - end_i) 누적.
  앞/뒤 패딩(처음 segment 시작 전, 마지막 segment 종료 후 ~ duration_sec)도 포함.
- pronunciation_accuracy 근사: segment 평균 logprob → exp(logprob) 의 평균으로 0~1 근사.
  logprob 가 비어있는 segment 는 제외. 모든 segment 가 비면 None.
"""

from __future__ import annotations

import math
import re
from collections import Counter
from dataclasses import dataclass

from ai_server.voice.stt.base import TranscriptionResult


@dataclass(frozen=True)
class VoiceMetrics:
    speaking_rate_wpm: float | None
    silence_duration_sec: float | None
    filler_word_counts: dict[str, int]
    pronunciation_accuracy: float | None


def analyze(result: TranscriptionResult, *, filler_pattern: str) -> VoiceMetrics:
    text = (result.text or "").strip()
    duration = result.duration_sec

    word_count = _word_count(text)
    wpm: float | None = None
    if duration and duration > 0:
        wpm = round((word_count / duration) * 60.0, 2)

    fillers = _count_fillers(text, filler_pattern)
    silence = _silence_seconds(result)
    accuracy = _pronunciation_accuracy(result)

    return VoiceMetrics(
        speaking_rate_wpm=wpm,
        silence_duration_sec=silence,
        filler_word_counts=fillers,
        pronunciation_accuracy=accuracy,
    )


def _word_count(text: str) -> int:
    if not text:
        return 0
    return len([t for t in text.split() if t])


def _count_fillers(text: str, pattern: str) -> dict[str, int]:
    if not text:
        return {}
    try:
        regex = re.compile(pattern)
    except re.error:
        return {}
    counter: Counter[str] = Counter()
    for match in regex.findall(text):
        # 반복형(예: 음음음) 을 키로 normalize: 첫 글자만 유지.
        token = match[0] if match else match
        counter[token] += 1
    return dict(counter)


def _silence_seconds(result: TranscriptionResult) -> float | None:
    if not result.segments:
        return None
    silence = 0.0
    # 앞 패딩
    silence += max(0.0, result.segments[0].start_sec)
    # 사이 갭
    for prev, nxt in zip(result.segments, result.segments[1:]):
        silence += max(0.0, nxt.start_sec - prev.end_sec)
    # 뒤 패딩
    if result.duration_sec is not None:
        silence += max(0.0, result.duration_sec - result.segments[-1].end_sec)
    return round(silence, 3)


def _pronunciation_accuracy(result: TranscriptionResult) -> float | None:
    logprobs = [s.avg_logprob for s in result.segments if s.avg_logprob is not None]
    if not logprobs:
        return None
    # exp(logprob) ∈ (0, 1], 평균.
    probs = [math.exp(lp) for lp in logprobs]
    return round(sum(probs) / len(probs), 4)
