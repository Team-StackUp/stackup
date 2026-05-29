from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.followup_generation_chain import FollowupGenerator
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.followup import (
    FollowupCallbackPayload,
    GenerateFollowupRequest,
)

log = structlog.get_logger(__name__)


class FollowupConsumer:
    def __init__(
        self,
        *,
        generator: FollowupGenerator,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
    ) -> None:
        self._generator = generator
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[GenerateFollowupRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "followup.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "followup.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

            req = envelope.payload
            log.info(
                "followup.generate.start",
                message_id=envelope.message_id,
                session_id=req.session_id,
                parent=req.parent_message_id,
                trace_id=envelope.trace_id,
            )

            result = await self._generator.generate(
                job_category=req.job_category,
                interview_type=req.interview_type,
                previous_question=req.previous_question,
                answer_text=req.answer_text,
            )

            payload = FollowupCallbackPayload(
                session_id=req.session_id,
                kind="FOLLOWUP",
                parent_message_id=req.parent_message_id,
                followup_question=result.followup_question,
                answer_evaluation=result.answer_evaluation,
            )

            await self._publisher.publish(
                routing_key=self._callback_routing_key,
                message_type="callback.questions",
                payload=payload,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )
            log.info(
                "followup.generate.done",
                message_id=envelope.message_id,
                session_id=req.session_id,
                trace_id=envelope.trace_id,
            )
