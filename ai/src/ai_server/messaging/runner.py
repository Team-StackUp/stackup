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
from ai_server.chain.pdf_vision import build_vision_pdf_reader
from ai_server.chain.followup_generation_chain import (
    LlmFollowupGenerator,
    build_followup_generation_chain,
)
from ai_server.chain.feedback_generation_chain import (
    LlmFeedbackGenerator,
    build_feedback_generation_chain,
)
from ai_server.chain.question_generation_chain import (
    LlmQuestionGenerator,
    build_question_generation_chain,
)
from ai_server.config.settings import Settings
from ai_server.core.client import HttpCoreClient
from ai_server.messaging.connection import RabbitConnection
from ai_server.rag.chunker import MarkdownChunker
from ai_server.rag.embedder import build_embedding_provider
from ai_server.rag.reranker import build_reranker
from ai_server.messaging.consumers.feedback_consumer import FeedbackConsumer
from ai_server.messaging.consumers.followup_consumer import FollowupConsumer
from ai_server.messaging.consumers.questions_consumer import QuestionsConsumer
from ai_server.messaging.consumers.tts_consumer import TtsConsumer
from ai_server.messaging.consumers.voice_consumer import VoiceConsumer
from ai_server.voice.stt.factory import build_stt_provider
from ai_server.voice.tts.factory import build_tts_provider
from ai_server.messaging.consumers.repository_consumer import RepositoryConsumer
from ai_server.messaging.consumers.resume_consumer import ResumeConsumer
from ai_server.messaging.consumers.web_consumer import WebResumeConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.progress import AnalysisProgressNotifier
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
        # AI -> RealTime 직접 발행용 (분석 단계 진행 상황). Core 콜백 익스체인지와 별개.
        self._realtime_publisher = CallbackPublisher(
            connection=self._connection,
            exchange_name=settings.ai_realtime_exchange,
            publisher_name=settings.ai_publisher_name,
        )
        self._progress_notifier = AnalysisProgressNotifier(
            publisher=self._realtime_publisher,
            routing_key=settings.ai_realtime_routing_user,
        )
        self._idempotency = LruIdempotencyStore(
            max_size=settings.ai_idempotency_lru_size,
        )

        storage = build_storage(settings)
        core_client = HttpCoreClient(
            base_url=settings.core_internal_base_url,
            api_key=settings.core_internal_api_key,
            timeout_sec=settings.core_internal_timeout_sec,
        )
        chain = build_document_analysis_chain(settings, core_client=core_client)
        chain_analyzer = LlmDocumentAnalyzer(chain)

        chunker = MarkdownChunker(
            chunk_size=settings.embedding_chunk_size,
            chunk_overlap=settings.embedding_chunk_overlap,
        )
        embedder = build_embedding_provider(
            provider=settings.embedding_provider,
            dim=settings.embedding_dim,
            model=settings.embedding_model,
            gemini_api_key=settings.gemini_api_key,
        )
        reranker = build_reranker(settings, core_client=core_client)
        vision_pdf_reader = build_vision_pdf_reader(settings, core_client=core_client)

        # 이력서 PDF
        resume_analyzer = ResumeAnalyzer(
            extractor=PdfSourceExtractor(
                storage=storage, vision_reader=vision_pdf_reader
            ),
            chain=chain_analyzer,
            storage=storage,
            chunker=chunker,
            embedder=embedder,
            core_client=core_client,
            analyzed_key_template=settings.analyzed_resume_md_key_template,
        )

        # 리포지토리
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
            chunker=chunker,
            embedder=embedder,
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
            chunker=chunker,
            embedder=embedder,
            core_client=core_client,
            analyzed_key_template=settings.analyzed_web_resume_md_key_template,
        )

        self._resume_consumer = ResumeConsumer(
            analyzer=resume_analyzer,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_analysis,
            progress_notifier=self._progress_notifier,
        )
        self._repository_consumer = RepositoryConsumer(
            analyzer=repo_analyzer,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_analysis,
            progress_notifier=self._progress_notifier,
        )
        self._web_consumer = WebResumeConsumer(
            analyzer=web_analyzer,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_analysis,
        )

        # 질문 풀 생성 (US-18)
        question_generator = LlmQuestionGenerator(
            build_question_generation_chain(settings, core_client=core_client)
        )
        self._questions_consumer = QuestionsConsumer(
            generator=question_generator,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_questions,
            initial_pool_size=settings.questions_initial_pool_size,
            core_client=core_client,
            embedder=embedder,
            reranker=reranker,
            candidate_k=settings.rerank_candidate_k,
        )

        # 꼬리질문 생성 (US-19)
        followup_generator = LlmFollowupGenerator(
            build_followup_generation_chain(settings, core_client=core_client)
        )
        self._followup_consumer = FollowupConsumer(
            generator=followup_generator,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_questions,
            core_client=core_client,
            embedder=embedder,
            reranker=reranker,
            candidate_k=settings.rerank_candidate_k,
        )

        # 종합 피드백 생성 (US-24)
        feedback_generator = LlmFeedbackGenerator(
            build_feedback_generation_chain(settings, core_client=core_client)
        )
        self._feedback_consumer = FeedbackConsumer(
            generator=feedback_generator,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_feedback,
            core_client=core_client,
            embedder=embedder,
            reranker=reranker,
            candidate_k=settings.rerank_candidate_k,
            rag_top_k=settings.feedback_rag_top_k,
        )

        # 음성 답변 STT + 분석 (Phase 2)
        stt = build_stt_provider(settings)
        self._voice_consumer = VoiceConsumer(
            stt=stt,
            storage=storage,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_voice,
            filler_pattern=settings.voice_filler_pattern,
            core_client=core_client,
        )

        # 질문 TTS (Part A)
        tts = build_tts_provider(settings)
        self._tts_consumer = TtsConsumer(
            tts=tts,
            storage=storage,
            publisher=self._publisher,
            idempotency=self._idempotency,
            callback_routing_key=settings.ai_callback_routing_tts,
            voice=settings.openai_tts_voice,
            key_template=settings.tts_audio_key_template,
        )

        self._consumers: list[tuple[AbstractRobustQueue, str]] = []

    @property
    def publisher(self) -> CallbackPublisher:
        return self._publisher

    async def start(self) -> None:
        await self._connection.open()
        await self._publisher.open()
        await self._realtime_publisher.open()

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
        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_questions,
            handler=self._questions_consumer.handle,
        )
        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_followup,
            handler=self._followup_consumer.handle,
        )
        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_feedback,
            handler=self._feedback_consumer.handle,
        )
        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_voice,
            handler=self._voice_consumer.handle,
        )
        await self._start_consumer(
            channel,
            queue_name=self._settings.ai_queue_tts,
            handler=self._tts_consumer.handle,
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
