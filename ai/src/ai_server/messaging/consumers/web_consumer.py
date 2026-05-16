from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.analyzer.web_resume_analyzer import (
    WebResumeAnalyzeError,
    WebResumeAnalyzer,
)
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    WebResumeAnalyzeRequest,
)

log = structlog.get_logger(__name__)


class WebResumeConsumer:
    def __init__(
        self,
        *,
        analyzer: WebResumeAnalyzer,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
    ) -> None:
        self._analyzer = analyzer
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[WebResumeAnalyzeRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "web_resume.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "web_resume.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

            req = envelope.payload
            log.info(
                "web_resume.analyze.start",
                message_id=envelope.message_id,
                resume_id=req.resume_id,
                url=req.url,
                trace_id=envelope.trace_id,
            )

            payload = await self._run_and_build_payload(req, envelope.trace_id)

            await self._publisher.publish(
                routing_key=self._callback_routing_key,
                message_type="callback.analysis",
                payload=payload,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )
            log.info(
                "web_resume.analyze.done",
                message_id=envelope.message_id,
                resume_id=req.resume_id,
                status=payload.status,
                trace_id=envelope.trace_id,
            )

    async def _run_and_build_payload(
        self,
        req: WebResumeAnalyzeRequest,
        trace_id: str,
    ) -> AnalysisCallbackPayload:
        try:
            result = await self._analyzer.analyze(
                resume_id=req.resume_id,
                url=req.url,
            )
        except WebResumeAnalyzeError as err:
            log.warning(
                "web_resume.analyze.domain_failed",
                resume_id=req.resume_id,
                code=err.code,
                retriable=err.retriable,
                trace_id=trace_id,
            )
            return AnalysisCallbackPayload(
                target_type="WEB",
                target_id=req.resume_id,
                status="FAILED",
                error_code=err.code,
                error_message=err.message,
                retriable=err.retriable,
            )
        except Exception as exc:
            log.exception(
                "web_resume.analyze.unexpected_failed",
                resume_id=req.resume_id,
                trace_id=trace_id,
            )
            return AnalysisCallbackPayload(
                target_type="WEB",
                target_id=req.resume_id,
                status="FAILED",
                error_code="UNEXPECTED",
                error_message=str(exc),
                retriable=True,
            )

        return AnalysisCallbackPayload(
            target_type="WEB",
            target_id=req.resume_id,
            status="ANALYZED",
            summary=result.summary,
            tech_stack=result.tech_stack,
            document_path=result.document_path,
            embedding_chunk_count=0,
        )
