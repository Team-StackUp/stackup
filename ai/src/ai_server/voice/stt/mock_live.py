from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator

from ai_server.voice.stt.base import TranscriptionResult, TranscriptionSegment
from ai_server.voice.stt.live import (
    LiveSttProvider,
    LiveSttSession,
    LiveTranscriptEvent,
)


class _MockLiveSession(LiveSttSession):
    def __init__(self, script: list[str]) -> None:
        self._script = script or ["테스트 transcript"]
        self._queue: asyncio.Queue[LiveTranscriptEvent | None] = asyncio.Queue()
        self._pushes = 0
        self._final_text = self._script[-1]

    async def start(self) -> None:
        return None

    async def push(self, chunk: bytes) -> None:
        # 청크마다 부분 transcript 한 조각 방출 (스크립트 순서대로).
        idx = min(self._pushes, len(self._script) - 1)
        await self._queue.put(
            LiveTranscriptEvent(
                text=self._script[idx], is_final=False, speech_final=False
            )
        )
        self._pushes += 1

    async def finish(self) -> None:
        await self._queue.put(
            LiveTranscriptEvent(text=self._final_text, is_final=True, speech_final=True)
        )
        await self._queue.put(None)  # 종료 센티넬

    async def events(self) -> AsyncIterator[LiveTranscriptEvent]:
        while True:
            ev = await self._queue.get()
            if ev is None:
                return
            yield ev

    async def result(self) -> TranscriptionResult:
        return TranscriptionResult(
            text=self._final_text,
            language="ko",
            duration_sec=float(max(1, self._pushes)),
            segments=[
                TranscriptionSegment(
                    start_sec=0.0,
                    end_sec=float(max(1, self._pushes)),
                    text=self._final_text,
                    avg_logprob=-0.1,
                )
            ],
        )

    async def close(self) -> None:
        return None


class MockLiveSttProvider(LiveSttProvider):
    model_name = "mock-live"

    def __init__(self, script: list[str] | None = None) -> None:
        self._script = script or ["부분", "부분 transcript", "부분 transcript 완성"]

    def open_session(
        self, *, content_type: str, language: str | None
    ) -> LiveSttSession:
        return _MockLiveSession(self._script)
