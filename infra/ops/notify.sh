#!/usr/bin/env bash
# 운영 알림 한 곳. 백업 스크립트와 헬스 감시가 같이 쓴다.
#
# 왜 파일로 빼는가 — 2026-09-27 점검에서 `infra/backup/README.md` 가 "실패 시 Discord
# 알림"을 **있다고 적어 뒀는데 스크립트에는 그런 코드가 없었다.** 문서만 보고 "실패하면
# 연락이 오겠지" 라고 믿으면 백업이 조용히 멈춰도 아무도 모른다. 보내는 쪽을 한 곳에
# 두고 양쪽이 같은 코드를 쓰게 한다.
#
# 규칙 두 가지:
#   · 웹훅이 없으면 조용히 넘어간다(로컬 개발에서 스크립트가 죽으면 안 된다).
#   · 알림 실패가 호출자를 죽이지 않는다 — 백업이 성공했는데 알림이 안 갔다고
#     백업을 실패로 만들면 안 된다. 반대도 마찬가지다.

ops_webhook() {
  # 전용 키가 있으면 그걸 쓰고, 없으면 기존 Discord 웹훅으로 떨어진다.
  # (현재 .env 의 DISCORD_WEBHOOK_URL 은 PR 알림 봇과 같은 채널이다 — 운영 알림을
  #  따로 받고 싶으면 OPS_ALERT_WEBHOOK_URL 을 채운다.)
  local w="${OPS_ALERT_WEBHOOK_URL:-${DISCORD_WEBHOOK_URL:-}}"
  if [ -z "$w" ] && [ -f "${OPS_ENV_FILE:-$HOME/stackup/.env}" ]; then
    w=$(grep -m1 '^OPS_ALERT_WEBHOOK_URL=' "${OPS_ENV_FILE:-$HOME/stackup/.env}" 2>/dev/null | cut -d= -f2-)
    [ -z "$w" ] && w=$(grep -m1 '^DISCORD_WEBHOOK_URL=' "${OPS_ENV_FILE:-$HOME/stackup/.env}" 2>/dev/null | cut -d= -f2-)
  fi
  printf '%s' "$w"
}

# ops_notify <level: ok|warn|fail> <title> <body>
ops_notify() {
  local level="$1" title="$2" body="$3"
  local webhook; webhook="$(ops_webhook)"
  [ -n "$webhook" ] || return 0

  local mark
  case "$level" in
    ok)   mark="[복구]" ;;
    warn) mark="[경고]" ;;
    *)    mark="[실패]" ;;
  esac

  # JSON 은 python 으로 만든다 — 메시지에 따옴표·줄바꿈·백슬래시가 들어와도 안전하다.
  # 셸 문자열 연결로 만들면 pg_dump 오류 문구 한 줄에 페이로드가 깨진다.
  local payload
  payload=$(TITLE="$mark $title" BODY="$body" HOST="$(hostname)" python3 -c '
import json, os
print(json.dumps({"content": "**%s**\n호스트: `%s`\n```\n%s\n```" % (
    os.environ["TITLE"], os.environ["HOST"], os.environ["BODY"][:1500])}))') || return 0

  curl -sS -m 15 -X POST -H 'Content-Type: application/json' \
       -d "$payload" "$webhook" >/dev/null 2>&1 || true
  return 0
}
