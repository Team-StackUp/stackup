from __future__ import annotations

import structlog

from ai_server.voice.stt.base import TranscriptionResult, TranscriptionSegment

log = structlog.get_logger(__name__)


class MockSttProvider:
    """개발/테스트용. 실제 STT 안 함 — 더미 transcript + 단일 segment 반환."""

    def __init__(self, *, default_text: str = "(mock transcript)") -> None:
        self._default_text = default_text

    async def transcribe(
        self,
        *,
        audio_bytes: bytes,
        content_type: str,
        hint: str | None = None,
    ) -> TranscriptionResult:
        # 매우 단순한 길이 추정: 1초당 16KB 가정.
        estimated_duration = max(1.0, len(audio_bytes) / 16_000.0)
        text = self._default_text
        return TranscriptionResult(
            text=text,
            language="ko",
            duration_sec=estimated_duration,
            segments=[
                TranscriptionSegment(
                    start_sec=0.0,
                    end_sec=estimated_duration,
                    text=text,
                    avg_logprob=None,
                )
            ],
        )
