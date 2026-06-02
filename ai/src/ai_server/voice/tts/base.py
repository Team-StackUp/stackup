from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass


class TtsError(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass(frozen=True)
class TtsResult:
    audio_bytes: bytes
    duration_sec: float | None
    content_type: str = "audio/mpeg"


class TtsProvider(ABC):
    model_name: str = "tts"

    @abstractmethod
    async def synthesize(self, text: str, *, voice: str) -> TtsResult: ...
