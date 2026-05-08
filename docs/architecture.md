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
              │                │   │ - SSE                │
              │ - GitHub OAuth │   │ - WebRTC (미디어)     │
              │ - 회원관리      │   │ - 세션 실시간 push    │
              │ - 세션/리포트   │   └──────────────────────┘
              │ - CRUD API     │
              │ - SSE 엔드포인트│   (RealTime이 분리되기 전까지는
              └───────┬───────┘    Core가 SSE 직접 제공)
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
              │  - 음성 분석 (Whisper STT)   │
              └───┬───────┬───────┬────────┘
                  │       │       │
          ┌───────▼┐ ┌───▼────┐ ┌▼──────────┐
          │External │ │Local   │ │ VectorDB  │
          │LLM APIs │ │LLM     │ │ (pgvector)│
          │(Gemini  │ │        │ │           │
          │ 3.1,    │ │        │ │           │
          │ Whisper)│ │        │ │           │
          └────────┘ └────────┘ └───────────┘

              ┌──────────────┐     ┌────────────────┐
              │ PostgreSQL   │     │ Object Storage │
              │ + pgvector   │     │ (S3 / MinIO)   │
              └──────────────┘     └────────────────┘
```

> Redis 미사용. 휘발성 데이터(OAuth state, 멱등 키, 질문 풀 캐시)는 PostgreSQL의 short-lived 레코드 또는 Core 서버 인메모리로 처리. ([§4.5](#45-redis-미사용-결정))

---

## 2. 컴포넌트 책임 매트릭스

| 컴포넌트 | 책임 | 명시적 비책임 |
|----------|------|---------------|
| **Frontend** | UI 렌더링, 사용자 입력, 미디어 스트림 캡처, SSE 구독 | 비즈니스 로직, 인증 토큰 검증 |
| **Nginx** | 라우팅, TLS 종료, X-Trace-Id 부여 | 인증 처리 |
| **Core Server (Spring Boot)** | 인증·인가, CRUD, 트랜잭션, AI 작업 발행/콜백 처리, **PostgreSQL 단독 접근**, SSE 엔드포인트 | AI 추론, 미디어 스트리밍 |
| **RealTime Server (Go)** *(미구축)* | WebRTC 미디어, SSE 분리 (필요 시점에 도입) | 영속 데이터 저장 |
| **RabbitMQ** | Core ↔ AI 비동기 메시지 큐 | RPC 동기 호출 대체 |
| **AI Server (FastAPI)** | LLM 호출, RAG 파이프라인, 임베딩, STT(Whisper)/TTS, 음성 분석 | 사용자 인증, REST CRUD |
| **PostgreSQL** | 영속 관계형 데이터 + 벡터 임베딩 (pgvector) + 휘발성 short-lived 레코드 | 대용량 바이너리 |
| **S3 / MinIO** | 이력서 PDF 원본, 분석 마크다운, 음성 오디오 | 메타데이터 (DB가 담당) |

---

## 3. 통신 규약

### 3.1 동기 (HTTP REST)

- Frontend → Nginx → **Core Server** (인증, CRUD)
- 베이스 경로: `/api/*`
- 인증: `Authorization: Bearer <access_token>`
- 추적: `X-Trace-Id` 헤더 (Nginx 또는 클라이언트가 부여)

### 3.2 비동기 (RabbitMQ)

- **Core → AI**: `stackup.core-to-ai` exchange
- **AI → Core**: `stackup.ai-to-core` exchange
- 메시지 envelope·routing key·재시도: [`messaging.md`](./messaging.md)

### 3.3 실시간 푸시 (SSE + WebSocket)

- **SSE (작업 상태)**: Frontend ← RealTime — AI 분석 진행 상태, 세션 알림. 단방향, EventSource 자동 재연결.
- **WebSocket (라이브 면접)**: Frontend ↔ RealTime — RT1은 면접 메시지 양방향 (FE answer → RealTime → Core `/internal/messages`), RT3은 음성 스트림.
- **선택 기준**:
  - 단방향 push만 필요 → SSE (트래픽·인프라 단순, 자동 재연결)
  - 양방향 + 저지연 필요 (라이브 면접, 음성) → WebSocket
- 이벤트 스펙: [`event-stream.md`](./event-stream.md)

### 3.4 미디어 (WebRTC)

- **Frontend ↔ RealTime Server (계획)**: WebRTC — 음성·영상 스트림
- Phase 2 도입 시 RealTime Server 분리

### 3.5 외부

- **AI Server → Gemini / OpenAI API**: HTTPS, 모델별 레이트 리밋 준수
- **AI Server → OpenAI Whisper API**: STT (선택: 셀프호스팅 `whisper.cpp`)
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
| 세션 시작 시 | **Gemini 3.1 Pro** | 이력서 + GitHub 컨텍스트 기반 질문 풀 생성, 품질 우선 |
| 세션 중 (꼬리질문) | **Gemini 3.1 Flash + RAG** | 3초 이내 응답, 저지연 우선 |
| STT (음성 → 텍스트) | **OpenAI Whisper API** | 한국어 + 개발 영어 혼용 정확도 우수 |
| 카메라 분석 | **Local LLM (MediaPipe)** | 비용·프라이버시 |

### 4.3 Hybrid Storage

| 데이터 종류 | 저장소 | 이유 |
|-------------|--------|------|
| 사용자 / 세션 / 메시지 메타 | PostgreSQL | 관계형 쿼리, 트랜잭션 |
| 벡터 임베딩 | PostgreSQL (pgvector) | DB 단일화 |
| 분석 마크다운 (이력서/레포) | S3 | 대용량 텍스트 |
| 면접 오디오 | S3 | 대용량 바이너리 |
| 이력서 원본 PDF | S3 | 대용량 바이너리 |
| OAuth state, 멱등 키, 질문 풀 캐시 | PostgreSQL short-lived 테이블 또는 Core 인메모리 | Redis 미사용 결정 (§4.5) |

### 4.4 분산 추적

- 모든 서버는 `X-Trace-Id`를 수신·전파한다.
- 로그 출력 포맷에 `traceId`를 포함한다.
- RabbitMQ 메시지 헤더에도 동일 trace id를 포함한다.

상세: [`observability.md`](./observability.md)

### 4.5 Redis 미사용 결정

**배경**: MVP 단계에서 컴포넌트 수를 줄이기 위함. Redis가 제공하던 기능들의 대안:

| 기존 Redis 용도 | 대안 |
|-----------------|------|
| OAuth state 5분 TTL | `oauth_states` 테이블 + 짧은 expires_at + cron으로 정리, 또는 stateless JWT |
| RabbitMQ 메시지 멱등 | `processed_messages` 테이블 (messageId UNIQUE + 24h 정리) 또는 인메모리 LRU + delivery_tag 조합 |
| 세션 진행 중 일시 상태 | `interview_sessions` row 자체에 보관 (낮은 빈도) |
| SSE pub/sub (멀티 인스턴스 fanout) | 단일 인스턴스 운영(MVP) → 인메모리 채널. 수평 확장 시점에 RabbitMQ fanout exchange로 대체 |
| refresh token 블랙리스트 | `refresh_tokens.is_revoked` 컬럼 (이미 존재) |

**재도입 조건**: SSE 동시 연결이 단일 Core 인스턴스 한계(약 1만)를 넘어서거나, 멱등 처리량이 DB 부담으로 가시화될 때.

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
   │              ├──→ RabbitMQ ←──→ AI Server ──→ External LLM (Gemini 3.1)
   │              │                              └→ Whisper API
   │              └──→ GitHub API                  └→ pgvector (Core 경유)
   │
   └──→ Core SSE 엔드포인트 (또는 RealTime 분리 시 그쪽)
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
- **SSE → Redis pub/sub 또는 RabbitMQ fanout**: 멀티 Core 인스턴스 운영 시점에 검토
- **Whisper 셀프호스팅**: 사용량 증가 시 비용 손익분기점에서 GPU 노드로 이전 (`faster-whisper`, `whisper.cpp`)
