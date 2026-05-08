# StackUp — Claude 컨텍스트 (루트)

> **IT 직군 멀티모달 AI 면접 시뮬레이터.** 본 문서는 전체 오버뷰 + 모든 컨텍스트 문서의 **인덱스**다. 작업 디렉토리에서 가장 가까운 `CLAUDE.md`를 먼저 읽고, 필요한 횡단 관심사는 `docs/`에서 찾는다.

---

## 0. 한 줄 요약

GitHub 레포 + 이력서 → AI가 분석 → 개인 맞춤 모의 면접 → 음성·비언어적 분석 포함 종합 피드백.

상세 비전·페르소나·메트릭: [`docs/product-overview.md`](./docs/product-overview.md)

---

## 1. 컴포넌트 한눈에

```
Frontend (React)
    ↓
Nginx Gateway
    ↓
┌────────────────┐    ┌──────────────────────┐
│ Core (Spring)  │ ←→ │ RealTime (Go)         │
└─────┬──────────┘    └──────────────────────┘
      │ RabbitMQ
      ↓
┌──────────────────────────────┐
│ AI (FastAPI/LangChain)       │
└──────────────────────────────┘

PostgreSQL+pgvector  ·  S3/MinIO
```

자세한 다이어그램과 책임 매트릭스: [`docs/architecture.md`](./docs/architecture.md)

---

## 2. 레이어별 진입점 (CLAUDE.md)

| 레이어 | 위치 | 기술 |
|--------|------|------|
| 프론트엔드 | [`frontend/CLAUDE.md`](./frontend/CLAUDE.md) | React 19 + TypeScript + Vite, FSD 구조 |
| 백엔드 (Core) | [`backend/CLAUDE.md`](./backend/CLAUDE.md) | Java 21 + Spring Boot 4 + JPA + QueryDSL |
| RealTime 서버 | [`realtime/CLAUDE.md`](./realtime/CLAUDE.md) | Go 1.26 + chi + amqp091-go, SSE/WebSocket |
| AI 서버 | [`ai/CLAUDE.md`](./ai/CLAUDE.md) | Python 3.13 + FastAPI + LangChain |
| 인프라 | [`infra/CLAUDE.md`](./infra/CLAUDE.md) | Docker Compose: PG(+pgvector), RabbitMQ, MinIO |

각 레이어 하위 슬라이스/도메인 패키지에도 `CLAUDE.md`가 배치되어 있을 수 있다. 작업할 디렉토리에서 가장 가까운 것을 우선 읽는다.

---

## 3. 횡단 관심사 인덱스

| 분류 | 문서 |
|------|------|
| 제품 비전 | [`docs/product-overview.md`](./docs/product-overview.md) |
| 시스템 아키텍처 | [`docs/architecture.md`](./docs/architecture.md) |
| 데이터 흐름 (시나리오) | [`docs/data-flow.md`](./docs/data-flow.md) |
| 데이터베이스 (DDL, ENUM, 인덱스) | [`docs/database.md`](./docs/database.md) |
| REST API 규약 | [`docs/api-conventions.md`](./docs/api-conventions.md) |
| RabbitMQ 메시징 | [`docs/messaging.md`](./docs/messaging.md) |
| 객체 스토리지 (S3/MinIO) | [`docs/storage.md`](./docs/storage.md) |
| SSE 이벤트 스펙 | [`docs/event-stream.md`](./docs/event-stream.md) |
| **디자인 시스템** | [`docs/design-system.md`](./docs/design-system.md) |
| UI 패턴 (4-state, 폼, 토스트, 키보드) | [`docs/ui-patterns.md`](./docs/ui-patterns.md) |
| 보안 (인증, 암호화, 개인정보) | [`docs/security.md`](./docs/security.md) |
| 옵저버빌리티 (trace, 로그, AI 비용) | [`docs/observability.md`](./docs/observability.md) |
| 환경 변수 카탈로그 | [`docs/environment.md`](./docs/environment.md) |
| 코딩 컨벤션 | [`docs/coding-conventions.md`](./docs/coding-conventions.md) |
| Git 컨벤션 (브랜치·커밋·PR) | [`docs/git-conventions.md`](./docs/git-conventions.md) |
| 테스트 전략 | [`docs/testing-strategy.md`](./docs/testing-strategy.md) |
| **용어집 (한↔영, ENUM)** | [`docs/glossary.md`](./docs/glossary.md) |

전체 인덱스: [`docs/README.md`](./docs/README.md)

산출물 (학교 제출용 PDF):
- `docs/1조(StackUp)UserStory (1).pdf`
- `docs/1조(StackUp)Product Backlog (1).pdf`

