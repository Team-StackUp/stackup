# 운영 백업

2026-09-26 이전까지 **백업이 하나도 없었다.** 사용자 계정·면접 기록·피드백·이력서 파일이
전부 도커 볼륨 단일 사본에만 있었고, `docker compose down -v`(이 레포가 초기화 절차로
안내하는 명령) 한 번이면 전부 사라지는 상태였다.

## 무엇을 받는가

| 대상 | 방식 | 크기(2026-09-26) |
|---|---|---|
| PostgreSQL | `pg_dump -Fc` (압축 + 선택 복원 가능) | 688 KB |
| MinIO 오브젝트 | 볼륨 tar.gz (이력서·음성·TTS·분석 원문) | 246 MB |

RabbitMQ 볼륨은 받지 않는다 — 큐는 재선언되고 메시지는 휘발성이다.

## 언제 도는가

`crontab` 매일 04:00 KST. 보관 14일(`BACKUP_RETENTION_DAYS`), 저장 위치 `~/backups`
(`BACKUP_DIR`).

```cron
0 4 * * * /home/stackup/stackup/infra/backup/backup.sh >> /home/stackup/backups/backup.log 2>&1
```

## 백업이 살아 있는지 확인

cron 출력은 아무도 보지 않으므로 **성공 시각 마커**를 남긴다. 이게 하루 이상 낡았으면
백업이 멈춘 것이다.

```bash
cat ~/backups/LAST_SUCCESS     # 예: 2026-09-26T15:44:58+09:00
ls -lh ~/backups/
tail -20 ~/backups/backup.log
```

## 복원

```bash
# PostgreSQL — 임시 DB 로 먼저 받아 확인한 뒤 교체하는 것을 권장
docker exec stackup-postgres psql -U stackup -d postgres -c "CREATE DATABASE restore_test"
docker exec -i stackup-postgres pg_restore -U stackup -d restore_test --no-owner < ~/backups/pg-<stamp>.dump
docker exec stackup-postgres psql -U stackup -d restore_test -c "SELECT count(*) FROM interview_sessions"

# MinIO — 컨테이너를 내리고 볼륨에 풀어 넣는다
docker compose stop minio
docker run --rm -v stackup_minio_data:/dst -v ~/backups:/src busybox \
  sh -c "rm -rf /dst/* && tar xzf /src/minio-<stamp>.tar.gz -C /dst"
docker compose start minio
```

2026-09-26 에 실제 복원을 검증했다 — 20개 테이블, `users`/`interview_sessions`/
`interview_messages`/`session_feedbacks`/`document_embeddings`/`analyzed_documents`/
`resumes` 행 수가 원본과 전부 일치.

## 한계 — 아직 못 막는 것

**같은 디스크에 저장한다.** 실수 삭제·잘못된 마이그레이션·논리적 손상은 막지만
**디스크·호스트 장애는 못 막는다.** 원격 복제(S3·다른 호스트 rsync)는 후속 과제다.

또한 스크립트가 실패하면 cron 은 조용하다. `LAST_SUCCESS` 를 주기적으로 보는 사람이
없으면 결국 같은 함정이므로, 헬스체크에 마커 신선도를 넣는 것이 다음 개선이다.
