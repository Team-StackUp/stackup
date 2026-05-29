# `session` — 면접 세션 도메인 가이드

> 상위 컨텍스트: [`/backend/CLAUDE.md`](../../../../../CLAUDE.md), [`/backend/src/main/java/com/stackup/stackup/CLAUDE.md`](../CLAUDE.md)

---

## 1. Aggregate

`InterviewSession` (root). 연관:
- `InterviewMessage` (질문/답변 시퀀스 — `(session_id, sequence_number) UNIQUE`)
- `SessionContext` (세션 ↔ `AnalyzedDocument` N:M)
- `MessageVoiceAnalysis` (Phase 2 — 음성)
- `SessionFeedback` (Sprint 3 — 종합 평가)

`InterviewMessage`, `SessionContext` 등 자식 aggregate 는 Repository 가 별도이며 service 가 명시적으로 INSERT/조회.

## 2. 상태 전이 (`SessionStatus`)

```
       create()
            ↓
READY ──markInProgress()──> IN_PROGRESS ──end()──> COMPLETED
  │                              │
  └──cancel()──> CANCELLED       └──cancel()──> CANCELLED
                                 │
                                 └──(network 등)──> INTERRUPTED  *(현재 코드 진입 없음)*
```

전이 가드는 `InterviewSession` 도메인 메서드 내부 `IllegalStateException`. 서비스 계층에서는 `SessionInvalidStateException`(`SESSION_INVALID_STATE` → HTTP 422) 으로 사전 차단.

## 3. API endpoints (`/api/sessions`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 세션 생성 (READY) + `generate.questions` 발행 |
| POST | `/{id}/messages` | 답변 제출 (`Idempotency-Key` 헤더). `generate.followup` 발행 |
| POST | `/{id}/end` | 세션 종료 / 취소 |
| GET | `/{id}` | 단건 조회 |
| GET | `/` | 페이지 목록 (생성일 DESC) |
| GET | `/{id}/messages` | 메시지 시퀀스 ASC |

## 4. 메시지

발행 (`session.application.SessionService`):
- `generate.questions` — 세션 생성 commit 후 AFTER_COMMIT 이벤트 (첫 질문 요청)
- `generate.followup` — 답변 commit 후 AFTER_COMMIT (꼬리질문 요청)

소비 (`session.infrastructure.SessionCallbackHandler` → `session.application.SessionCallbackService`):
- `core.callback.questions` 큐 — `kind=FIRST/FOLLOWUP/END` 분기
  - `FIRST` → `markInProgress` + 첫 INTERVIEWER 메시지 INSERT + SSE
  - `FOLLOWUP` → INTERVIEWER 메시지 INSERT + max 도달 시 자동 종료 + SSE
  - `END` → AI 가 조기 종료 신호. `session.end()` + SSE
  - 멱등: `processed_messages` (`messageId` PK)

## 5. 답변 멱등 정책

`POST /messages` 의 `Idempotency-Key` 헤더 → `processed_messages` 의 PK `session.answer:{uuid}` 로 24h 캐시. 중복 호출 시 가장 최근 INTERVIEWEE 메시지를 그대로 반환 (멱등 충돌 시 시퀀스 중복 방지).

## 6. SSE 이벤트

- `SESSION_MESSAGE` — 새 INTERVIEWER 메시지 도착 시 (FIRST/FOLLOWUP)
- `SESSION_STATE` — 상태 전이 또는 카운트 변화 시
- `ERROR` — 콜백 페이로드 비정상 (예: 빈 question)

전송 경로: Core 인메모리 (`SseEventPublisher.publishToSession`). RealTime 서버 미사용 (Phase 1).

## 7. 안티패턴

- 컨트롤러에서 `@Transactional` (ArchUnit 차단)
- 엔티티에 `@Setter` (ArchUnit 차단)
- Service 에 클래스 레벨 `@Transactional` + `@TransactionalEventListener` 혼용 (Spring 7 제약 — `AnalysisRequestService` 패턴 참조)
- `Map.of(...)` 에 null 값 (NPE)
