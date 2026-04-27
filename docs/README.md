# StackUp 문서 인덱스

> 이 디렉토리는 **레이어 비종속(cross-cutting) 문서**를 보관한다. 각 레이어 내부 규약은 해당 레이어의 `CLAUDE.md`를 참고한다.

---

## 1. 산출물 (학교 제출용)

| 파일 | 설명 |
|------|------|
| `1조(StackUp)UserStory (1).pdf` | User Story 60p 상세 (US-01 ~ US-31) |
| `1조(StackUp)Product Backlog (1).pdf` | Product Backlog (Epic, US, Acceptance, SP) |

> PDF는 **버전 픽스용 산출물**이므로 직접 수정하지 않는다. 변경 사항은 아래 `.md` 문서에 반영하고 PDF 재출력 시점에 동기화한다.

---

## 2. 횡단 관심사 문서

### 제품·아키텍처
- [`product-overview.md`](./product-overview.md) — 제품 비전, 페르소나, 핵심 차별점
- [`architecture.md`](./architecture.md) — 시스템 아키텍처, 컴포넌트 책임 분담
- [`data-flow.md`](./data-flow.md) — 핵심 시나리오별 데이터 흐름

### 데이터·계약
- [`database.md`](./database.md) — DDL, ENUM, 인덱싱, Flyway 정책
- [`api-conventions.md`](./api-conventions.md) — REST API 설계 규약, 에러 코드, 페이지네이션
- [`messaging.md`](./messaging.md) — RabbitMQ 큐/익스체인지, 메시지 스키마, 재시도 정책
- [`storage.md`](./storage.md) — S3(MinIO) 키 컨벤션, 버킷 설계
- [`event-stream.md`](./event-stream.md) — SSE 이벤트 스펙

### 디자인·프론트엔드
- [`design-system.md`](./design-system.md) — 토큰, 컬러, 타이포그래피, 컴포넌트 인벤토리
- [`ui-patterns.md`](./ui-patterns.md) — 반복되는 UX 패턴, 상태 처리 (loading/empty/error)

### 보안·운영
- [`security.md`](./security.md) — 인증·인가, 토큰 암호화, 개인정보 처리
- [`observability.md`](./observability.md) — X-Trace-Id, 로깅 레벨, AI 요청 로깅
- [`environment.md`](./environment.md) — 환경 변수, 로컬/스테이징/운영 분리

### 협업
- [`coding-conventions.md`](./coding-conventions.md) — 언어별 공통 코딩 규약
- [`git-conventions.md`](./git-conventions.md) — 브랜치 전략, 커밋 컨벤션, PR 템플릿
- [`testing-strategy.md`](./testing-strategy.md) — 테스트 피라미드, 핵심 시나리오 정의
- [`glossary.md`](./glossary.md) — 도메인 용어집 (한/영 대응)

---

## 3. 레이어별 진입점 (CLAUDE.md)

| 레이어 | 진입 문서 | 역할 |
|--------|-----------|------|
| 루트 | `/CLAUDE.md` | 전체 오버뷰 + 인덱스 |
| 프론트 | `/frontend/CLAUDE.md` | React/FSD 구조 |
| 백엔드 | `/backend/CLAUDE.md` | Spring Boot Core 서버 |
| AI | `/ai/CLAUDE.md` | FastAPI/LangChain 서버 |
| 인프라 | `/infra/CLAUDE.md` | Docker Compose, PG/RabbitMQ/MinIO |

> 각 레이어 하위 디렉토리에도 슬라이스/도메인별 `CLAUDE.md`가 존재할 수 있다. 작업할 디렉토리에서 가장 가까운 `CLAUDE.md`를 우선 참고한다.

---

## 4. 문서 작성 원칙

1. **단일 출처 원칙(Single Source of Truth)**: 같은 정보를 두 곳에 쓰지 않는다. 다른 문서를 링크로 참조한다.
2. **레이어 종속성 방향**: 횡단 관심사 → 레이어 → 슬라이스. 상위 문서는 하위를 모르고, 하위는 상위를 참조한다.
3. **변경 시 함께 갱신**: 코드 변경이 컨벤션·아키텍처·DB 스키마에 영향을 주면 해당 문서도 같은 PR에 포함한다.
4. **예시 우선**: 추상적 설명보다 실제 코드 스니펫·DDL·요청 예시로 보여준다.
5. **한국어 우선**: 도메인 용어는 한국어로, 기술 용어·식별자(클래스명·테이블명·ENUM)는 영문 그대로 둔다.
