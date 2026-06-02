import pytest

from ai_server.voice.tts.mock import MockTtsProvider


@pytest.mark.asyncio
async def test_mock_tts_returns_bytes_and_duration():
    provider = MockTtsProvider()
    result = await provider.synthesize("안녕하세요 질문입니다", voice="alloy")
    assert isinstance(result.audio_bytes, bytes)
    assert len(result.audio_bytes) > 0
    assert result.duration_sec is not None
    assert result.content_type == "audio/mpeg"
    assert provider.model_name == "mock-tts"
