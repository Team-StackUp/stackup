# 시스템 아키텍처

> 컴포넌트 단위 책임 분담과 통신 규약을 정의한다. 각 컴포넌트의 **내부 구조**는 해당 레이어의 `CLAUDE.md`를 참고.

---

## 1. 컴포넌트 다이어그램

```
                        ┌─────────────────────────┐
                        │  Frontend (React)        │
                        │  CloudFront + S3 배포     │
                        └────────┬────────────────┘
                                 │
                        ┌────────▼────────────────┐
                        │  Nginx API Gateway       │
                        └───┬─────────────┬───────┘
                            │             │
              ┌─────────────▼──┐   ┌──────▼──────────────┐
              │ Core Server    │   │ RealTime Server      │
              │ (Spring Boot)  │   │ (Go)                 │
              │                │   │ - WebRTC             │
              │ - GitHub OAuth │   │ - WebSocket          │
              │ - 회원관리      │   │ - SSE                │
              │ - 세션/리포트   │   │ - 세션 실시간 관리     │
              │ - CRUD API     │   └──────────────────────┘
              └───────┬───────┘
                      │
              ┌───────▼───────┐
              │  RabbitMQ      │ ← Core ↔ AI 비동기 통신
              └───────┬───────┘
                      │
              ┌───────▼────────────────────┐
              │  AI Server (Python/FastAPI) │
              │  - LangChain RAG            │
              │  - 질문 / 꼬리질문            │
              │  - 이력서·레포 분석           │
              │  - 음성 분석                 │
              └───┬───────┬───────┬────────┘
                  │       │       │
          ┌───────▼┐ ┌───▼────┐ ┌▼──────────┐
          │External │ │Local   │ │ VectorDB  │
          │LLM APIs │ │LLM     │ │ (pgvector)│
          └────────┘ └────────┘ └───────────┘

              ┌──────────────┐     ┌────────────────┐
              │ PostgreSQL   │     │ Object Storage │
              │ + pgvector   │     │ (S3 / MinIO)   │
              └──────────────┘     └────────────────┘

              ┌──────────────┐
              │ Redis        │ ← 세션 ephemeral state
              └──────────────┘
```

---

## 2. 컴포넌트 책임 매트릭스

| 컴포넌트 | 책임 | 명시적 비책임 |
|----------|------|---------------|
| **Frontend** | UI 렌더링, 사용자 입력, 미디어 스트림 캡처, SSE 구독 | 비즈니스 로직, 인증 토큰 검증 |
| **Nginx** | 라우팅, TLS 종료, X-Trace-Id 부여 | 인증 처리 |
| **Core Server (Spring Boot)** | 인증·인가, CRUD, 트랜잭션, AI 작업 발행/콜백 처리, **PostgreSQL 단독 접근** | AI 추론, 실시간 스트리밍 |
| **RealTime Server (Go)** | WebRTC/WebSocket/SSE, 세션 ephemeral state, 미디어 스트리밍 | 영속 데이터 저장 |
| **RabbitMQ** | Core ↔ AI 비동기 메시지 큐 | RPC 동기 호출 대체 |
| **AI Server (FastAPI)** | LLM 호출, RAG 파이프라인, 임베딩, STT/TTS, 음성 분석 | 사용자 인증, REST CRUD |
| **PostgreSQL** | 영속 관계형 데이터 + 벡터 임베딩 (pgvector) | 대용량 바이너리 |
| **Redis** | 세션 일시 상태, 토큰 블랙리스트, TTL 캐시 | 영속 데이터 |
| **S3 / MinIO** | 이력서 PDF 원본, 분석 마크다운, 음성 오디오 | 메타데이터 (DB가 담당) |

---

## 3. 통신 규약

### 3.1 동기 (HTTP REST)

- Frontend → Nginx → **Core Server** (인증, CRUD)
- 베이스 경로: `/api/*`
- 인증: `Authorization: Bearer <access_token>`
- 추적: `X-Trace-Id` 헤더 (Nginx 또는 클라이언트가 부여)

### 3.2 비동기 (RabbitMQ)

- **Core → AI**: `ai.request.*` 익스체인지에 발행
- **AI → Core**: `ai.callback.*` 익스체인지로 결과 회신
- 메시지 스키마: [`messaging.md`](./messaging.md) 참조

