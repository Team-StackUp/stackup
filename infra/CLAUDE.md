# Infra — 로컬·운영 인프라

> Docker Compose 기반 로컬 환경. PostgreSQL(+pgvector), RabbitMQ, MinIO(S3 호환).

상위: [`/CLAUDE.md`](../CLAUDE.md) · 환경변수: [`/docs/environment.md`](../docs/environment.md) · 메시징: [`/docs/messaging.md`](../docs/messaging.md)

---

## 1. 디렉토리 구조

```
infra/
├── postgres/
│   ├── Dockerfile        # pgvector/pgvector:pg17 + init.sql
│   └── init.sql          # CREATE EXTENSION vector
├── rabbitmq/
│   ├── Dockerfile
│   ├── rabbitmq.conf     # management.load_definitions
│   └── definitions.json  # exchanges / queues / bindings (자동 import)
└── minio/
    ├── Dockerfile
    └── init.sh           # 부트스트랩: 버킷 생성

# (루트)
docker-compose.yml         # 모든 인프라 서비스 + 헬스체크
.env.example               # 변수 카탈로그
```

---

## 2. 서비스 인벤토리

| 서비스 | 이미지 | 포트 | 책임 |
|--------|--------|------|------|
| `postgres` | `pgvector/pgvector:pg17` | 5432 | 영속 데이터 + 벡터 임베딩 |
| `rabbitmq` | `rabbitmq:management` (커스텀 빌드) | 5672 / 15672 | Core ↔ AI 비동기 메시징 |
| `minio` | `minio/minio` (커스텀 빌드) | 9000 / 9001 | S3 호환 객체 스토리지 |
| `minio-init` | `minio/mc` | — | 부트스트랩: 버킷 생성 후 종료 |

> **Redis 미사용** — 휘발성 데이터(OAuth state, 멱등 키, 질문 풀 캐시)는 PostgreSQL의 short-lived 레코드 또는 Core 서버 인메모리로 처리. 본 의사결정 배경: 컴포넌트 단순화 (1개 줄임), 운영 부담 감소.
> **AI 서버, Core 서버는 docker-compose 미포함** (현재는 호스트에서 직접 실행). 향후 추가 시 본 표에 등록.

---

## 3. 부팅·종료

```bash
# 부팅 (백그라운드)
docker compose up -d

# 로그 따라가기
docker compose logs -f postgres
docker compose logs -f rabbitmq

# 헬스 상태
docker compose ps

# 종료 (볼륨 보존)
docker compose down

# 종료 + 볼륨 삭제 (모든 데이터 날아감)
docker compose down -v
```

각 서비스에 `healthcheck`가 정의되어 있어 `depends_on: condition: service_healthy`로 의존 순서가 보장된다.

---

## 4. PostgreSQL

### 4.1 접속
```bash
docker exec -it stackup-postgres psql -U stackup -d stackup
```

### 4.2 pgvector
`init.sql`에서 `CREATE EXTENSION IF NOT EXISTS vector;` 실행됨.
임베딩 테이블 정의는 [`/docs/database.md §4`](../docs/database.md) 참조.

### 4.3 초기 스키마
- 현재 init.sql은 extension만 생성
- 테이블 DDL은 Core 서버의 **Flyway** 가 적용 (`backend/src/main/resources/db/migration/V*.sql`)
- 마이그레이션 정책: [`/docs/database.md §8`](../docs/database.md)

### 4.4 데이터 백업
```bash
docker exec stackup-postgres pg_dump -U stackup stackup > backup.sql
docker exec -i stackup-postgres psql -U stackup stackup < backup.sql
```

운영 단계에서는 자동 스냅샷 (RDS) 또는 cron 기반 dump.

---

## 5. RabbitMQ

### 5.1 토폴로지 (실제 `definitions.json` 기준)

**Exchanges (topic, durable)**:
- `stackup.core-to-ai` — Core → AI 작업 요청
- `stackup.ai-to-core` — AI → Core 결과 회신

**Queues (durable)**:
| Queue | Bound to | Routing Key | Consumer |
|-------|----------|-------------|----------|
| `ai.analyze.repository` | `stackup.core-to-ai` | `analyze.repository` | AI Server |
| `ai.analyze.resume` | `stackup.core-to-ai` | `analyze.resume` | AI Server |
| `ai.generate.questions` | `stackup.core-to-ai` | `generate.questions` | AI Server |
| `ai.generate.followup` | `stackup.core-to-ai` | `generate.followup` | AI Server |
| `core.callback.analysis` | `stackup.ai-to-core` | `callback.analysis` | Core Server |
| `core.callback.questions` | `stackup.ai-to-core` | `callback.questions` | Core Server |

> 추가 큐(피드백, 상태 알림, DLQ)는 정의 시점에 본 표 갱신.

### 5.2 관리 콘솔
- URL: http://localhost:15672
- 계정: `${RABBITMQ_USER}` / `${RABBITMQ_PASSWORD}` (default `stackup`/`stackup`)

