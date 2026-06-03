import json
from datetime import datetime, timezone

import pytest

from ai_server.messaging.consumers.tts_consumer import TtsConsumer
from ai_server.voice.tts.base import TtsProvider, TtsResult
from ai_server.voice.tts.mock import MockTtsProvider


class _WavTts(TtsProvider):
    model_name = "fake-wav"

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        return TtsResult(
            audio_bytes=b"RIFF....WAVE", duration_sec=1.2, content_type="audio/wav"
        )


class _FakeStorage:
    def __init__(self):
        self.puts = []

    async def put_bytes(self, key, data, *, content_type=None):
        self.puts.append((key, data, content_type))


class _FakePublisher:
    def __init__(self):
        self.published = []

    async def publish(
        self, *, routing_key, message_type, payload, trace_id, correlation_id, context
    ):
        self.published.append((routing_key, message_type, payload))


class _FakeIdem:
    def is_seen_then_mark(self, mid):
        return False


class _FakeMsg:
    def __init__(self, body: bytes):
        self.body = body
        self.delivery_tag = 1

    def process(self, requeue=False):
        class _Ctx:
            async def __aenter__(self_):
                return self_

            async def __aexit__(self_, *a):
                return False

        return _Ctx()


def _envelope_body():
    return json.dumps(
        {
            "messageId": "m-1",
            "messageType": "generate.tts",
            "version": "v1",
            "traceId": "t-1",
            "publishedAt": datetime.now(timezone.utc).isoformat(),
            "publisher": "core-server",
            "payload": {
                "sessionId": 7,
                "messageId": 42,
                "text": "질문 본문",
                "mode": "TECHNICAL",
                "jobCategory": "BACKEND",
            },
            "context": {"userId": 1, "sessionId": 7},
        }
    ).encode("utf-8")


@pytest.mark.asyncio
async def test_tts_consumer_synthesizes_and_publishes():
    storage = _FakeStorage()
    publisher = _FakePublisher()
    consumer = TtsConsumer(
        tts=MockTtsProvider(),
        storage=storage,
        publisher=publisher,
        idempotency=_FakeIdem(),
        callback_routing_key="callback.tts",
        voice="alloy",
        key_template="interview/tts/{session_id}/{message_id}.mp3",
    )

    await consumer.handle(_FakeMsg(_envelope_body()))

    assert storage.puts, "S3 PUT 발생해야 함"
    key, data, ctype = storage.puts[0]
    assert key == "interview/tts/7/42.mp3"
    assert ctype == "audio/mpeg"
    assert publisher.published, "callback.tts 발행해야 함"
    rk, mtype, payload = publisher.published[0]
    assert rk == "callback.tts"
    assert mtype == "callback.tts"
    assert payload.status == "SUCCEEDED"
    assert payload.audio_key == "interview/tts/7/42.mp3"
    assert payload.message_id == 42


@pytest.mark.asyncio
async def test_tts_consumer_uses_wav_extension_for_wav_audio():
    # WAV(Gemini) 결과면 템플릿의 .mp3 확장자를 .wav 로 교체해 저장해야 한다.
    storage = _FakeStorage()
    publisher = _FakePublisher()
    consumer = TtsConsumer(
        tts=_WavTts(),
        storage=storage,
        publisher=publisher,
        idempotency=_FakeIdem(),
        callback_routing_key="callback.tts",
        voice="alloy",
        key_template="interview/tts/{session_id}/{message_id}.mp3",
    )

    await consumer.handle(_FakeMsg(_envelope_body()))

    key, _data, ctype = storage.puts[0]
    assert key == "interview/tts/7/42.wav"
    assert ctype == "audio/wav"
    assert publisher.published[0][2].audio_key == "interview/tts/7/42.wav"