---

## 4. 핵심 설계 원칙 (전 컴포넌트 공통)

1. **PostgreSQL 단독 접근** — Core 서버만 직접 접근. AI/RealTime은 API 또는 RabbitMQ 경유. ([architecture §4.1](./docs/architecture.md))
2. **Hybrid Storage** — 관계형 → PG, 임베딩 → pgvector, 대용량 → S3. (Redis 사용 안 함 — 휘발성 데이터는 DB short-lived 레코드 또는 Core 인메모리)
3. **LLM 이중 모델** — 세션 시작은 Pro(품질), 꼬리질문은 Flash + RAG(저지연 < 3s).
4. **분산 추적** — 모든 요청에 `X-Trace-Id` 전파. RabbitMQ 메시지 헤더에도 동일.
5. **MVP 우선** — 필요해질 때 추가, 미래 가정 테이블/기능 만들지 않음.
6. **도메인 우선 패키징** — 백엔드는 layered가 아닌 도메인 패키지로 구성.
7. **레이어별 컨텍스트** — 작업 시 가장 가까운 `CLAUDE.md`부터 읽는다.

---

## 5. 팀 구성

| 이름 | 역할 |
|------|------|
| 박상우 (SM) | Backend Core (Spring Boot, OAuth, 세션·리포트 API, DB) |
| 정준모 | AI 서빙 (LangChain/RAG, STT/TTS, 프롬프트) |
| 조서현 (PO) | RealTime 서버 (Go), Core-AI 연동, GitHub 분석 |
| 신재호 | Frontend (React UI, 미디어 스트림, 웹캠) |

---

## 6. 빠른 시작

```bash
# 1. 환경 변수
cp .env.example .env
cp frontend/.env.example frontend/.env.local    # 도입 후
cp ai/.env.example ai/.env

# 2. 인프라 부팅
docker compose up -d

# 3. 각 서버 실행
cd backend && ./gradlew bootRun
cd ai && uv sync && uv run uvicorn ai_server.main:app --reload
cd frontend && npm install && npm run dev
```

상세: [`infra/CLAUDE.md §3`](./infra/CLAUDE.md), [`docs/environment.md §10`](./docs/environment.md)

---

## 7. 작업 시 체크리스트

신규 기능 PR 머지 전:

- [ ] 가장 가까운 `CLAUDE.md`의 규약 준수
- [ ] 단위 테스트 추가 (해피 + 경계값)
- [ ] DB 스키마 변경 시 Flyway 마이그레이션 + [`docs/database.md`](./docs/database.md) 갱신
- [ ] 새 API 시 [`docs/api-conventions.md`](./docs/api-conventions.md) 준수 + OpenAPI 자동 생성
- [ ] 새 메시지 시 [`docs/messaging.md`](./docs/messaging.md) §1, §5 갱신 + `infra/rabbitmq/definitions.json`
- [ ] 새 환경변수 시 `.env.example` 동기화 + [`docs/environment.md`](./docs/environment.md)
- [ ] 보안 체크리스트 통과 [`docs/security.md §10`](./docs/security.md)
- [ ] PR 본문 템플릿 채우기 (`.github/PULL_REQUEST_TEMPLATE.md`)

---

## 8. 메모

- Phase 1 (MVP) 진행 중. US-01 ~ US-20 우선.
- 디자인 시스템 토큰 파일 미생성 — 디자인 적용 첫 PR에서 `frontend/src/app/styles/tokens.css` 생성.
- Spring Security, RabbitMQ starter, Flyway는 백엔드 기능 작성 PR과 함께 도입.
- `infra/rabbitmq/definitions.json`은 현재 exchange 3개 (`stackup.core-to-ai`, `stackup.ai-to-core`, `stackup.realtime`), 큐 7개 정의. 피드백 큐는 US-24 작업 시 추가.
- **Redis 미사용** — 휘발성 데이터(OAuth state, 멱등 키, 질문 풀 캐시)는 PostgreSQL의 short-lived 레코드 또는 Core 서버 인메모리로 처리.
- 실시간 푸시는 **SSE + WebSocket 병행**. SSE는 작업 상태(RT2), WebSocket은 라이브 면접 메시지(RT1) 및 음성 스트림(RT3). 미디어 시그널링은 추후 WebRTC 도입 시점에 검토.
- `RealTime Server (Go)`는 `realtime/` 디렉토리에 부트스트랩 완료. 현재 SSE만 활성, WS 엔드포인트는 US-Session-03 / US-Voice-01에서 도입.

문서 변경 원칙: 코드와 함께 같은 PR에 포함. 단일 출처 원칙(SSOT) 유지.
