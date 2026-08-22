from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.analyzer.repository_analyzer import (
    RepositoryAnalyzeError,
    RepositoryAnalyzer,
)
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
    RepositoryAnalyzeRequest,
)

log = structlog.get_logger(__name__)


class RepositoryConsumer:
    def __init__(
        self,
        *,
        analyzer: RepositoryAnalyzer,
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
            domain="repository",
            action="analyze",
            envelope_type=Envelope[RepositoryAnalyzeRequest],
            idempotency=self._idempotency,
            publisher=self._publisher,
            routing_key=self._callback_routing_key,
            message_type="callback.analysis",
            process=self._process,
            failed_payload=self._failed_payload,
            done_fields=analysis_done_fields,
            expected_errors=(RepositoryAnalyzeError,),
        )

    async def _process(
        self, envelope: Envelope[RepositoryAnalyzeRequest]
    ) -> AnalysisCallbackPayload:
        req = envelope.payload
        user_id = envelope.context.user_id
        log.info(
            "repository.analyze.start",
            message_id=envelope.message_id,
            repository_id=req.repository_id,
            repo_full_name=req.repo_full_name,
            user_id=user_id,
            trace_id=envelope.trace_id,
        )
        progress = (
            self._progress.emitter_for(
                user_id=user_id,
                target_type="REPOSITORY",
                target_id=req.repository_id,
                trace_id=envelope.trace_id,
            )
            if self._progress is not None
            else None
        )
        result = await self._analyzer.analyze(
            repository_id=req.repository_id,
            repo_full_name=req.repo_full_name,
            default_branch=req.default_branch,
            user_id=user_id,
            analyzed_document_id=req.analyzed_document_id,
            progress=progress,
        )
        return AnalysisCallbackPayload(
            target_type="REPOSITORY",
            target_id=req.repository_id,
            status="ANALYZED",
            summary=result.summary,
            tech_stack=result.tech_stack,
            document_path=result.document_path,
            embedding_chunk_count=result.embedding_chunk_count,
        )

    def _failed_payload(
        self, req: RepositoryAnalyzeRequest, exc: Exception
    ) -> AnalysisCallbackPayload:
        return analysis_failed_payload(
            target_type="REPOSITORY",
            target_id=req.repository_id,
            exc=exc,
            domain_error=RepositoryAnalyzeError,
        )
