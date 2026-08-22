from __future__ import annotations

import structlog

from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import MessageContext
from ai_server.model.messages.realtime import (
    SessionAudioNotifyPayload,
    SessionMessageAudioData,
    SessionMessageDeltaData,
    SessionNotifyPayload,
    SessionProgressData,
    SessionProgressNotifyPayload,
)

log = structlog.get_logger(__name__)

SESSION_MESSAGE_DELTA_EVENT = "SESSION_MESSAGE_DELTA"
SESSION_MESSAGE_AUDIO_EVENT = "SESSION_MESSAGE_AUDIO"
QUESTION_POOL_PROGRESS_EVENT = "QUESTION_POOL_PROGRESS"
FEEDBACK_PROGRESS_EVENT = "FEEDBACK_PROGRESS"


class SessionRealtimeNotifier:
    """AI -> RealTime 세션 채널 직접 발행. 꼬리질문 토큰 델타(휘발성).

    발행 실패가 생성 파이프라인을 막아선 안 된다 — 예외를 삼키고 경고만.
    """

    def __init__(self, *, publisher: CallbackPublisher, routing_key: str) -> None:
        self._publisher = publisher
        self._routing_key = routing_key

    async def emit_delta(
        self,
        *,
        session_id: int,
        message_id: int,
        seq: int,
        text: str,
        trace_id: str,
    ) -> None:
        payload = SessionNotifyPayload(
            event_type=SESSION_MESSAGE_DELTA_EVENT,
            data=SessionMessageDeltaData(message_id=message_id, seq=seq, text=text),
        )
        try:
            await self._publisher.publish(
                routing_key=self._routing_key,
                message_type=self._routing_key,
                payload=payload,
                trace_id=trace_id,
                correlation_id=f"delta-{message_id}-{seq}",
                context=MessageContext(session_id=session_id),
            )
        except Exception:
            log.warning(
                "session.delta.publish_failed",
                session_id=session_id,
                message_id=message_id,
                seq=seq,
                trace_id=trace_id,
            )

    async def emit_audio(
        self,
        *,
        session_id: int,
        message_id: int,
        seq: int,
        ext: str,
        duration_sec: float | None,
        trace_id: str,
    ) -> None:
        payload = SessionAudioNotifyPayload(
            event_type=SESSION_MESSAGE_AUDIO_EVENT,
            data=SessionMessageAudioData(
                message_id=message_id, seq=seq, ext=ext, duration_sec=duration_sec
            ),
        )
        try:
            await self._publisher.publish(
                routing_key=self._routing_key,
                message_type=self._routing_key,
                payload=payload,
                trace_id=trace_id,
                correlation_id=f"audio-{message_id}-{seq}",
                context=MessageContext(session_id=session_id),
            )
        except Exception:
            log.warning(
                "session.audio.publish_failed",
                session_id=session_id,
                message_id=message_id,
                seq=seq,
                trace_id=trace_id,
            )

    async def emit_progress(
        self,
        *,
        event_type: str,
        session_id: int,
        phase: str,
        message: str,
        trace_id: str,
        completed: int | None = None,
        total: int | None = None,
    ) -> None:
        payload = SessionProgressNotifyPayload(
            event_type=event_type,
            data=SessionProgressData(
                session_id=session_id,
                phase=phase,
                message=message,
                completed=completed,
                total=total,
            ),
        )
        try:
            await self._publisher.publish(
                routing_key=self._routing_key,
                message_type=self._routing_key,
                payload=payload,
                trace_id=trace_id,
                correlation_id=f"progress-{session_id}-{phase}-{completed or 0}",
                context=MessageContext(session_id=session_id),
            )
        except Exception:
            log.warning(
                "session.progress.publish_failed",
                session_id=session_id,
                event_type=event_type,
                phase=phase,
                trace_id=trace_id,
            )
