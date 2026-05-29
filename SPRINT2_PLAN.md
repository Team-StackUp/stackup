# Sprint 2 — 텍스트 면접 E2E 작업 계획

> 범위: **US-13 ~ US-20**. 세션 생성 → 첫 질문 → 텍스트 답변 → 꼬리질문 → 세션 종료까지의 골든 패스 완성. 피드백 리포트(US-24)는 Sprint 3로 분리.

작성일: 2026-05-29 / 기준 브랜치: `main` (Sprint 1 #30까지 머지 완료)

---

## 1. Sprint 1 정리

- 열린 PR 없음. Sprint 1 마지막 PR(#30, Sprint1 백엔드 및 AI서버 API 연동)이 5/28에 머지됨.
- 머지 완료: auth(GitHub OAuth + JWT), resume 업로드/분석, GitHub repo 등록/분석, document 메타·요약·임베딩 upsert, SSE stream-token + 분석 상태 푸시, RabbitMQ DLX·DLQ + 재시도 정책(US-28), `.env` 자동 sync 워크플로.
- **Sprint 2 진입 준비 완료 상태.**

---

## 2. 현재 면접 도메인 진척도

| 레이어 | 상태 |
|---|---|
| **DB** | 7개 테이블(`interview_sessions`, `interview_messages`, `session_contexts`, `message_voice_analyses`, `session_feedbacks` 등) + 모든 ENUM 이미 마이그레이션됨 |
| **Backend `session/`** | [`InterviewSession.java`](backend/src/main/java/com/stackup/stackup/session/domain/InterviewSession.java), [`InterviewMessage.java`](backend/src/main/java/com/stackup/stackup/session/domain/InterviewMessage.java), Repository, Enum 까지만. `application/` · `presentation/` · `infrastructure/` 패키지 **부재** |
| **AI 서버** | `analyze.resume / .repository / .web` 컨슈머만 본 구현. `generate.questions` · `generate.followup` 큐는 정의만, **코드 없음** ([`ai/CLAUDE.md §16`](ai/CLAUDE.md)) |
| **Frontend** | [`features/interview/index.ts`](frontend/src/features/interview/index.ts), [`pages/Interview/index.ts`](frontend/src/pages/Interview/index.ts), [`features/feedback/index.ts`](frontend/src/features/feedback/index.ts), [`pages/History/index.ts`](frontend/src/pages/History/index.ts) 모두 1줄 빈 export 스캐폴딩 |
| **RealTime** | SSE + RabbitMQ `realtime.session.*` bridge 동작 중. Core 가 publish 만 하면 그대로 fan-out |
| **메시징 스펙** | [`docs/messaging.md §5.6~5.9`](docs/messaging.md), envelope · 큐 · DLQ 모두 확정 |

데이터·흐름·SSE 이벤트 카탈로그 모두 사양화 완료 ([`docs/data-flow.md §3`](docs/data-flow.md), [`docs/event-stream.md §3.3~3.4`](docs/event-stream.md)) — **사양은 다 있고 코드만 채우면 됨.**

---

## 3. 작업 분해 — 트랙별

### 트랙 A — Backend session 도메인

세션 생성부터 종료까지의 트랜잭션 · 메시지 발행 · SSE push 전부.

#### A-1. Application / Presentation 계층 신설
- `session/application/SessionService`
  - `create(cmd)` — `interview_sessions` INSERT(`status=READY`) + `session_contexts` INSERT
  - `start(id)` — `READY → IN_PROGRESS`, `started_at` 세팅
  - `submitAnswer(id, content)` — INTERVIEWEE 메시지 INSERT + `generate.followup` 발행
  - `end(id, reason)` — `IN_PROGRESS → COMPLETED`, `ended_at`
  - `get(id)`, `list(userId, pageable)`, `getMessages(id)`
- `session/application/dto/` — `SessionCreateCommand`, `SessionResult`, `MessageSubmitCommand`, `MessageResult`
- `session/presentation/SessionController`
  - `POST /api/sessions`
  - `POST /api/sessions/{id}/start`
  - `POST /api/sessions/{id}/messages`
  - `POST /api/sessions/{id}/end`
  - `GET /api/sessions/{id}`
  - `GET /api/sessions`
  - `GET /api/sessions/{id}/messages`

#### A-2. RabbitMQ 발행 (`session/infrastructure/SessionEventPublisher`)
- 세션 생성 commit 후 `generate.questions` 발행 (`@TransactionalEventListener(AFTER_COMMIT)`, [`docs/messaging.md §3`](docs/messaging.md))
- 답변 INSERT commit 후 `generate.followup` 발행

#### A-3. RabbitMQ 컨슈머 (`session/infrastructure/SessionCallbackHandler`)
`core.callback.questions` 구독, `payload.kind`로 분기.
- `POOL` — 질문 풀 저장 + 첫 질문 `interview_messages` INSERT + SSE `session.message` push + (옵션) `realtime.session.notify` 발행
- `FOLLOWUP` — INTERVIEWER 메시지 INSERT(`parent_message_id` 매핑) + SSE push + `total_question_count >= max_questions` 시 자동 종료 트리거

#### A-4. 세션 종료 정책
- 백엔드 컷: `total_question_count >= max_questions` 또는 timeout
- `interview_sessions.status=COMPLETED`, `ended_at`, SSE `session.state` push

#### A-5. ArchUnit 룰 위반 점검 ([`backend/CLAUDE.md §16`](backend/CLAUDE.md))

#### A-6. 테스트
- `SessionServiceTest` (Mockito)
- `SessionControllerTest` (`@WebMvcTest`)
- 컨슈머 통합 테스트 (Testcontainer RabbitMQ + PG)

---

### 트랙 B — AI 서버 질문 / 꼬리질문

[`ai/CLAUDE.md §14`](ai/CLAUDE.md) 절차 따라.

#### B-1. 메시지 모델
- `model/messages/generate.py` — `GenerateQuestionsPayload`, `GenerateFollowupPayload`, `CallbackQuestionsPayload`

#### B-2. 컨슈머 2종
- `messaging/consumers/questions_consumer.py`
- `messaging/consumers/followup_consumer.py`
- 멱등(messageId) 패턴은 기존 컨슈머 동일

#### B-3. 질문 풀 생성 체인 (`chain/question_pool_chain.py`)
- 입력: `interviewType`, `jobCategory`, `documentIds`, `maxQuestions`
- RAG: Core 내부 API `POST /api/internal/embeddings/search` 로 컨텍스트 chunk fetch → 프롬프트 주입
- LLM: **Pro 모델** (Gemini 3.1 Pro), Pydantic 출력 파서로 `[{ category, question }]` 강제
- 프롬프트: `chain/prompts/question_pool.py` 신규

#### B-4. 꼬리질문 체인 (`chain/followup_chain.py`)
- 입력: 직전 질문 / 답변, sessionId
- **Flash 모델** + 답변 평가 스키마(`specificity`, `logic`, `structure`)
- SLA < 3 초 — 응답 토큰 cap, streaming 미사용

#### B-5. 임베딩 provider
- **운영/개발: [`GeminiEmbeddingProvider`](ai/src/ai_server/rag/embedder.py) 사용** (이미 구현되어 있음). 설정에서 default 로 스위치
- **테스트: `MockEmbeddingProvider`** 유지 (단위/통합 테스트 결정론 확보)
- [`ai/CLAUDE.md §16`](ai/CLAUDE.md) 의 "MockEmbeddingProvider default" 기술은 본 스프린트에서 갱신

#### B-6. 콜백 발행
- `callback.questions` (`kind=POOL` / `FOLLOWUP`)

#### B-7. `ai_request_logs`
- Core API 또는 자체 publish 로 input/output 토큰 · latency 기록

#### B-8. 테스트
- `FakeListLLM` mock 으로 chain 단위
- Testcontainer 로 컨슈머 통합

---

### 트랙 C — Frontend Interview UX

[`frontend/CLAUDE.md §5`](frontend/CLAUDE.md) 라우트 가이드 기준.

#### C-1. 라우터 등록
- `/sessions/new`, `/sessions/:id`, `/history`, `/history/:id`
- `RequireAuth` wrap

#### C-2. `features/interview/`
- `api/interview.ts` — create / start / submitAnswer / end / get / list / getMessages
- `model/types.ts` — `Session`, `InterviewMessage`, ENUM 미러 ([`docs/glossary.md`](docs/glossary.md))
- `model/useSession.ts`, `useSessionMessages.ts`, `useSubmitAnswer.ts`
- `ui/SessionConfigForm.tsx` — mode / type / jobCategory / maxQuestions / maxDuration + `analyzed_documents` 멀티셀렉트(컨텍스트 문서)
- `ui/MessageList.tsx`, `ui/AnswerInput.tsx`, `ui/SessionControls.tsx` (start, end, 진행률)
- `ui/QuestionPendingState.tsx` — AI 풀 생성 대기용 4-state pending

#### C-3. SSE 연동
- 기존 [`useEventStream.ts`](frontend/src/shared/hooks/useEventStream.ts) 에 `session.message` · `session.state` 핸들러 추가
- `GET /api/stream/sessions/{sessionId}` — Sprint 1 의 stream-token API 재사용

#### C-4. `pages/Interview/`
- `NewSessionPage` (config form 만)
- `InterviewPage` (`:id`, AsyncBoundary 로 감싸고 진행 중 / 종료 분기)

#### C-5. `pages/History/`
- 세션 리스트(상태 뱃지) · 세션 상세(메시지 시퀀스)

#### C-6. 테스트
- Vitest 로 hook
- Playwright 로 "세션 생성 → 답변 3회 → 종료" 골든 패스

---

### 트랙 D — RealTime 서버

현재 `q.realtime.session.notify` 컨슈머가 SSE 로 fan-out 됨. **Core 가 publish 만 시작하면 별도 작업 없음.** 다만 확인할 것:

- Core 가 `q.realtime.session.notify` 로 publish vs 자기 인메모리 SSE 둘 중 어디로 보낼지 정책 통일 ([`docs/event-stream.md §6`](docs/event-stream.md)) — Phase 1 은 Core 인메모리로도 충분. RealTime 경유로 가면 멀티 인스턴스 대비 가능. **Sprint 2 에선 인메모리 + 별도 publish 둘 다 가능** — 결정 필요
- session 권한 검증(`/api/stream/sessions/:id` 본인 세션 검사) 이 이미 들어가 있는지 확인

---

## 4. 결정 사항 / 미결정 사항

### 4.1 확정된 결정 (2026-05-29)

| # | 항목 | 결정 | 근거 |
|---|---|---|---|
| 1 | **임베딩 provider** | 운영 / 개발은 [`GeminiEmbeddingProvider`](ai/src/ai_server/rag/embedder.py) 사용 (이미 구현됨), 테스트는 `MockEmbeddingProvider` 유지 | Mock 만 쓰면 RAG 검색이 noise, 신규 구현체 도입 불필요 |
| 2 | **첫 질문 push 방식** | Core 가 `callback.questions (POOL)` 수신 즉시 첫 질문을 `interview_messages` INSERT + SSE `session.message` push | round-trip · race 회피, SSE 인프라 재사용 |
| 3 | **SSE 전송 경로** | Phase 1 은 Core 인메모리 단일화 | RT2 (분석 상태) 와 경로 통일, 디버깅 비용 ↓. RealTime 서버는 멀티 인스턴스 / 음성 단계에서 본격 활용 |
| 4 | **답변 멱등키** | `POST /api/sessions/{id}/messages` 한정으로 `Idempotency-Key` 헤더 도입 (Frontend UUID 발급 → Core 가 `processed_messages` 테이블에 24h 캐시) | `(session_id, sequence_number) UNIQUE` 깨짐 방지, SSE 재연결 + 자동 재시도 시 중복 INSERT 차단 |

### 4.2 미결정 사항 (Sprint 2 진입 전 픽스 필요)

| # | 항목 | 책임 | 메모 |
|---|---|---|---|
| 5 | 세션 종료 후 피드백 (US-24) Sprint 3 로 미루는지 확정 | PO | 본 계획은 분리를 가정. Sprint 2 종료 시점에 `/sessions/:id/feedback` placeholder 만 두는 안 검토 |
| 6 | Flyway 추가 마이그레이션 필요 여부 | Core | 핵심 결정: **질문 풀 저장 위치** — `interview_sessions.question_pool JSONB` 컬럼 추가 vs 별도 테이블 vs Core 인메모리. 컬럼 추가 시 마이그레이션 1 개 필요 |

---

## 5. 권장 진행 순서

1. **미결정 사항 5·6 회의로 픽스** (10 분)
2. **메시지 스키마 정합성 PR** — envelope 모델만 Core / AI 양쪽에 추가 → 트랙 A · B 의 unblock
3. **트랙 A · B · C 병렬 진행** (각각 PR 1~2 개)
4. **통합 시나리오 테스트** — 세션 생성 → 첫 질문 → 답변 → 꼬리질문 → 종료 골든 패스 E2E
5. **트랙 D 정책 통일** + 멀티 인스턴스 대비 (선택)

Sprint 1 PR 들이 평균 1~2 개 도메인씩 끊어 머지된 패턴을 보면, Sprint 2 도 트랙별 PR 2~3 개씩(총 8~10 PR)으로 끊는 게 자연스러움.

---

## 6. Definition of Done

- [ ] `/sessions/new` 에서 컨텍스트 문서를 골라 세션 생성 가능
- [ ] 세션 생성 후 첫 질문이 SSE 로 도착
- [ ] 답변 제출 → < 3 초 내 꼬리질문 push
- [ ] `maxQuestions` 도달 시 자동 종료, `/history` 에 COMPLETED 표시
- [ ] `/history/:id` 에서 전체 메시지 트리 조회 가능
- [ ] DLQ 격리 정책 ([`docs/messaging.md §6`](docs/messaging.md)) 가 generate.* 큐에도 적용 확인
- [ ] `ai_request_logs` 에 질문 풀 / 꼬리질문 호출 기록
- [ ] 골든 패스 E2E 테스트 통과
