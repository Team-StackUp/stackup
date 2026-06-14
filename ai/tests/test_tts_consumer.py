import io
import json
import wave
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


def _wav_bytes(n_frames: int) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(24000)
        w.writeframes(b"\x00\x01" * n_frames)
    return buf.getvalue()


class _PerSentenceWavTts(TtsProvider):
    """문장마다 길이가 다른 정상 WAV 를 돌려준다(합본 검증용)."""

    model_name = "fake-wav-seg"

    def __init__(self):
        self.calls: list[str] = []

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        self.calls.append(text)
        return TtsResult(
            audio_bytes=_wav_bytes(100 * len(self.calls)),
            duration_sec=0.5,
            content_type="audio/wav",
        )


class _RecordingNotifier:
    def __init__(self):
        self.audio: list[dict] = []

    async def emit_audio(self, *, session_id, message_id, seq, ext, duration_sec, trace_id):
        self.audio.append(
            {"session_id": session_id, "message_id": message_id, "seq": seq, "ext": ext}
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


def _envelope_body(text: str = "질문 본문"):
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
                "text": text,
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


@pytest.mark.asyncio
async def test_tts_consumer_streams_segments_then_assembles_full_file():
    # notifier 주입 시: 문장마다 세그먼트 PUT + emit_audio, 마지막에 전체 파일 PUT + callback.
    storage = _FakeStorage()
    publisher = _FakePublisher()
    notifier = _RecordingNotifier()
    tts = _PerSentenceWavTts()
    consumer = TtsConsumer(
        tts=tts,
        storage=storage,
        publisher=publisher,
        idempotency=_FakeIdem(),
        callback_routing_key="callback.tts",
        voice="alloy",
        key_template="interview/tts/{session_id}/{message_id}.mp3",
        session_notifier=notifier,
    )

    await consumer.handle(_FakeMsg(_envelope_body("첫 문장입니다. 둘째 문장입니다.")))

    # 문장 2개 → 세그먼트 2개 합성
    assert tts.calls == ["첫 문장입니다.", "둘째 문장입니다."]
    # seq 0,1 로 라이브 푸시
    assert [a["seq"] for a in notifier.audio] == [0, 1]
    assert all(a["ext"] == "wav" for a in notifier.audio)
    # 세그먼트 키 + 전체 파일 키가 모두 PUT 됨
    seg_keys = [k for (k, _d, _c) in storage.puts if "/seg-" in k]
    assert seg_keys == [
        "interview/tts/7/42/seg-0.wav",
        "interview/tts/7/42/seg-1.wav",
    ]
    full_key, full_data, full_ctype = storage.puts[-1]
    assert full_key == "interview/tts/7/42.wav"
    assert full_ctype == "audio/wav"
    # 합본은 세그먼트 PCM 프레임 합(100+200) 을 담은 정상 WAV
    with wave.open(io.BytesIO(full_data), "rb") as r:
        assert r.getnframes() == 300
    # callback.tts SUCCEEDED + 전체 키
    rk, mtype, payload = publisher.published[0]
    assert rk == "callback.tts"
    assert payload.status == "SUCCEEDED"
    assert payload.audio_key == "interview/tts/7/42.wav"


@pytest.mark.asyncio
async def test_tts_consumer_segment_seq_stays_contiguous_when_one_fails():
    # 중간 문장 합성이 실패해도 seq 는 0,1 로 연속 유지(프론트 큐 정지 방지).
    storage = _FakeStorage()
    publisher = _FakePublisher()
    notifier = _RecordingNotifier()

    class _FlakyTts(TtsProvider):
        model_name = "flaky"

        def __init__(self):
            self.n = 0

        async def synthesize(self, text, *, voice):
            self.n += 1
            if self.n == 2:
                raise RuntimeError("boom")
            return TtsResult(
                audio_bytes=_wav_bytes(50), duration_sec=0.3, content_type="audio/wav"
            )

    consumer = TtsConsumer(
        tts=_FlakyTts(),
        storage=storage,
        publisher=publisher,
        idempotency=_FakeIdem(),
        callback_routing_key="callback.tts",
        voice="alloy",
        key_template="interview/tts/{session_id}/{message_id}.mp3",
        session_notifier=notifier,
    )

    await consumer.handle(_FakeMsg(_envelope_body("하나. 둘. 셋.")))

    # 3문장 중 2번째 실패 → 성공 2개에 seq 0,1 연속 부여
    assert [a["seq"] for a in notifier.audio] == [0, 1]
    assert publisher.published[0][2].status == "SUCCEEDED"
