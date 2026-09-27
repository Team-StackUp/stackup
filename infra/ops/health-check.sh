#!/usr/bin/env bash
# /api/system/health 를 주기적으로 보고 상태가 바뀔 때만 알린다.
#
# 왜 있나 — 2026-09-27 점검 결과 **헬스 엔드포인트를 아무도 보고 있지 않았다.**
# database·rabbitmq·s3·aiServer·backup·scheduler 여섯 컴포넌트를 잘 만들어 두고도
# DOWN 이 떠도 알 방법이 없었다. 백업 신선도(backup)와 스케줄러 생존(scheduler)은
# 애초에 "조용히 멈추는 것"을 잡으려고 만든 신호인데, 그 신호를 읽는 쪽이 없으면
# 만들지 않은 것과 같다.
#
# 상태가 **바뀔 때만** 보낸다. 5분마다 같은 말을 보내면 아무도 안 읽게 되고,
# 그러면 진짜 장애 때도 안 읽는다.
set -uo pipefail

HEALTH_URL="${HEALTH_URL:-https://stack-up.shop/api/system/health}"
STATE_FILE="${OPS_STATE_FILE:-$HOME/.stackup-health-state}"
TIMEOUT="${HEALTH_TIMEOUT:-20}"

# shellcheck source=/dev/null
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/notify.sh"

body=$(curl -sS -m "$TIMEOUT" "$HEALTH_URL" 2>/dev/null)
curl_rc=$?

if [ $curl_rc -ne 0 ] || [ -z "$body" ]; then
  # 도달 불가도 장애다. 오히려 "UP 인데 못 읽는" 경우보다 나쁘다.
  current="UNREACHABLE"
  detail="curl exit=$curl_rc, url=$HEALTH_URL"
else
  read -r current detail < <(BODY="$body" python3 -c "
import json, os, sys
try:
    d = json.loads(os.environ['BODY'])
except Exception:
    print('UNPARSEABLE', os.environ['BODY'][:200].replace(chr(10), ' '))
    sys.exit()
comps = d.get('components', {}) or {}
bad = [k + '=' + str(v.get('status')) for k, v in comps.items() if v.get('status') != 'UP']
# 집계 상태만 보면 informational 컴포넌트(backup·scheduler)의 DOWN 을 놓친다 —
# 그게 정확히 이 감시가 잡으려는 것이다. 개별 컴포넌트까지 본다.
overall = d.get('status', 'UNKNOWN')
state = overall if not bad else 'DEGRADED'
detail = '전체=' + overall + (' / 비정상: ' + ', '.join(bad) if bad else '')
print(state, detail)
")
  # 파싱 자체가 죽으면(문법 오류 등) current 가 비어 감시가 조용히 무력해진다.
  # 빈 값은 장애로 취급한다 — '모르는 상태'와 '정상'을 섞으면 감시가 아니다.
  [ -n "${current:-}" ] || { current="UNPARSEABLE"; detail="헬스 응답 해석 실패"; }
fi

previous=$(cat "$STATE_FILE" 2>/dev/null || echo "")
printf '%s' "$current" > "$STATE_FILE"

if [ "$current" = "$previous" ]; then
  exit 0                      # 같은 상태 반복 — 조용히 넘어간다
fi

if [ "$current" = "UP" ]; then
  # 첫 실행(previous 없음)에 "복구됨"을 보내지 않는다 — 처음부터 정상인 게 정상이다.
  [ -n "$previous" ] && ops_notify ok "헬스 복구 (이전: $previous)" "$detail"
else
  ops_notify fail "헬스 이상: $current" "$detail"
fi
