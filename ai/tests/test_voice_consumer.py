from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.messaging.consumers.voice_consumer import VoiceConsumer
from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.model.messages.voice import VoiceCallbackPayload
from ai_server.voice.stt.base import (
    SttError,
    TranscriptionResult,
    TranscriptionSegment,
)


class _StubMessage:
    def __init__(self, body: bytes):
        self.body = body
        self.delivery_tag = 1

    def process(self, requeue: bool = False):
        return _NoopCtx()


class _NoopCtx:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False


def _envelope() -> bytes:
    env = {
        "messageId": "voice-1",
        "messageType": "analyze.voice",
        "version": "v1",
        "traceId": "t-1",
        "publishedAt": "2026-05-30T00:00:00Z",
        "publisher": "core-server",
        "payload": {
            "sessionId": 99,
            "messageId": 501,
            "parentQuestionMessageId": 500,
            "audioS3Key": "interview/voice/raw/99/501.webm",
            "contentType": "audio/webm",
            "previousQuestionText": "ACID 설명해주세요",
            "mode": "TECHNICAL",
            "jobCategory": "BACKEND",
        },
        "context": {"userId": 42, "sessionId": 99},
    }
    return json.dumps(env).encode()


def _stt_ok():
    stt = MagicMock()
    stt.transcribe = AsyncMock(
        return_value=TranscriptionResult(
            text="ACID는 원자성 일관성 격리성 영속성 입니다",
            language="ko",
            duration_sec=30.0,
            segments=[
                TranscriptionSegment(start_sec=0.0, end_sec=30.0,
                                     text="ACID는 원자성 일관성 격리성 영속성 입니다",
                                     avg_logprob=-0.3)
            ],
        )
    )
    return stt


def _storage_ok():
    storage = MagicMock()
    storage.get_bytes = AsyncMock(return_value=b"fake-audio")
    return storage


@pytest.mark.asyncio
async def test_consumer_publishes_transcript_and_metrics():
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = VoiceConsumer(
        stt=_stt_ok(),
        storage=_storage_ok(),
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.voice",
        filler_pattern=r"(?:음+|어+|그+|아+)",
    )
    await consumer.handle(_StubMessage(_envelope()))

    publisher.publish.assert_awaited_once()
    payload: VoiceCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.session_id == 99
    assert payload.interview_message_id == 501
    assert payload.transcript.startswith("ACID")
    assert payload.speaking_rate_wpm is not None
    assert publisher.publish.await_args.kwargs["message_type"] == "callback.voice"


@pytest.mark.asyncio
async def test_consumer_publishes_error_code_on_stt_failure():
    stt = MagicMock()
    stt.transcribe = AsyncMock(
        side_effect=SttError(code="STT_AUTH_FAILED", message="bad key", retriable=False)
    )
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = VoiceConsumer(
        stt=stt,
        storage=_storage_ok(),
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.voice",
        filler_pattern=r"(?:음+|어+|그+|아+)",
    )
    await consumer.handle(_StubMessage(_envelope()))

    payload: VoiceCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.error_code == "STT_AUTH_FAILED"
    assert payload.transcript is None


@pytest.mark.asyncio
async def test_consumer_publishes_error_code_on_storage_failure():
    storage = MagicMock()
    storage.get_bytes = AsyncMock(side_effect=RuntimeError("s3 down"))
    publisher = MagicMock()
    publisher.publish = AsyncMock()

    consumer = VoiceConsumer(
        stt=_stt_ok(),
        storage=storage,
        publisher=publisher,
        idempotency=LruIdempotencyStore(max_size=10),
        callback_routing_key="callback.voice",
        filler_pattern=r"(?:음+|어+|그+|아+)",
    )
    await consumer.handle(_StubMessage(_envelope()))

    payload: VoiceCallbackPayload = publisher.publish.await_args.kwargs["payload"]
    assert payload.error_code == "AUDIO_FETCH_FAILED"


@pytest.mark.asyncio
async def test_consumer_idempotent_skip():
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    idempotency = LruIdempotencyStore(max_size=10)
    idempotency.is_seen_then_mark("voice-1")

    consumer = VoiceConsumer(
        stt=_stt_ok(),
        storage=_storage_ok(),
        publisher=publisher,
        idempotency=idempotency,
        callback_routing_key="callback.voice",
        filler_pattern=r"(?:음+|어+|그+|아+)",
    )
    await consumer.handle(_StubMessage(_envelope()))
    publisher.publish.assert_not_awaited()
