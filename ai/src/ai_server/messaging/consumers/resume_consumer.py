from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.analyzer.resume_analyzer import ResumeAnalyzeError, ResumeAnalyzer
from ai_server.messaging.consumers.failure_signal import (
    analysis_done_fields,
    analysis_failed_payload,
    consume_with_failure_signal,
)
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
        await consume_with_failure_signal(
            message,
            domain="resume",
            action="analyze",
            envelope_type=Envelope[ResumeAnalyzeRequest],
            idempotency=self._idempotency,
            publisher=self._publisher,
            routing_key=self._callback_routing_key,
            message_type="callback.analysis",
            process=self._process,
            failed_payload=self._failed_payload,
            done_fields=analysis_done_fields,
            expected_errors=(ResumeAnalyzeError,),
        )

    async def _process(
        self, envelope: Envelope[ResumeAnalyzeRequest]
    ) -> AnalysisCallbackPayload:
        req = envelope.payload
        log.info(
            "resume.analyze.start",
            message_id=envelope.message_id,
            resume_id=req.resume_id,
            trace_id=envelope.trace_id,
        )
        progress = (
            self._progress.emitter_for(
                user_id=envelope.context.user_id,
                target_type="RESUME",
                target_id=req.resume_id,
                trace_id=envelope.trace_id,
            )
            if self._progress is not None
            else None
        )
        result = await self._analyzer.analyze(
            resume_id=req.resume_id,
            file_path=req.file_path,
            analyzed_document_id=req.analyzed_document_id,
            progress=progress,
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

    def _failed_payload(
        self, req: ResumeAnalyzeRequest, exc: Exception
    ) -> AnalysisCallbackPayload:
        return analysis_failed_payload(
            target_type="RESUME",
            target_id=req.resume_id,
            exc=exc,
            domain_error=ResumeAnalyzeError,
        )
