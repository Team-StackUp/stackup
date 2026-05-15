from __future__ import annotations

import structlog
from aio_pika.abc import AbstractRobustQueue

from ai_server.analyzer.resume_analyzer import ResumeAnalyzer
from ai_server.analyzer.sources.pdf import PdfSourceExtractor
from ai_server.chain.document_analysis_chain import (
    LlmDocumentAnalyzer,
    build_document_analysis_chain,
)
from ai_server.config.settings import Settings
from ai_server.messaging.connection import RabbitConnection
from ai_server.messaging.consumers.resume_consumer import ResumeConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.storage.factory import build_storage

log = structlog.get_logger(__name__)


# FastAPI 서버가 켜져있는 동안 메세지를 보관함
class MessagingRuntime:

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._connection = RabbitConnection(
            url=settings.rabbitmq_url,
            prefetch=settings.ai_queue_prefetch,
        )
        self._publisher = CallbackPublisher(
            connection=self._connection,
            exchange_name=settings.ai_callback_exchange,
            publisher_name=settings.ai_publisher_name,
        )
        self._idempotency = LruIdempotencyStore(
            max_size=settings.ai_idempotency_lru_size,
        )

        storage = build_storage(settings)
        chain = build_document_analysis_chain(settings)
        analyzer = ResumeAnalyzer(
            extractor=PdfSourceExtractor(storage=storage),
            chain=LlmDocumentAnalyzer(chain),
            storage=storage,
            analyzed_key_template=settings.analyzed_resume_md_key_template,
        )

        self._resume_consumer = ResumeConsumer(
            analyzer=analyzer,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_analysis,
        )
        self._resume_queue: AbstractRobustQueue | None = None
        self._consumer_tag: str | None = None

    async def start(self) -> None:
        await self._connection.open()
        await self._publisher.open()

        channel = self._connection.channel
        queue = await channel.declare_queue(
            self._settings.ai_queue_resume,
            durable=True,
            passive=True,  # 정의 파일이 이미 선언함
        )
        self._resume_queue = queue
        self._consumer_tag = await queue.consume(self._resume_consumer.handle)
        log.info(
            "ai.consumer.started",
            queue=self._settings.ai_queue_resume,
            consumer_tag=self._consumer_tag,
        )

    async def stop(self) -> None:
        if self._resume_queue is not None and self._consumer_tag is not None:
            await self._resume_queue.cancel(self._consumer_tag)
            log.info("ai.consumer.stopped", consumer_tag=self._consumer_tag)
        await self._connection.close()
