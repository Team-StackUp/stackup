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
    CoverLetterAnalyzeRequest,
)

log = structlog.get_logger(__name__)


# 자소서 분석 consumer. 본문이 inline(content)이라는 점만 빼면 이력서와 동일한 분석·임베딩
# 파이프라인을 탄다 — ResumeAnalyzer 를 TextSourceExtractor 와 자소서 키 템플릿으로 재사용.
class CoverLetterConsumer:
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
            domain="cover_letter",
            action="analyze",
            envelope_type=Envelope[CoverLetterAnalyzeRequest],
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
        self, envelope: Envelope[CoverLetterAnalyzeRequest]
    ) -> AnalysisCallbackPayload:
        req = envelope.payload
        log.info(
            "cover_letter.analyze.start",
            message_id=envelope.message_id,
            cover_letter_id=req.cover_letter_id,
            trace_id=envelope.trace_id,
        )
        progress = (
            self._progress.emitter_for(
                user_id=envelope.context.user_id,
                target_type="COVER_LETTER",
                target_id=req.cover_letter_id,
                trace_id=envelope.trace_id,
            )
            if self._progress is not None
            else None
        )
        # ResumeAnalyzer 의 resume_id/file_path 는 각각 식별자/추출 locator 로 일반화돼 있어
        # 자소서는 cover_letter_id 와 inline content 를 그대로 넘긴다.
        result = await self._analyzer.analyze(
            resume_id=req.cover_letter_id,
            file_path=req.content,
            analyzed_document_id=req.analyzed_document_id,
            progress=progress,
        )
        return AnalysisCallbackPayload(
            target_type="COVER_LETTER",
            target_id=req.cover_letter_id,
            status="ANALYZED",
            summary=result.summary,
            tech_stack=result.tech_stack,
            document_path=result.document_path,
            embedding_chunk_count=result.embedding_chunk_count,
        )

    def _failed_payload(
        self, req: CoverLetterAnalyzeRequest, exc: Exception
    ) -> AnalysisCallbackPayload:
        return analysis_failed_payload(
            target_type="COVER_LETTER",
            target_id=req.cover_letter_id,
            exc=exc,
            domain_error=ResumeAnalyzeError,
        )
