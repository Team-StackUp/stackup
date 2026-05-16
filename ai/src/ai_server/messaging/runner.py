from __future__ import annotations

import structlog
from aio_pika.abc import AbstractRobustQueue

from ai_server.analyzer.repository_analyzer import RepositoryAnalyzer
from ai_server.analyzer.resume_analyzer import ResumeAnalyzer
from ai_server.analyzer.sources.github_repo import GitHubRepoSourceExtractor
from ai_server.analyzer.sources.pdf import PdfSourceExtractor
from ai_server.analyzer.sources.web import WebSourceExtractor
from ai_server.analyzer.web_resume_analyzer import WebResumeAnalyzer
from ai_server.chain.document_analysis_chain import (
    LlmDocumentAnalyzer,
    build_document_analysis_chain,
)
from ai_server.config.settings import Settings
from ai_server.core.client import HttpCoreClient
from ai_server.messaging.connection import RabbitConnection
from ai_server.messaging.consumers.repository_consumer import RepositoryConsumer
from ai_server.messaging.consumers.resume_consumer import ResumeConsumer
from ai_server.messaging.consumers.web_consumer import WebResumeConsumer
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
        chain_analyzer = LlmDocumentAnalyzer(chain)

        # 이력서 PDF
        resume_analyzer = ResumeAnalyzer(
            extractor=PdfSourceExtractor(storage=storage),
            chain=chain_analyzer,
            storage=storage,
            analyzed_key_template=settings.analyzed_resume_md_key_template,
        )

        # 리포지토리
        core_client = HttpCoreClient(
            base_url=settings.core_internal_base_url,
            api_key=settings.core_internal_api_key,
            timeout_sec=settings.core_internal_timeout_sec,
        )
        repo_analyzer = RepositoryAnalyzer(
            extractor=GitHubRepoSourceExtractor(
                api_base_url=settings.github_api_base_url,
                fallback_token=settings.github_fallback_token,
                max_files=settings.repo_max_source_files,
                max_file_bytes=settings.repo_max_source_file_bytes,
                timeout_sec=settings.repo_fetch_timeout_sec,
            ),
            core_client=core_client,
            chain=chain_analyzer,
            storage=storage,
            analyzed_key_template=settings.analyzed_repository_md_key_template,
        )

        # 웹 이력서
        web_analyzer = WebResumeAnalyzer(
            extractor=WebSourceExtractor(
                timeout_sec=settings.web_fetch_timeout_sec,
                max_html_bytes=settings.web_max_html_bytes,
            ),
            chain=chain_analyzer,
            storage=storage,
            analyzed_key_template=settings.analyzed_web_resume_md_key_template,
        )

        self._resume_consumer = ResumeConsumer(
            analyzer=resume_analyzer,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_analysis,
        )
        self._repository_consumer = RepositoryConsumer(
            analyzer=repo_analyzer,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_analysis,
        )
        self._web_consumer = WebResumeConsumer(
            analyzer=web_analyzer,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_analysis,
        )

        self._consumers: list[tuple[AbstractRobustQueue, str]] = []

    async def start(self) -> None:
        await self._connection.open()
        await self._publisher.open()

        channel = self._connection.channel

        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_resume,
            handler=self._resume_consumer.handle,
        )
        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_repository,
            handler=self._repository_consumer.handle,
        )
        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_web,
            handler=self._web_consumer.handle,
        )

    async def _start_consumer(self, channel, *, queue_name, handler) -> None:
        queue = await channel.declare_queue(
            queue_name,
            durable=True,
            passive=True,
        )
        tag = await queue.consume(handler)
        self._consumers.append((queue, tag))
        log.info("ai.consumer.started", queue=queue_name, consumer_tag=tag)

    async def stop(self) -> None:
        for queue, tag in self._consumers:
            try:
                await queue.cancel(tag)
                log.info("ai.consumer.stopped", consumer_tag=tag)
            except Exception:  
                log.exception("ai.consumer.cancel_failed", consumer_tag=tag)
        await self._connection.close()
