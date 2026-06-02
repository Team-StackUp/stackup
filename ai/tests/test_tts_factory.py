from ai_server.config.settings import Settings
from ai_server.voice.tts.factory import build_tts_provider
from ai_server.voice.tts.mock import MockTtsProvider
from ai_server.voice.tts.openai_tts import OpenAiTtsProvider


def _settings(**over):
    base = dict(
        rabbitmq_url="amqp://x",
        s3_endpoint_url="http://x",
        s3_access_key="x",
        s3_secret_key="x",
        s3_bucket_name="b",
    )
    base.update(over)
    return Settings(**base)


def test_auto_falls_back_to_mock_without_key():
    s = _settings(tts_provider="auto", openai_api_key="")
    assert isinstance(build_tts_provider(s), MockTtsProvider)


def test_auto_picks_openai_with_key():
    s = _settings(tts_provider="auto", openai_api_key="sk-test")
    assert isinstance(build_tts_provider(s), OpenAiTtsProvider)


def test_explicit_mock():
    s = _settings(tts_provider="mock", openai_api_key="sk-test")
    assert isinstance(build_tts_provider(s), MockTtsProvider)
