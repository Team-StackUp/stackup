from __future__ import annotations

import asyncio
import json
import math
from collections.abc import AsyncIterator
from typing import Any

import structlog
import websockets

from ai_server.voice.stt.base import TranscriptionResult, TranscriptionSegment
from ai_server.voice.stt.live import LiveSttProvider, LiveSttSession, LiveTranscriptEvent

log = structlog.get_logger(__name__)


class _DeepgramLiveSession(LiveSttSession):
    def __init__(self, *, api_key: str, url: str, model: str, language: str,
                 endpointing_ms: int, content_type: str) -> None:
        self._api_key = api_key
        self._url = url
        self._model = model
        self._language = language
        self._endpointing_ms = endpointing_ms
        self._content_type = content_type
        self._ws: Any | None = None
        self._queue: asyncio.Queue[LiveTranscriptEvent | None] = asyncio.Queue()
        self._recv_task: asyncio.Task[None] | None = None
        self._finals: list[str] = []
        self._segments: list[TranscriptionSegment] = []

    def _query(self) -> str:
        # encoding/sample_rate 는 컨테이너(webm/opus)면 Deepgram 이 자동 디코드.
        params = {
            "model": self._model,
            "language": self._language,
            "smart_format": "true",
            "interim_results": "true",
            # endpointing: N ms 무음 후 speech_final=true 로 턴 종료 신호 (Deepgram 공식).
            "endpointing": str(self._endpointing_ms),
            # utterance_end_ms: 별도 UtteranceEnd 메시지 backstop (interim_results 필요, 최소 1000).
            "utterance_end_ms": str(max(1000, self._endpointing_ms)),
            "vad_events": "true",
        }
        return self._url + "?" + "&".join(f"{k}={v}" for k, v in params.items())

    async def start(self) -> None:
        self._ws = await websockets.connect(
            self._query(),
            additional_headers={"Authorization": f"Token {self._api_key}"},
        )
        self._recv_task = asyncio.create_task(self._recv_loop())

    async def _recv_loop(self) -> None:
        assert self._ws is not None
        try:
            async for raw in self._ws:
                msg = json.loads(raw)
                mtype = msg.get("type")
                if mtype == "Results":
                    alt = (((msg.get("channel") or {}).get("alternatives") or [{}])[0])
                    text = str(alt.get("transcript") or "")
                    is_final = bool(msg.get("is_final"))
                    speech_final = bool(msg.get("speech_final"))
                    if text:
                        await self._queue.put(
                            LiveTranscriptEvent(
                                text=text, is_final=is_final, speech_final=speech_final
                            )
                        )
                    if is_final and text:
                        self._finals.append(text)
                        start = float(msg.get("start", 0.0))
                        dur = float(msg.get("duration", 0.0))
                        conf = alt.get("confidence")
                        self._segments.append(
                            TranscriptionSegment(
                                start_sec=start,
                                end_sec=start + dur,
                                text=text,
                                avg_logprob=_conf_to_logprob(conf),
                            )
                        )
                elif mtype == "UtteranceEnd":
                    await self._queue.put(
                        LiveTranscriptEvent(text="", is_final=True, speech_final=True)
                    )
        except Exception as exc:  # noqa: BLE001
            log.warn("deepgram_live.recv.closed", error=str(exc))
        finally:
            await self._queue.put(None)

    async def push(self, chunk: bytes) -> None:
        if self._ws is not None:
            await self._ws.send(chunk)

    async def finish(self) -> None:
        if self._ws is not None:
            await self._ws.send(json.dumps({"type": "CloseStream"}))

    async def events(self) -> AsyncIterator[LiveTranscriptEvent]:
        while True:
            ev = await self._queue.get()
            if ev is None:
                return
            yield ev

    async def result(self) -> TranscriptionResult:
        text = " ".join(self._finals).strip()
        dur = self._segments[-1].end_sec if self._segments else None
        return TranscriptionResult(
            text=text,
            language=self._language,
            duration_sec=dur,
            segments=list(self._segments),
        )

    async def close(self) -> None:
        if self._recv_task is not None:
            self._recv_task.cancel()
        if self._ws is not None:
            await self._ws.close()


class DeepgramLiveSttProvider(LiveSttProvider):
    model_name = "deepgram-live"

    def __init__(self, *, api_key: str, url: str, model: str, language: str,
                 endpointing_ms: int) -> None:
        if not api_key:
            raise ValueError("Deepgram API key 누락")
        self._api_key = api_key
        self._url = url
        self._model = model
        self._language = language
        self._endpointing_ms = endpointing_ms

    def open_session(self, *, content_type: str, language: str | None) -> LiveSttSession:
        return _DeepgramLiveSession(
            api_key=self._api_key,
            url=self._url,
            model=self._model,
            language=language or self._language,
            endpointing_ms=self._endpointing_ms,
            content_type=content_type,
        )


def _conf_to_logprob(confidence: Any) -> float | None:
    if confidence is None:
        return None
    try:
        c = float(confidence)
    except (TypeError, ValueError):
        return None
    return math.log(min(c, 1.0)) if c > 0 else None
