from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import AsyncIterator
from dataclasses import dataclass

from ai_server.voice.stt.base import TranscriptionResult


@dataclass(frozen=True)
class LiveTranscriptEvent:
    text: str
    is_final: bool  # 발화(utterance) 확정 조각인지
    speech_final: bool  # 발화 끝(턴 종료 신호)


class LiveSttSession(ABC):
    """단일 스트리밍 STT 세션. push()로 오디오 청크 투입, events()로 부분/최종 수신."""

    @abstractmethod
    async def start(self) -> None: ...

    @abstractmethod
    async def push(self, chunk: bytes) -> None: ...

    @abstractmethod
    async def finish(self) -> None:
        """오디오 입력 종료 신호(클라 stop). 남은 최종 이벤트 flush 유도."""

    @abstractmethod
    def events(self) -> AsyncIterator[LiveTranscriptEvent]: ...

    @abstractmethod
    async def result(self) -> TranscriptionResult:
        """세션 종료 후 누적 최종 transcript + segment(메트릭 계산용)."""

    @abstractmethod
    async def close(self) -> None: ...


class LiveSttProvider(ABC):
    model_name: str = "live-stt"

    @abstractmethod
    def open_session(
        self, *, content_type: str, language: str | None
    ) -> LiveSttSession: ...