### 5.3 정의 파일 변경 절차
`definitions.json` 은 **컨테이너 부팅 시 한 번만 import** 되는 게 아니라 `management.load_definitions` 옵션이 켜져 있으면 매번 적용된다.

새 큐/exchange/binding 추가:
1. `infra/rabbitmq/definitions.json` 편집
2. `docker compose restart rabbitmq` (또는 management UI에서 import)
3. [`/docs/messaging.md`](../docs/messaging.md) 의 토폴로지 섹션 갱신
4. 관련 발행자(Core)·소비자(AI) 코드 작성

### 5.4 메시지 직접 발행 (스모크 테스트)
```bash
docker exec stackup-rabbitmq rabbitmqadmin \
  -u "${RABBITMQ_USER:-stackup}" -p "${RABBITMQ_PASSWORD:-stackup}" \
  publish exchange=stackup.core-to-ai routing_key=analyze.resume \
  payload='{"messageId":"test-1","payload":{"resumeId":1,"s3Key":"resumes/raw/1/test.pdf"}}'
```

---

## 6. MinIO

### 6.1 접속
- API: http://localhost:9000 (S3 호환)
- Console: http://localhost:9001 (default `minioadmin`/`minioadmin`)

### 6.2 버킷
`minio-init` 서비스가 `${MINIO_BUCKET}` (default `stackup`)을 부팅 시 생성. 이미 있으면 skip.

### 6.3 mc CLI (호스트에서)
```bash
brew install minio/stable/mc

mc alias set local http://localhost:9000 minioadmin minioadmin
mc ls local/stackup
mc cp ./test.pdf local/stackup/resumes/raw/1/test.pdf
```

### 6.4 객체 키 컨벤션
[`/docs/storage.md §2`](../docs/storage.md) — bucket은 환경변수, key만 코드에 등장.

---

## 7. 환경 변수

`.env.example` 기준 (현재 정의된 변수만):
```dotenv
POSTGRES_DB=stackup
POSTGRES_USER=stackup
POSTGRES_PASSWORD=stackup
POSTGRES_PORT=5432

RABBITMQ_USER=stackup
RABBITMQ_PASSWORD=stackup
RABBITMQ_PORT=5672
RABBITMQ_MANAGEMENT_PORT=15672

MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_BUCKET=stackup
```

> Core 서버 / AI 서버용 변수(JWT_SECRET, GITHUB_OAUTH_*, GEMINI_API_KEY 등)는 각 레이어 `.env.example`에 분리.
> 전체 카탈로그: [`/docs/environment.md`](../docs/environment.md)

`.env`는 `.gitignore` 처리. `.env.example`만 커밋.

---

## 8. 볼륨 (영속 데이터)

```yaml
volumes:
  postgres_data:    # PG 데이터
  rabbitmq_data:    # MQ 메시지·정의
  minio_data:       # 객체
```

위치: `docker volume inspect stackup_postgres_data` 로 확인.
완전 초기화: `docker compose down -v`

---

## 9. 운영 환경 매핑

| 로컬 | 운영 (예상) |
|------|-------------|
| docker compose postgres | AWS RDS (PostgreSQL + pgvector) 또는 Aurora |
| docker compose rabbitmq | Amazon MQ (RabbitMQ) 또는 self-hosted on K8s |
| docker compose minio | AWS S3 |

운영 매니페스트(K8s, Terraform)는 별도 레포 또는 `infra/k8s/`, `infra/terraform/` 추가 시 작성.

---

## 10. 디버깅 팁

```bash
# 컨테이너 안에 들어가기
docker exec -it stackup-postgres bash
docker exec -it stackup-rabbitmq bash

# 네트워크 (컨테이너 간 연결 확인)
docker network inspect stackup_default

# 헬스체크 수동
docker exec stackup-postgres pg_isready -U stackup
docker exec stackup-rabbitmq rabbitmq-diagnostics check_port_connectivity
docker exec stackup-minio mc ready local

# 디스크 사용량
docker system df
```

---

## 11. 새 인프라 추가 절차

예: 새 컴포넌트(예: Elasticsearch) 추가 시
1. `infra/{name}/Dockerfile` (필요 시) + 설정 파일
2. `docker-compose.yml`에 서비스 추가 (포트, healthcheck, volume)
3. `.env.example` 변수 추가
4. 본 문서 §2, §7 갱신
5. 사용 컴포넌트(Core/AI) 환경변수 연결

---

## 12. 안티패턴

- ❌ 컨테이너 내부에 직접 데이터 만들고 의존하기 → 항상 init script로 멱등하게
- ❌ definitions.json 수동 변경 후 커밋 누락
- ❌ 운영 비밀(.env 본체) 커밋
- ❌ `docker compose down -v` 를 운영 데이터 위에서 실수로 실행
- ❌ 포트 하드코딩 (변수화 안 함)
