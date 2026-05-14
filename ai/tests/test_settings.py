import os

import pytest

from ai_server.config.settings import Settings


def test_settings_defaults_for_messaging(monkeypatch: pytest.MonkeyPatch) -> None:
    # __init__.py가 .env를 우연히 읽지 않도록 환경 격리
    for key in [
        "RABBITMQ_URL",
        "AI_QUEUE_RESUME",
        "AI_QUEUE_PREFETCH",
        "AI_CALLBACK_EXCHANGE",
        "AI_CALLBACK_ROUTING_ANALYSIS",
        "AI_PUBLISHER_NAME",
        "AI_IDEMPOTENCY_LRU_SIZE",
    ]:
        monkeypatch.delenv(key, raising=False)

    s = Settings(_env_file=None)  # type: ignore[call-arg]

    assert s.rabbitmq_url == "amqp://stackup:stackup@localhost:38050/"
    assert s.ai_queue_resume == "ai.analyze.resume"
    assert s.ai_queue_prefetch == 10
    assert s.ai_callback_exchange == "stackup.ai-to-core"
    assert s.ai_callback_routing_analysis == "callback.analysis"
    assert s.ai_publisher_name == "ai-server"
    assert s.ai_idempotency_lru_size == 1024
