from fastapi import FastAPI
from fastapi.testclient import TestClient

from ai_server.api.voice_stream import router
from ai_server.voice.stt.mock_live import MockLiveSttProvider


class _FakePublisher:
    def __init__(self):
        self.published = []

    async def publish(self, **kwargs):
        self.published.append(kwargs)


class _Settings:
    core_internal_api_key = ""          # 빈 값이면 인증 skip
    voice_filler_pattern = r"(음+|어+|그+)"
    ai_callback_routing_voice = "callback.voice"


def _app(publisher):
    app = FastAPI()
    app.include_router(router)
    app.state.live_stt_provider = MockLiveSttProvider(script=["안녕", "안녕하세요"])
    app.state.callback_publisher = publisher
    app.state.settings = _Settings()
    return app


def test_stream_publishes_callback_voice_on_stop():
    publisher = _FakePublisher()
    client = TestClient(_app(publisher))
    with client.websocket_connect("/internal/voice/stream?sessionId=7&messageId=42") as ws:
        ws.send_bytes(b"chunk1")
        ws.send_bytes(b"chunk2")
        # 부분 자막 수신
        first = ws.receive_json()
        assert first["type"] in ("transcript.partial", "transcript.final")
        ws.send_text('{"type":"stop"}')
    # 연결 종료 후 callback.voice 발행되어 있어야 함
    assert publisher.published, "callback.voice 발행됨"
    pub = publisher.published[0]
    assert pub["message_type"] == "callback.voice"
    assert pub["payload"].interview_message_id == 42
    assert pub["payload"].session_id == 7
