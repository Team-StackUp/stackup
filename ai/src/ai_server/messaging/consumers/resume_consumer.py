from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    ResumeAnalyzeRequest,
)

log = structlog.get_logger(__name__)


def build_echo_callback(
    envelope: Envelope[ResumeAnalyzeRequest],
) -> AnalysisCallbackPayload:
    rid = envelope.payload.resume_id
    return AnalysisCallbackPayload(
        target_type="RESUME",
        target_id=rid,
        status="ANALYZED",
        summary="[ECHO] not yet analyzed",
        tech_stack=[],
        document_s3_key=f"echo/resume/{rid}/echo.md",
        embedding_chunk_count=0,
    )


class ResumeConsumer:
    def __init__(
        self,
        *,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
    ) -> None:
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[ResumeAnalyzeRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:  # parse error → DLQ-ready (auto NACK on raise)
                log.error(
                    "resume.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "resume.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

            log.info(
                "resume.echo.start",
                message_id=envelope.message_id,
                resume_id=envelope.payload.resume_id,
                trace_id=envelope.trace_id,
            )
            callback = build_echo_callback(envelope)
            await self._publisher.publish(
                routing_key=self._callback_routing_key,
                message_type="callback.analysis",
                payload=callback,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )
            log.info(
                "resume.echo.done",
                message_id=envelope.message_id,
                resume_id=envelope.payload.resume_id,
                trace_id=envelope.trace_id,
            )
