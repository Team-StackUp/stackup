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

# 상태 파일은 두 줄이다: 1) 마지막으로 **알린** 상태 2) 아직 확정되지 않은 비정상 후보.
# 예전에는 한 줄(마지막 관측 상태)이었고, 그래서 **한 번만 나쁘면 바로 알렸다.**
#
# 그게 배포마다 헛울렸다. 배포는 컨테이너를 재생성하므로 그 직후 몇십 초 동안
#   · scheduler = 아직 첫 박동 전 (백엔드에서 따로 고쳤다)
#   · aiServer  = 큐 컨슈머 0 (AI 컨테이너가 다시 붙는 중)
# 이 정상적으로 관측된다. 지표만 보고는 "배포 중"과 "죽었다"를 구분할 수 없다 —
# 구분되는 건 **지속 시간**뿐이다. 그래서 같은 비정상을 연속 2회(≈5분 간격) 봐야 알린다.
#
# 대가: 진짜 장애 감지가 최대 한 주기 늦는다(≈10분). 스위퍼 주기가 2~5분인 서비스라
# 감당할 수 있고, 배포마다 헛울려 아무도 안 읽는 알림보다 낫다.
# 한계: 한 번 걸러 한 번씩 나빠지는 플래핑은 잡지 못한다.
previous=$(sed -n '1p' "$STATE_FILE" 2>/dev/null || echo "")
pending=$(sed -n '2p' "$STATE_FILE" 2>/dev/null || echo "")

write_state() { printf '%s\n%s\n' "$1" "${2:-}" > "$STATE_FILE"; }

if [ "$current" = "$previous" ]; then
  write_state "$previous" ""  # 후보 취소 — 알린 상태로 되돌아왔다
  exit 0                      # 같은 상태 반복 — 조용히 넘어간다
fi

# 복구는 즉시 알린다. 지연시킬 이유가 없고(나쁜 소식이 아니다), 늦추면 "아직도 장애중"
# 으로 오해한다. 단, 애초에 알린 적 없는 비정상의 '복구'는 보내지 않는다 —
# previous 가 UP 이면 위 분기에서 이미 걸러진다.
if [ "$current" != "UP" ] && [ "$current" != "$pending" ]; then
  write_state "$previous" "$current"   # 1회차 — 확정 보류
  exit 0
fi

write_state "$current" ""

if [ "$current" = "UP" ]; then
  # 첫 실행(previous 없음)에 "복구됨"을 보내지 않는다 — 처음부터 정상인 게 정상이다.
  #
  # `[ -n "$previous" ] && ops_notify ...` 로 쓰면 안 된다. previous 가 비었을 때
  # && 가 단락되어 **스크립트가 1로 끝난다** — 정상인데 cron 이 실패로 보고,
  # 매 첫 실행마다 메일/로그 소음이 난다. 감시 도구가 정상 상태에서 실패 코드를
  # 내면 "이 cron 이 원래 그래" 가 되고, 진짜 실패도 같이 무시된다.
  if [ -n "$previous" ]; then
    ops_notify ok "헬스 복구 (이전: $previous)" "$detail"
  fi
else
  ops_notify fail "헬스 이상: $current" "$detail"
fi

# 알림 전송 실패가 이 스크립트의 종료 코드를 오염시키지 않게 명시적으로 끝낸다.
# (ops_notify 는 항상 0 을 돌려주지만, 마지막 명령의 상태에 의존하지 않는다.)
exit 0
