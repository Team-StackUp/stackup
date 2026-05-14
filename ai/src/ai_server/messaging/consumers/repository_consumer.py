from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.analyzer.repository_analyzer import (
    RepositoryAnalyzeError,
    RepositoryAnalyzer,
)
from ai_server.messaging.idempotency import LruIdempotencyStore
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
    ) -> None:
        self._analyzer = analyzer
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[RepositoryAnalyzeRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "repository.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "repository.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

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

            payload = await self._run_and_build_payload(
                req, user_id=user_id, trace_id=envelope.trace_id
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
                "repository.analyze.done",
                message_id=envelope.message_id,
                repository_id=req.repository_id,
                status=payload.status,
                trace_id=envelope.trace_id,
            )

    async def _run_and_build_payload(
        self,
        req: RepositoryAnalyzeRequest,
        *,
        user_id: int | None,
        trace_id: str,
    ) -> AnalysisCallbackPayload:
        try:
            result = await self._analyzer.analyze(
                repository_id=req.repository_id,
                repo_full_name=req.repo_full_name,
                default_branch=req.default_branch,
                user_id=user_id,
            )
        except RepositoryAnalyzeError as err:
            log.warning(
                "repository.analyze.domain_failed",
                repository_id=req.repository_id,
                code=err.code,
                retriable=err.retriable,
                trace_id=trace_id,
            )
            return AnalysisCallbackPayload(
                target_type="REPOSITORY",
                target_id=req.repository_id,
                status="FAILED",
                error_code=err.code,
                error_message=err.message,
                retriable=err.retriable,
            )
        except Exception as exc:
            log.exception(
                "repository.analyze.unexpected_failed",
                repository_id=req.repository_id,
                trace_id=trace_id,
            )
            return AnalysisCallbackPayload(
                target_type="REPOSITORY",
                target_id=req.repository_id,
                status="FAILED",
                error_code="UNEXPECTED",
                error_message=str(exc),
                retriable=True,
            )

        return AnalysisCallbackPayload(
            target_type="REPOSITORY",
            target_id=req.repository_id,
            status="ANALYZED",
            summary=result.summary,
            tech_stack=result.tech_stack,
            document_path=result.document_path,
            embedding_chunk_count=0,
        )
