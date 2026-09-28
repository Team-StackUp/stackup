#!/usr/bin/env bash
# infra/ops 자가 테스트. **팀 채널로 아무것도 보내지 않는다** — 로컬 수신기로만 보낸다.
#
# 왜 레포에 두는가 — 이 스크립트들을 만들면서 실제로 두 번 깨졌고 둘 다 조용한 실패였다:
#   1. 임베디드 파이썬 따옴표 이스케이프가 깨져 판정이 빈 값 → 감시가 그냥 통과
#   2. `[ -n "$previous" ] && ops_notify ...` 단락으로 정상인데 exit 1 → cron 이 실패로 봄
# 셸 스크립트는 "돌려보면 되겠지" 로 넘어가기 쉬운데, 이 둘은 돌려봐도 눈에 안 띈다.
#
# 사용: bash infra/ops/selftest.sh     (python3, curl 필요)
set -u
set +m          # 리스너를 죽일 때 "Terminated" 잡 제어 메시지가 결과에 섞이지 않게
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PORT="${SELFTEST_PORT:-8896}"
RECV=$(mktemp); HEALTH=$(mktemp); STATE=$(mktemp -u)
pass=0; fail=0

check() { # check <설명> <기대> <실제>
  if [ "$2" = "$3" ]; then printf '  ok   %s\n' "$1"; pass=$((pass+1))
  else printf '  FAIL %s  (기대=%s 실제=%s)\n' "$1" "$2" "$3"; fail=$((fail+1)); fi
}

RECV="$RECV" HEALTH="$HEALTH" PORT="$PORT" python3 - <<'PY' &
import http.server, socketserver, os
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = open(os.environ["HEALTH"], "rb").read()
        self.send_response(200); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        with open(os.environ["RECV"], "a") as f:
            f.write(self.rfile.read(n).decode() + "\n")
        self.send_response(204); self.end_headers()
    def log_message(self, *a): pass
socketserver.TCPServer.allow_reuse_address = True
socketserver.TCPServer(("127.0.0.1", int(os.environ["PORT"])), H).serve_forever()
PY
LISTENER=$!
disown $LISTENER 2>/dev/null || true   # 종료 시 셸이 "Terminated" 를 찍지 않게
trap 'kill $LISTENER 2>/dev/null; rm -f "$RECV" "$HEALTH" "$STATE"' EXIT
sleep 1

export OPS_ALERT_WEBHOOK_URL="http://127.0.0.1:$PORT/hook"
export OPS_ENV_FILE=/dev/null
export HEALTH_URL="http://127.0.0.1:$PORT/health"
export OPS_STATE_FILE="$STATE"

sent() { local n=0; [ -s "$RECV" ] && n=$(grep -c . "$RECV"); printf '%s' "$n"; }
UP='{"status":"UP","components":{"database":{"status":"UP"},"backup":{"status":"UP"}}}'
DEGRADED='{"status":"UP","components":{"database":{"status":"UP"},"backup":{"status":"DOWN"}}}'

echo "[notify.sh]"
. ./notify.sh
ops_notify fail '따옴표 "큰" 작은'"'"' 백슬래시 \x' '두 줄
째'
check 'JSON 이 깨지지 않는다' 1 "$(python3 -c "
import json,sys
try:
    json.loads(open('$RECV').readline()); print(1)
except Exception: print(0)")"
: > "$RECV"
( unset OPS_ALERT_WEBHOOK_URL DISCORD_WEBHOOK_URL; . ./notify.sh; ops_notify fail x y )
check '웹훅 없으면 무발송' 0 "$(sent)"
( export OPS_ALERT_WEBHOOK_URL="http://127.0.0.1:9/dead"; . ./notify.sh; set -e
  ops_notify fail x y ) >/dev/null 2>&1
check '죽은 엔드포인트가 호출자를 죽이지 않는다' 0 $?

echo "[health-check.sh]"
: > "$RECV"; rm -f "$STATE"
echo "$UP" > "$HEALTH"
./health-check.sh; check '첫 실행 정상: exit 0' 0 $?
check '첫 실행 정상: 무발송' 0 "$(sent)"
./health-check.sh; check '정상 반복: exit 0' 0 $?
check '정상 반복: 무발송' 0 "$(sent)"

echo "$DEGRADED" > "$HEALTH"
./health-check.sh; check '집계 UP·backup DOWN 포착: exit 0' 0 $?
check '집계 UP·backup DOWN 포착: 1건' 1 "$(sent)"
./health-check.sh
check '같은 이상 반복: 발송 늘지 않음' 1 "$(sent)"

echo "$UP" > "$HEALTH"
./health-check.sh; check '복구: exit 0' 0 $?
check '복구: 2건' 2 "$(sent)"

# 리스너를 죽이는 대신 죽은 포트를 가리킨다 — kill 하면 셸이 "Terminated" 를
# 결과 사이에 끼워 넣어 읽기 나빠진다(리스너는 EXIT trap 이 정리한다).
HEALTH_URL="http://127.0.0.1:9/health" ./health-check.sh
check '도달 불가: exit 0' 0 $?
check '도달 불가: UNREACHABLE 로 기록' UNREACHABLE "$(cat "$STATE")"

echo
echo "통과 $pass, 실패 $fail"
[ "$fail" -eq 0 ]
