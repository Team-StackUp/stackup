from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.analyzer.resume_analyzer import ResumeAnalyzeError, ResumeAnalyzer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.progress import AnalysisProgressNotifier
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    ResumeAnalyzeRequest,
)

log = structlog.get_logger(__name__)


class ResumeConsumer:
    def __init__(
        self,
        *,
        analyzer: ResumeAnalyzer,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
        progress_notifier: AnalysisProgressNotifier | None = None,
    ) -> None:
        self._analyzer = analyzer
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        self._progress = progress_notifier

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

            req = envelope.payload
            log.info(
                "resume.analyze.start",
                message_id=envelope.message_id,
                resume_id=req.resume_id,
                trace_id=envelope.trace_id,
            )

            payload = await self._run_and_build_payload(
                req, envelope.trace_id, user_id=envelope.context.user_id
            )

            await self._publisher.publish(
                routing_key=self._callback_routing_key,
                message_type="callback.analysis",
                payload=payload,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )
            log.info(
                "resume.analyze.done",
                message_id=envelope.message_id,
                resume_id=req.resume_id,
                status=payload.status,
                trace_id=envelope.trace_id,
            )

    async def _run_and_build_payload(
        self,
        req: ResumeAnalyzeRequest,
        trace_id: str,
        *,
        user_id: int | None,
    ) -> AnalysisCallbackPayload:
        progress = (
            self._progress.emitter_for(
                user_id=user_id,
                target_type="RESUME",
                target_id=req.resume_id,
                trace_id=trace_id,
            )
            if self._progress is not None
            else None
        )
        try:
            result = await self._analyzer.analyze(
                resume_id=req.resume_id,
                file_path=req.file_path,
                analyzed_document_id=req.analyzed_document_id,
                progress=progress,
            )
        except ResumeAnalyzeError as err:
            log.warning(
                "resume.analyze.domain_failed",
                resume_id=req.resume_id,
                code=err.code,
                retriable=err.retriable,
                trace_id=trace_id,
            )
            return AnalysisCallbackPayload(
                target_type="RESUME",
                target_id=req.resume_id,
                status="FAILED",
                error_code=err.code,
                error_message=err.message,
                retriable=err.retriable,
            )
        except Exception as exc:
            log.exception(
                "resume.analyze.unexpected_failed",
                resume_id=req.resume_id,
                trace_id=trace_id,
            )
            return AnalysisCallbackPayload(
                target_type="RESUME",
                target_id=req.resume_id,
                status="FAILED",
                error_code="UNEXPECTED",
                error_message=str(exc),
                retriable=True,
            )

        return AnalysisCallbackPayload(
            target_type="RESUME",
            target_id=req.resume_id,
            status="ANALYZED",
            summary=result.summary,
            tech_stack=result.tech_stack,
            document_path=result.document_path,
            embedding_chunk_count=result.embedding_chunk_count,
        )
