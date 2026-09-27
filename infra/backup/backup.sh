#!/usr/bin/env bash
# StackUp 운영 백업 — PostgreSQL 덤프 + MinIO 오브젝트 아카이브.
#
# 왜 있나: 2026-09-26 점검 시점까지 백업이 **하나도 없었다**. 모든 사용자 계정·면접
# 기록·피드백·이력서 파일이 도커 볼륨 단일 사본에만 존재했고, `docker compose down -v`
# 한 번이면 전부 사라지는 상태였다(infra/CLAUDE.md 가 그 명령을 초기화 절차로 안내한다).
#
# 한계: **같은 호스트**에 저장하므로 논리적 사고(실수 삭제·잘못된 마이그레이션·손상)와
# 데이터 디스크 고장까지는 막지만, 호스트 자체를 잃으면 같이 사라진다 — 원격 복제는
# 후속 과제. (운영 확인: 데이터는 /dev/nvme0n1p3, 백업은 /dev/nvme1n1p1 로 **다른 물리
# 디스크**다. 이전 주석은 "같은 디스크"라고 적혀 있었는데 사실이 아니었다.)
set -Eeuo pipefail

# ── 실패 알림 ─────────────────────────────────────────────────────────────────
# README 는 2026-09-26 부터 "실패 시 Discord 알림"을 안내했지만 **그 코드가 없었다.**
# 문서만 보고 "실패하면 연락이 오겠지" 라고 믿는 것이 가장 나쁜 상태다 — 조용히 멈춘
# 백업과 정상 백업이 구분되지 않는다. 여기서 실제로 보낸다.
#
# trap 으로 건다: fail() 뿐 아니라 set -e 로 죽는 예상 못 한 실패(디스크 가득, docker
# 데몬 중단 등)까지 잡아야 한다. 그런 것들이 정확히 "아무도 모르게" 실패하는 종류다.
#
# ERR 이 아니라 **EXIT 하나만** 쓴다. 둘 다 걸면 set -e 로 죽을 때 ERR→EXIT 순으로
# 연달아 떠서 같은 실패를 두 번 알린다.
# 로그 경로는 BACKUP_DIR 정의 **이전에** trap 이 걸리므로 기본값을 여기서도 펼친다
# (set -u 라 미정의 변수를 그냥 참조하면 알림 함수 자체가 죽는다).
# shellcheck source=/dev/null
. "$(cd "$(dirname "${BASH_SOURCE[0]}")/../ops" && pwd)/notify.sh"

backup_failed() {
  local rc=$?
  [ "$rc" -eq 0 ] && return 0
  local logfile="${BACKUP_LOG:-${BACKUP_DIR:-$HOME/backups}/backup.log}"
  ops_notify fail "백업 실패 (exit=$rc)" \
    "$(tail -n 20 "$logfile" 2>/dev/null || echo '(로그 없음)')"
}
trap backup_failed EXIT

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
PG_CONTAINER="${PG_CONTAINER:-stackup-postgres}"
PG_USER="${PG_USER:-stackup}"
PG_DB="${PG_DB:-stackup}"
MINIO_VOLUME="${MINIO_VOLUME:-stackup_minio_data}"

stamp="$(date +%Y%m%d-%H%M%S)"
log() { echo "[$(date +%H:%M:%S)] $*"; }
fail() { log "FAILED: $*"; exit 1; }

mkdir -p "$BACKUP_DIR"

# ── PostgreSQL ────────────────────────────────────────────────────────────────
# -Fc(custom) 은 압축 + pg_restore 로 선택 복원이 가능하다. 평문 SQL 보다 낫다.
pg_file="$BACKUP_DIR/pg-$stamp.dump"
log "pg_dump -> $(basename "$pg_file")"
docker exec "$PG_CONTAINER" pg_dump -U "$PG_USER" -Fc "$PG_DB" > "$pg_file" \
  || fail "pg_dump 실패"

# 덤프가 실제로 복원 가능한 형식인지 즉시 검증한다 — 0바이트나 깨진 파일을
# 성공으로 세면 백업이 있다는 착각만 남는다(오늘 고친 버그들과 같은 함정).
docker exec -i "$PG_CONTAINER" pg_restore --list > /dev/null < "$pg_file" \
  || fail "덤프 검증 실패 — 복원 불가능한 파일"
pg_tables=$(docker exec -i "$PG_CONTAINER" pg_restore --list < "$pg_file" | grep -c "TABLE DATA" || true)
[ "$pg_tables" -gt 0 ] || fail "덤프에 테이블 데이터가 없다 (tables=$pg_tables)"

# ── MinIO (이력서·음성·TTS·분석 원문) ──────────────────────────────────────────
minio_file="$BACKUP_DIR/minio-$stamp.tar.gz"
log "minio archive -> $(basename "$minio_file")"
# --user 없이 돌리면 결과물이 root 소유가 되고, 아래 보존 삭제(find -delete)가
# 조용히 실패해 아카이브가 무한정 쌓인다.
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$MINIO_VOLUME":/src:ro -v "$BACKUP_DIR":/dst busybox \
  tar czf "/dst/$(basename "$minio_file")" -C /src . || fail "MinIO 아카이브 실패"
tar tzf "$minio_file" > /dev/null || fail "MinIO 아카이브 검증 실패"

# ── 보존 ──────────────────────────────────────────────────────────────────────
deleted=$(find "$BACKUP_DIR" -maxdepth 1 -name 'pg-*.dump' -o -name 'minio-*.tar.gz' \
  | wc -l)
find "$BACKUP_DIR" -maxdepth 1 \( -name 'pg-*.dump' -o -name 'minio-*.tar.gz' \) \
  -mtime +"$RETENTION_DAYS" -delete

# 성공 시각 마커. cron 출력은 아무도 안 보므로, "언제 마지막으로 성공했는가" 를
# 파일 하나로 남겨 둔다 — 백업이 조용히 멈춘 것과 정상인 것을 구분할 유일한 단서다.
date -Iseconds > "$BACKUP_DIR/LAST_SUCCESS"

log "done. pg=$(du -h "$pg_file" | cut -f1) (tables=$pg_tables), minio=$(du -h "$minio_file" | cut -f1), 보관=$deleted개, 보존=${RETENTION_DAYS}일"

# 성공으로 끝났으니 trap 을 해제한다. (rc=0 이면 backup_failed 가 그냥 빠져나오지만,
# 명시적으로 풀어 두는 편이 의도가 드러난다.)
trap - EXIT
