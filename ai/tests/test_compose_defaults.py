from __future__ import annotations

import re
from pathlib import Path

from ai_server.config.settings import Settings

COMPOSE = Path(__file__).resolve().parents[2] / "docker-compose.yml"

# compose 가 기본값을 가져야 하는 키 — settings.py 가 알 수 없는 값들.
#  · 컨테이너 토폴로지: 서비스 호스트명(minio/backend/rabbitmq)은 compose 네트워크의 사실이고
#    settings.py 기본값(localhost)은 로컬 실행용이라 서로 달라야 정상이다.
#  · 이름 변환: MINIO_ROOT_USER → S3_ACCESS_KEY 처럼 키 이름 자체가 바뀌는 것.
#  · 로컬 개발 편의 기본값: 비어 있으면 로컬 기동이 막히는 것(CORE_INTERNAL_API_KEY).
COMPOSE_OWNED = {
    "RABBITMQ_URL",
    "S3_ENDPOINT_URL",
    "S3_ACCESS_KEY",
    "S3_SECRET_KEY",
    "S3_BUCKET_NAME",
    "STORAGE_LOCAL_ROOT",
    "CORE_INTERNAL_BASE_URL",
    "CORE_INTERNAL_API_KEY",
}


def _ai_service_block() -> str:
    text = COMPOSE.read_text(encoding="utf-8")
    start = text.index("\n  ai:")
    rest = text[start + 1 :]
    # 첫 줄(`  ai:`) 자신이 매칭되지 않도록 다음 줄부터 형제 서비스를 찾는다.
    body_at = rest.index("\n") + 1
    nxt = re.search(r"^  [a-z][a-z0-9_-]*:$", rest[body_at:], re.M)
    return rest[: body_at + nxt.start()] if nxt else rest


def _env_entries() -> list[str]:
    """ai 서비스의 `environment:` 리스트 항목만. ports/extra_hosts 같은 다른 리스트는 제외."""
    block = _ai_service_block()
    env_at = block.index("    environment:")
    after = block[env_at + len("    environment:") :]
    end = re.search(r"^    [a-z_]+:", after, re.M)
    body = after[: end.start()] if end else after
    return [
        line.strip()[2:] for line in body.splitlines() if line.strip().startswith("- ")
    ]


def _declared_defaults() -> dict[str, str]:
    """compose 가 값을 직접 박아 둔 환경변수 → 그 값."""
    out: dict[str, str] = {}
    for entry in _env_entries():
        if "=" in entry:
            key, value = entry.split("=", 1)
            out[key.strip()] = value.strip()
    return out


def _passthrough_keys() -> list[str]:
    """이름만 적어 전달만 하는 환경변수."""
    return [e for e in _env_entries() if "=" not in e]


def test_compose_does_not_shadow_settings_defaults() -> None:
    """compose 가 settings.py 의 기본값을 다시 적으면 코드 수정이 운영에 도달하지 못한다.

    실제로 `LLM_PRO_TIMEOUT_SEC: ${LLM_PRO_TIMEOUT_SEC:-30.0}` 한 줄이 settings.py 의
    30→60 변경을 통째로 삼켰고, 웹 이력서 분석이 계속 타임아웃으로 죽었다(실제 소요 37.4초).
    조용히 무효가 되는 종류의 사고라 사람 눈으로는 못 잡는다 — 여기서 막는다.
    """
    settings_fields = {name.upper() for name in Settings.model_fields}
    offenders = sorted(
        key
        for key in _declared_defaults()
        if key in settings_fields and key not in COMPOSE_OWNED
    )
    assert not offenders, (
        "docker-compose.yml 이 settings.py 가 소유한 기본값을 덮어쓴다: "
        f"{offenders}. 값을 지우고 이름만 남겨 전달만 하거나"
        "(`- KEY`), 정말 compose 가 소유해야 하면 COMPOSE_OWNED 에 추가하고 이유를 적는다."
    )


def test_passthrough_keys_are_known_settings() -> None:
    """이름만 적힌 전달용 키가 settings.py 에 없으면 오타이거나 죽은 설정이다."""
    passthrough = _passthrough_keys()
    settings_fields = {name.upper() for name in Settings.model_fields}
    unknown = sorted(k for k in passthrough if k not in settings_fields)
    assert not unknown, f"settings.py 에 없는 환경변수를 전달하고 있다: {unknown}"


def test_no_empty_string_fallbacks_for_settings_keys() -> None:
    """`${VAR:-}` 는 미설정을 빈 문자열로 바꿔 코드 기본값을 덮는다 — 전달만 하게 둔다."""
    settings_fields = {name.upper() for name in Settings.model_fields}
    bad = sorted(
        key
        for key, value in _declared_defaults().items()
        if key in settings_fields and re.fullmatch(r"\$\{[A-Z_0-9]+:-\}", value)
    )
    assert not bad, f"빈 문자열 fallback 이 코드 기본값을 덮는다: {bad}"
