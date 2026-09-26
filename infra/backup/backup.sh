#!/usr/bin/env bash
# StackUp 운영 백업 — PostgreSQL 덤프 + MinIO 오브젝트 아카이브.
#
# 왜 있나: 2026-09-26 점검 시점까지 백업이 **하나도 없었다**. 모든 사용자 계정·면접
# 기록·피드백·이력서 파일이 도커 볼륨 단일 사본에만 존재했고, `docker compose down -v`
# 한 번이면 전부 사라지는 상태였다(infra/CLAUDE.md 가 그 명령을 초기화 절차로 안내한다).
#
# 한계: 같은 디스크에 저장하므로 **논리적 사고**(실수 삭제·잘못된 마이그레이션·손상)만
# 막는다. 디스크·호스트 장애는 못 막는다 — 원격 복제는 후속 과제(docs/operations.md).
set -Eeuo pipefail

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
