from ai_server.config.settings import Settings


def _settings(**over):
    base = dict(
        rabbitmq_url="amqp://x",
        s3_endpoint_url="http://x",
        s3_access_key="x",
        s3_secret_key="x",
        s3_bucket_name="b",
        llm_api_key="school-gw-key",
        llm_base_url="https://gateway.example/v1",
        llm_pro_base_url="",
        llm_pro_api_key="",
        llm_flash_base_url="",
        llm_flash_api_key="",
    )
    base.update(over)
    return Settings(**base)


def test_tiers_share_gateway_when_not_overridden():
    s = _settings()
    for tier in ("pro", "flash"):
        assert s.llm_base_url_for(tier) == "https://gateway.example/v1"
        assert s.llm_api_key_for(tier) == "school-gw-key"


def test_flash_override_routes_only_flash_to_local():
    s = _settings(llm_flash_base_url="http://ollama:11434/v1")
    assert s.llm_base_url_for("flash") == "http://ollama:11434/v1"
    assert s.llm_base_url_for("pro") == "https://gateway.example/v1"
    assert s.llm_api_key_for("pro") == "school-gw-key"


def test_overridden_tier_never_inherits_shared_gateway_key():
    # 학교 게이트웨이 키가 다른 서버(로컬/외부)로 새면 안 된다.
    s = _settings(llm_flash_base_url="http://ollama:11434/v1")
    key = s.llm_api_key_for("flash")
    assert key != "school-gw-key"
    assert key  # OpenAI SDK 는 빈 키를 거부하므로 placeholder


def test_overridden_tier_uses_its_own_key():
    s = _settings(
        llm_pro_base_url="https://other.example/v1", llm_pro_api_key="pro-key"
    )
    assert s.llm_api_key_for("pro") == "pro-key"


def test_no_key_anywhere_returns_none_for_gateway_path():
    s = _settings(llm_api_key="")
    assert s.llm_api_key_for("pro") is None
