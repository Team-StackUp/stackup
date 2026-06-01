from __future__ import annotations

import structlog
from aio_pika.abc import AbstractIncomingMessage

from ai_server.chain.question_generation_chain import QuestionGenerator
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope
from ai_server.model.messages.questions import (
    DocumentContext,
    GenerateQuestionsRequest,
    QuestionPoolCallbackPayload,
)

log = structlog.get_logger(__name__)


class QuestionsConsumer:
    def __init__(
        self,
        *,
        generator: QuestionGenerator,
        publisher: CallbackPublisher,
        idempotency: LruIdempotencyStore,
        callback_routing_key: str,
        initial_pool_size: int = 1,
    ) -> None:
        self._generator = generator
        self._publisher = publisher
        self._idempotency = idempotency
        self._callback_routing_key = callback_routing_key
        # Core 의 QuestionsCallbackService.applyPool 은 questions[0] 만 INSERT 하고 나머지는 폐기.
        # 토큰 낭비를 줄이기 위해 envelope.max_questions 대신 풀 크기를 강제. 후속 작업에서 풀 저장 도입 시 늘리기 쉬움.
        self._initial_pool_size = max(1, initial_pool_size)

    async def handle(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            try:
                envelope = Envelope[GenerateQuestionsRequest].model_validate_json(
                    message.body
                )
            except Exception as exc:
                log.error(
                    "questions.parse.failed",
                    error=str(exc),
                    delivery_tag=message.delivery_tag,
                )
                raise

            if self._idempotency.is_seen_then_mark(envelope.message_id):
                log.info(
                    "questions.idempotent.skip",
                    message_id=envelope.message_id,
                    trace_id=envelope.trace_id,
                )
                return

            req = envelope.payload
            effective_pool_size = self._initial_pool_size  # envelope.max_questions 무시
            log.info(
                "questions.generate.start",
                message_id=envelope.message_id,
                session_id=req.session_id,
                doc_count=len(req.documents),
                max_questions=req.max_questions,
                pool_size=effective_pool_size,
                trace_id=envelope.trace_id,
            )

            context_text = _build_context(req.documents)
            pool = await self._generator.generate(
                job_category=req.job_category,
                mode=req.mode,
                max_questions=effective_pool_size,
                context=context_text,
            )

            payload = QuestionPoolCallbackPayload(
                session_id=req.session_id,
                kind="POOL",
                questions=pool.questions,
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
                "questions.generate.done",
                message_id=envelope.message_id,
                session_id=req.session_id,
                question_count=len(pool.questions),
                trace_id=envelope.trace_id,
            )


def _build_context(documents: list[DocumentContext]) -> str:
    parts: list[str] = []
    for d in documents:
        block = [f"## 문서 #{d.document_id} ({d.source_type})"]
        if d.summary:
            block.append(f"요약: {d.summary}")
        if d.tech_stack:
            block.append("기술 스택: " + ", ".join(d.tech_stack))
        if d.markdown:
            block.append("")
            block.append(d.markdown)
        parts.append("\n".join(block))
    return "\n\n".join(parts) if parts else "(no documents)"
