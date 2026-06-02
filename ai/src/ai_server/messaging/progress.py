from __future__ import annotations

import structlog

from ai_server.analyzer._progress import ProgressEmitter
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import MessageContext
from ai_server.model.messages.realtime import (
    AnalysisProgressData,
    RealtimeNotifyPayload,
)

log = structlog.get_logger(__name__)

ANALYSIS_PROGRESS_EVENT = "ANALYSIS_PROGRESS"


class AnalysisProgressNotifier:
    """AI -> RealTime 직접 발행. 분석 단계 진행 상황을 user 채널 SSE 로 흘린다.

    진행 정보는 휘발성이라 발행 실패가 분석을 막아선 안 된다 — 모든 예외를 삼키고 경고만 남긴다.
    """

    def __init__(self, *, publisher: CallbackPublisher, routing_key: str) -> None:
        self._publisher = publisher
        self._routing_key = routing_key

    async def emit(
        self,
        *,
        user_id: int | None,
        target_type: str,
        target_id: int,
        phase: str,
        message: str,
        trace_id: str,
    ) -> None:
        if user_id is None:
            return

        payload = RealtimeNotifyPayload(
            event_type=ANALYSIS_PROGRESS_EVENT,
            data=AnalysisProgressData(
                target_type=target_type,
                target_id=target_id,
                phase=phase,
                message=message,
            ),
        )
        try:
            await self._publisher.publish(
                routing_key=self._routing_key,
                message_type=self._routing_key,
                payload=payload,
                trace_id=trace_id,
                correlation_id=f"progress-{target_type}-{target_id}-{phase}",
                context=MessageContext(user_id=user_id),
            )
        except Exception:
            log.warning(
                "analysis.progress.publish_failed",
                target_type=target_type,
                target_id=target_id,
                phase=phase,
                trace_id=trace_id,
            )

    def emitter_for(
        self,
        *,
        user_id: int | None,
        target_type: str,
        target_id: int,
        trace_id: str,
    ) -> ProgressEmitter:
        async def _emit(phase: str, message: str) -> None:
            await self.emit(
                user_id=user_id,
                target_type=target_type,
                target_id=target_id,
                phase=phase,
                message=message,
                trace_id=trace_id,
            )

        return _emit