### 3.3 실시간 (WebSocket / SSE)

- **Frontend ↔ RealTime Server**: WebSocket (면접 질문/답변), WebRTC (음성)
- **Frontend ← RealTime Server**: SSE (AI 작업 상태: QUEUED → PROCESSING → COMPLETED/FAILED)
- 이벤트 스펙: [`event-stream.md`](./event-stream.md) 참조

### 3.4 외부

- **AI Server → Gemini / OpenAI API**: HTTPS, 모델별 레이트 리밋 준수
- **Core Server → GitHub API**: REST v3 + GraphQL v4 혼용, OAuth access token 사용

---

## 4. 핵심 설계 원칙

### 4.1 PostgreSQL 단일 접근 주체

- **Core Server만** PostgreSQL에 직접 접근한다.
- AI Server, RealTime Server는 데이터가 필요하면:
  - REST API 호출 (Core 내부 API), 또는
  - RabbitMQ 메시지에 필요한 데이터를 동봉
- **이유**: 트랜잭션 경계 통일, 스키마 변경 영향 최소화, ORM 매핑 중복 방지.

### 4.2 LLM 이중 모델 전략

| 시점 | 모델 | 이유 |
|------|------|------|
| 세션 시작 시 | **Pro 모델** | 이력서 + GitHub 컨텍스트 기반 질문 풀 생성, 품질 우선 |
| 세션 중 (꼬리질문) | **Flash 모델 + RAG** | 3초 이내 응답, 저지연 우선 |
| 카메라 분석 | **Local LLM (MediaPipe)** | 비용·프라이버시 |

### 4.3 Hybrid Storage

| 데이터 종류 | 저장소 | 이유 |
|-------------|--------|------|
| 사용자 / 세션 / 메시지 메타 | PostgreSQL | 관계형 쿼리, 트랜잭션 |
| 벡터 임베딩 | PostgreSQL (pgvector) | DB 단일화 |
| 분석 마크다운 (이력서/레포) | S3 | 대용량 텍스트 |
| 면접 오디오 | S3 | 대용량 바이너리 |
| 이력서 원본 PDF | S3 | 대용량 바이너리 |
| 실시간 세션 상태 | Redis | 빠른 읽기/쓰기, TTL |

### 4.4 분산 추적

- 모든 서버는 `X-Trace-Id`를 수신·전파한다.
- 로그 출력 포맷에 `traceId`를 포함한다.
- RabbitMQ 메시지 헤더에도 동일 trace id를 포함한다.

상세: [`observability.md`](./observability.md)

---

## 5. 배포 토폴로지

| 환경 | 구성 |
|------|------|
| **로컬 개발** | Docker Compose (PG/RabbitMQ/MinIO) + 각 서버 독립 프로세스 |
| **운영** | K8s (Core/AI/RealTime), 관리형 서비스 미사용, RDS 대신 PG 컨테이너 |
| **정적 호스팅** | CloudFront + S3 (Frontend SPA) |

> Phase 1 시점에는 운영 배포 미정. 로컬 개발 환경 우선.

---

## 6. 컴포넌트 간 의존성 그래프

```
Frontend ──→ Core Server ──→ PostgreSQL
   │              │              ↑
   │              ├──→ S3 / MinIO
   │              ├──→ Redis
   │              ├──→ RabbitMQ ←──→ AI Server ──→ External LLM
   │              └──→ GitHub API                   ──→ pgvector (Core 경유)
   │
   └──→ RealTime Server ──→ Redis
                          └──→ Core Server (REST 내부 호출)
```

**의존성 역전 금지 케이스**:
- AI Server → PostgreSQL 직접 연결 ✗
- RealTime Server → RabbitMQ 발행 ✗ (Core를 통해)
- Frontend → AI Server 직접 호출 ✗

---

## 7. 추후 확장 시 고려 사항

- **AI Worker 수평 확장**: RabbitMQ consumer 다중화, 멱등성 보장 필수
- **세션 sticky routing**: WebRTC 세션 유지를 위해 Nginx 또는 K8s Ingress 레벨 sticky session
- **읽기 전용 리플리카**: 통계·히스토리 조회 부하가 늘면 PG read replica 도입 검토
