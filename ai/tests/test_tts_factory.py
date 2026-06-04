from ai_server.config.settings import Settings
from ai_server.voice.tts.factory import build_tts_provider
from ai_server.voice.tts.gateway import GatewayTtsProvider
from ai_server.voice.tts.gemini import GeminiTtsProvider
from ai_server.voice.tts.mock import MockTtsProvider
from ai_server.voice.tts.openai_tts import OpenAiTtsProvider


def _settings(**over):
    base = dict(
        rabbitmq_url="amqp://x",
        s3_endpoint_url="http://x",
        s3_access_key="x",
        s3_secret_key="x",
        s3_bucket_name="b",
        # 게이트웨이가 auto 최우선이므로, 명시 안 한 테스트는 비활성으로 고정(.env 누수 방지).
        llm_api_key="",
    )
    base.update(over)
    return Settings(**base)


def test_auto_prefers_gateway_when_llm_key_present():
    # 충남대 게이트웨이 키가 있으면 직접 Gemini 키보다 우선(429 부하 분산).
    s = _settings(tts_provider="auto", llm_api_key="gw-key", gemini_api_key="g-test")
    assert isinstance(build_tts_provider(s), GatewayTtsProvider)


def test_auto_falls_back_to_mock_without_key():
    s = _settings(tts_provider="auto", openai_api_key="", gemini_api_key="")
    assert isinstance(build_tts_provider(s), MockTtsProvider)


def test_auto_picks_openai_with_key():
    s = _settings(tts_provider="auto", openai_api_key="sk-test", gemini_api_key="")
    assert isinstance(build_tts_provider(s), OpenAiTtsProvider)


def test_explicit_mock():
    s = _settings(tts_provider="mock", openai_api_key="sk-test")
    assert isinstance(build_tts_provider(s), MockTtsProvider)


def test_auto_prefers_gemini_when_key_present():
    # Deepgram/OpenAI TTS 는 한국어 미지원 → gemini 키가 있으면 gemini 우선.
    s = _settings(
        tts_provider="auto", gemini_api_key="g-test", openai_api_key="sk-test"
    )
    assert isinstance(build_tts_provider(s), GeminiTtsProvider)


def test_explicit_gemini():
    s = _settings(tts_provider="gemini", gemini_api_key="g-test")
    assert isinstance(build_tts_provider(s), GeminiTtsProvider)


def test_gemini_without_key_falls_back_to_mock():
    s = _settings(tts_provider="gemini", gemini_api_key="")
    assert isinstance(build_tts_provider(s), MockTtsProvider)
