from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.analyzer.web_resume_analyzer import (
    WebResumeAnalyzeError,
    WebResumeAnalyzer,
)
from ai_server.messaging.consumers.failure_signal import (
    analysis_done_fields,
    analysis_failed_payload,
    consume_with_failure_signal,
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
        await consume_with_failure_signal(
            message,
            domain="web_resume",
            action="analyze",
            envelope_type=Envelope[WebResumeAnalyzeRequest],
            idempotency=self._idempotency,
            publisher=self._publisher,
            routing_key=self._callback_routing_key,
            message_type="callback.analysis",
            process=self._process,
            failed_payload=self._failed_payload,
            done_fields=analysis_done_fields,
            expected_errors=(WebResumeAnalyzeError,),
        )

    async def _process(
        self, envelope: Envelope[WebResumeAnalyzeRequest]
    ) -> AnalysisCallbackPayload:
        req = envelope.payload
        log.info(
            "web_resume.analyze.start",
            message_id=envelope.message_id,
            resume_id=req.resume_id,
            url=req.url,
            trace_id=envelope.trace_id,
        )
        result = await self._analyzer.analyze(
            resume_id=req.resume_id,
            url=req.url,
            analyzed_document_id=req.analyzed_document_id,
        )
        return AnalysisCallbackPayload(
            target_type="WEB",
            target_id=req.resume_id,
            status="ANALYZED",
            summary=result.summary,
            tech_stack=result.tech_stack,
            document_path=result.document_path,
            embedding_chunk_count=result.embedding_chunk_count,
        )

    def _failed_payload(
        self, req: WebResumeAnalyzeRequest, exc: Exception
    ) -> AnalysisCallbackPayload:
        return analysis_failed_payload(
            target_type="WEB",
            target_id=req.resume_id,
            exc=exc,
            domain_error=WebResumeAnalyzeError,
        )
