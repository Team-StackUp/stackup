from __future__ import annotations

from dataclasses import dataclass, field
from typing import Protocol


@dataclass(frozen=True)
class TranscriptionSegment:
    start_sec: float
    end_sec: float
    text: str
    avg_logprob: float | None = None


@dataclass(frozen=True)
class TranscriptionResult:
    text: str
    language: str | None
    duration_sec: float | None
    segments: list[TranscriptionSegment] = field(default_factory=list)


class SttProvider(Protocol):
    @property
    def model_name(self) -> str | None: ...

    async def transcribe(
        self,
        *,
        audio_bytes: bytes,
        content_type: str,
        hint: str | None = None,
    ) -> TranscriptionResult: ...


class SttError(Exception):
    def __init__(self, *, code: str, message: str, retriable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retriable = retriable
