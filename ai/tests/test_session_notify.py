import pytest
from ai_server.messaging.session_notify import SessionRealtimeNotifier


class _FakePublisher:
    def __init__(self):
        self.calls = []

    async def publish(self, **kwargs):
        self.calls.append(kwargs)


@pytest.mark.asyncio
async def test_emit_delta_publishes_session_event():
    pub = _FakePublisher()
    n = SessionRealtimeNotifier(publisher=pub, routing_key="realtime.session.notify")
    await n.emit_delta(session_id=7, message_id=503, seq=0, text="안녕", trace_id="t1")
    assert len(pub.calls) == 1
    call = pub.calls[0]
    assert call["routing_key"] == "realtime.session.notify"
    assert call["message_type"] == "realtime.session.notify"
    assert call["context"].session_id == 7
    assert call["payload"].event_type == "SESSION_MESSAGE_DELTA"
    assert call["payload"].data.text == "안녕"


@pytest.mark.asyncio
async def test_emit_delta_swallows_publish_errors():
    class Boom:
        async def publish(self, **kwargs):
            raise RuntimeError("down")

    n = SessionRealtimeNotifier(publisher=Boom(), routing_key="realtime.session.notify")
    await n.emit_delta(session_id=1, message_id=2, seq=0, text="x", trace_id="t")
