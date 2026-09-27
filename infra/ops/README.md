# 운영 알림 (`infra/ops`)

만들어 둔 신호를 **읽는 쪽**이다.

## 왜 있나

2026-09-27 전체 점검에서 두 가지가 드러났다.

1. **`infra/backup/README.md` 가 "실패 시 Discord 알림"을 안내했는데 그 코드가 없었다.**
   문서만 보고 "실패하면 연락이 오겠지" 라고 믿는 상태가 알림이 아예 없다고 아는 것보다 나쁘다.
2. **`/api/system/health` 를 아무도 보고 있지 않았다.** `database`·`rabbitmq`·`s3`·`aiServer`·
   `backup`·`scheduler` 여섯 컴포넌트를 만들어 두고도 DOWN 이 떠도 알 방법이 없었다.
   특히 `backup`(백업 신선도)과 `scheduler`(스위퍼 생존)는 애초에 **"조용히 멈추는 것"** 을
   잡으려고 만든 신호인데, 읽는 쪽이 없으면 만들지 않은 것과 같다.

## 파일

| 파일 | 역할 |
|---|---|
| `notify.sh` | 보내는 곳 한 군데. `ops_notify <ok\|warn\|fail> <제목> <본문>` |
| `health-check.sh` | 헬스 폴링 + **상태가 바뀔 때만** 알림 |

`infra/backup/backup.sh` 도 `notify.sh` 를 소스해서 실패를 알린다.

## 설치

```cron
*/5 * * * * /home/stackup/stackup/infra/ops/health-check.sh >> /home/stackup/backups/health.log 2>&1
```

## 설계 결정

**상태가 바뀔 때만 보낸다.** 5분마다 같은 말을 보내면 아무도 안 읽게 되고, 그러면 진짜 장애
때도 안 읽는다. 마지막 상태를 `~/.stackup-health-state` 에 두고 비교한다.

**첫 실행이 정상이면 아무것도 안 보낸다.** "처음부터 정상"은 알릴 일이 아니다.

**집계(`status`)가 아니라 개별 컴포넌트까지 본다.** `backup`·`scheduler` 는 의도적으로
aggregate 에서 빠져 있어서(informational — "복구 수단이 없다"와 "서비스가 죽었다"는 다르다),
집계만 보면 정확히 이 감시가 잡아야 할 것을 놓친다.

**도달 불가도 장애로 센다.** `UNREACHABLE` 은 "UP 인데 못 읽는" 것보다 나쁘다.

**해석 실패도 장애로 센다.** 임베디드 파이썬이 깨지면 판정이 빈 값이 되고 감시가 조용히
무력해진다 — 실제로 개발 중에 따옴표 이스케이프 때문에 이 상태가 났고 테스트가 잡았다.
`UNPARSEABLE` 로 떨어뜨린다. '모르는 상태'와 '정상'을 섞으면 감시가 아니다.

**알림 실패가 호출자를 죽이지 않는다.** 백업이 성공했는데 알림이 안 갔다고 백업을 실패로
만들면 안 된다. 웹훅이 없으면 조용히 넘어간다(로컬 개발에서 스크립트가 죽으면 안 된다).

**JSON 은 python 으로 만든다.** 셸 문자열 연결로 만들면 `pg_dump` 오류 한 줄에 따옴표나
백슬래시가 들어오는 순간 페이로드가 깨진다 — 알림이 가장 필요한 순간에.

## 채널

`OPS_ALERT_WEBHOOK_URL` → 없으면 `DISCORD_WEBHOOK_URL`(현재 PR 알림 봇과 같은 채널).
운영 알림을 따로 받고 싶으면 전용 웹훅을 `.env` 에 넣으면 그쪽으로 옮겨간다.

## 검증 방법 (팀 채널로 보내지 않고)

```bash
# 로컬 수신기를 띄우고 그쪽으로 보낸다
python3 -m http.server 8897 &   # 실제로는 POST 를 받는 핸들러 필요
OPS_ALERT_WEBHOOK_URL=http://127.0.0.1:8897/hook ./health-check.sh
```

웹훅 **유효성**은 게시 없이 확인할 수 있다 — Discord 웹훅 URL 에 `GET` 하면 이름과
channel_id 를 돌려준다.
