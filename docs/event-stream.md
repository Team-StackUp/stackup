# SSE 이벤트 스펙

> AI 작업 진행 상태와 면접 메시지를 프론트엔드에 실시간 푸시하기 위한 Server-Sent Events 스펙.
> **양방향 WebSocket 미사용** — 본 프로젝트의 푸시는 모두 SSE 단일 경로. 트래픽·인프라 효율 + EventSource 자동 재연결의 이점.
> 미디어 스트림(음성/영상)만 WebRTC 사용 (별도).

---

## 1. 엔드포인트

```
GET /realtime/stream/me                  # user 채널 SSE (userId는 토큰에서 추출)
GET /realtime/stream/sessions/{sessionId} # session 채널 SSE (feedback.ready 등 비-라이브)
GET /realtime/stream/documents/{documentId}
WS  /realtime/sessions/{sessionId}        # RT1 라이브 텍스트 면접 (양방향)
WS  /realtime/sessions/{sessionId}/audio  # RT3 실시간 음성 답변 스트림 (오디오 업/자막 다운)
```

- 제공 주체: **RealTime Server (Go)**. Core는 직접 SSE를 서빙하지 않고 `stackup.realtime` exchange로 발행만 한다 (RealTime이 consume → fan-out).

### WS 라이브 면접 (RT1)
- 경로 `WS /realtime/sessions/{id}` (SSE `/realtime/stream/*` 와 다른 path → Upgrade 분기 없음). 인증 동일 `?access_token=`.
- 서버→클라 프레임(JSON): `{ "id": <eventId>, "event": "<SseEventType 대문자>", "data": {"data": <payload>, "traceId": "..."} }` (session 채널 fan-out을 그대로 전달 — 질문/꼬리질문/세션상태). `event` 는 enum 이름(`SESSION_MESSAGE`/`SESSION_STATE`/`FEEDBACK_READY`), **소문자 `session.message` 아님**. `SESSION_MESSAGE` 의 payload 는 **messageId 뿐** → 본문은 `GET …/messages` 로 조회(§3.3).
- 클라→서버: `{ "type": "answer", "content": "...", "idempotencyKey"?: "..." }`. RealTime이 Core 내부 REST(`POST /api/internal/sessions/{id}/messages`)로 프록시 → 답변 INSERT + `generate.followup` 발행.
- 인증: 쿼리 토큰 `?access_token=<stream-token>` (EventSource/WS 헤더 한계 우회). RealTime `internal/auth`가 HS256(키=`SHA-256(JWT_SECRET)`)로 검증.
- 권한 (리소스 스코프): 토큰은 `resourceType`(`USER`/`SESSION`)·`resourceId` claim을 담는다. 소유권은 **발급 시점**에 Core가 검증하고(USER=`POST /api/auth/stream-token` 본인, SESSION=`POST /api/sessions/{id}/stream-token` 소유권 체크 후 발급), RealTime은 path 리소스와 토큰 리소스의 일치만 확인한다(불일치 → 403). 이로써 RealTime은 PG 무접근으로 소유권을 판정한다. `documents/{id}` 채널 스코프는 MVP deferred(인증 토큰만).
- 연결 유지: 약 30초마다 `: ping <unix-ts>` heartbeat 코멘트 송신 (`REALTIME_SSE_PING_INTERVAL`).

### WS 실시간 음성 스트림 (RT3)
- 경로 `WS /realtime/sessions/{id}/audio?access_token=<stream-token>&messageId=<placeholder messageId>`. 인증·리소스 스코프는 RT1과 동일(`SESSION` 토큰 ↔ path id 일치). `messageId`는 사전에 Core `POST /api/sessions/{id}/messages/voice/stream-begin`이 만든 placeholder 메시지 id.
- RealTime은 **순수 오디오 양방향 프록시**다(오디오 내용을 해석하지 않음). 브라우저 WS ↔ AI WS(`/internal/voice/stream`)를 프레임 타입 보존하며 양방향 복사한다.
- **업(클라→서버, 바이너리)**: 마이크 오디오 프레임(예: `audio/webm` opus). 프레임을 그대로 AI로 전달한다.
- **다운(서버→클라, JSON)** — AI가 발행, RealTime이 그대로 전달:
  - `{ "type": "transcript.partial", "text": "안녕하세" }` — 부분(interim) 자막
  - `{ "type": "transcript.final", "text": "안녕하세요", "messageId": 50 }` — 발화(utterance) 확정 조각
  - `{ "type": "error", "code": "..." }` — 처리 오류
- **제어(클라→서버, 텍스트)**: `{ "type": "stop" }` — 발화 종료. AI가 남은 최종 transcript flush 후 메트릭(WPM/filler/silence)을 계산해 `callback.voice`를 발행하고 연결을 닫는다. 이후 기존 followup 파이프라인(다음 질문 생성)으로 이어진다.

---

## 2. 이벤트 포맷

> - `event` 이름은 **`SseEventType` enum 이름(대문자)**: `DOC_STATE`·`REPO_STATE`·`SESSION_MESSAGE`·`SESSION_STATE`·`FEEDBACK_READY`·`ERROR`·`KEEP_ALIVE` (+ AI 가 직접 발행하는 `ANALYSIS_PROGRESS`). **`session.message` 같은 소문자 점표기가 아니다.** (RealTime `bridge/dispatcher.go` `Type: env.Payload.EventType` → `sse.go`/`ws.go` 가 그대로 전달.)
> - `data` 봉투는 `{"data": <payload>, "traceId": "..."}` 다 (`realtime/CLAUDE.md §8`). payload 필드는 camelCase.
> - 클라는 SSE `addEventListener(<ENUM_NAME>, …)` / WS `frame.event === '<ENUM_NAME>'` 로 매칭한다.

표준 SSE 프레임:
```
event: SESSION_MESSAGE
id: <eventId>
data: {"data": <payload>, "traceId": "..."}

```

WS(RT1)는 같은 내용을 JSON 한 줄 프레임으로: `{ "id": <eventId>, "event": "SESSION_MESSAGE", "data": {"data": <payload>, "traceId": "..."} }`.

---

## 3. 이벤트 카탈로그

### 3.1 분석 상태 (`event: DOC_STATE`)
```json
{
  "type": "DOC_STATE",
  "payload": {
    "documentType": "RESUME",
    "documentId": 42,
    "state": "PROCESSING",
    "progress": 0.4,
    "message": "임베딩 생성 중"
  }
}
```
- `state` ∈ `QUEUED | PROCESSING | COMPLETED | FAILED`
- `progress` 0.0~1.0 (옵션)

### 3.2 레포 분석 (`event: REPO_STATE`)
```json
{
  "type": "REPO_STATE",
  "payload": {
    "repositoryId": 7,
    "state": "ANALYZED",
    "summaryUrl": "/api/documents/123"
  }
}
```

### 3.2-1 분석 단계 진행 (`event: ANALYSIS_PROGRESS`)

분석이 끝나기 전, 진행 단계를 사용자에게 보여주기 위한 **휘발성** 이벤트.
종료 상태(`DOC_STATE`/`REPO_STATE`)와 달리 DB/쿼리 캐시를 건드리지 않고 화면의 진행 문구만 갱신한다.

```json
{
  "data": {
    "targetType": "REPOSITORY",
    "targetId": 4,
    "phase": "EMBEDDING",
    "message": "분석 결과를 임베딩하는 중…"
  },
  "traceId": "…"
}
```

- `targetType` ∈ `REPOSITORY | RESUME`
- `phase` ∈ `EXTRACTING | SUMMARIZING | EMBEDDING`
- **발행 경로**: AI 서버 → (`stackup.realtime` exchange, `realtime.user.notify`) → RealTime → user 채널 SSE. Core 를 거치지 않는다(진행 정보는 영속 대상이 아님). 종료 상태만 기존대로 AI → Core 콜백 → REPO_STATE/DOC_STATE 로 전달.
- 프론트는 `ANALYSIS_PROGRESS` 수신 시 진행 store 갱신(쿼리 무효화 X), `REPO_STATE`/`DOC_STATE`(종료) 수신 시 진행 store clear + 목록 쿼리 무효화.

### 3.3 세션 메시지 푸시 (`event: SESSION_MESSAGE`)

**`data` 는 변경된 메시지 본문이 아니라 messageId(숫자)만 담는 "변경 알림"이다.** Core `QuestionsCallbackService` 가 `RealtimeNotifyEvent.session(id, SESSION_MESSAGE, message.getId())` 로 발행하기 때문. 클라는 이 이벤트를 받으면 `GET /api/sessions/{id}/messages` 로 본문을 조회한다(Workspace 의 분석 SSE→쿼리 무효화와 동일 패턴). 질문·꼬리질문·STT transcript 반영 등 메시지 시퀀스가 바뀔 때마다 발행.

```json
{ "data": 503, "traceId": "..." }
```

### 3.4 세션 상태 (`event: SESSION_STATE`)
```json
{
  "type": "SESSION_STATE",
  "payload": {
    "sessionId": 99,
    "state": "IN_PROGRESS",
    "totalQuestionCount": 5
  }
}
```

### 3.5 피드백 생성 완료 (`event: FEEDBACK_READY`)
```json
{
  "type": "FEEDBACK_READY",
  "payload": {
    "sessionId": 99,
    "feedbackId": 88,
    "redirectTo": "/sessions/99/feedback"
  }
}
```

### 3.6 에러 (`event: ERROR`)
> WS 전송 계층 에러는 `event: "error"`(소문자) 프레임으로도 올 수 있다(`ws.go writeError`). 도메인 ERROR 이벤트는 `ERROR`.
```json
{
  "type": "ERROR",
  "payload": {
    "code": "DOC_ANALYSIS_FAILED",
    "message": "PDF 파싱에 실패했습니다",
    "documentId": 42
  }
}
```

---

## 4. 재연결 정책

- EventSource 자동 재연결 (브라우저 기본)
- 서버는 `id:` 필드로 마지막 이벤트 ID 부여
- 재연결 시 `Last-Event-ID` 헤더로 마지막 ID 전송 → 서버는 그 이후 이벤트만 재전송
- 미수신 이벤트는 짧은 메모리 버퍼 (최근 100개 또는 5분) 보관

### 폴링 Fallback
SSE 미지원 환경 또는 영구 단절 시:
```
GET /api/documents/{id}     # 5초 간격 폴링
GET /api/sessions/{id}      # 메시지 변경 감지
```

프론트엔드 구현은 `frontend/src/shared/hooks/useEventStream.ts` 단일 책임 훅으로 추상화. SSE 우선 → 실패 시 폴링.

---

## 5. 보안

- 토큰 검증은 매 SSE 연결 시작 시 수행
- 같은 user의 다른 디바이스 연결은 별도 세션으로 처리 (제한 없음)
- 비정상 종료 감지: heartbeat 30초 미수신 시 서버에서 connection close

---

## 6. 백엔드 구현 메모

### 단일 Core 인스턴스 (Phase 1)
- AI Server → RabbitMQ `callback.*` consume
- Core가 메모리 내 `Map<userId, List<SseEmitter>>` 유지
- DB 상태 갱신 후 같은 트랜잭션 종료 시점(AFTER_COMMIT)에 emitter로 push
- emitter가 닫혀 있으면 정리

### 멀티 Core 인스턴스 (수평 확장 시점)
선택지:
1. **RabbitMQ fanout exchange**: 모든 Core 인스턴스가 동일 사용자 알림을 받음 → 자기에게 연결된 emitter만 push
2. **Sticky session (Nginx ip_hash)**: 한 사용자의 SSE 연결을 같은 Core 인스턴스로 라우팅
3. **외부 pub/sub** (Redis 등): 본 프로젝트는 Redis 미사용 결정 ([`architecture.md §4.5`](./architecture.md)) → 1번 또는 2번 우선

처리량 메모:
- 단일 인스턴스 동시 SSE 1만 이하 처리 가능
- 그 이상에서 위 옵션 도입 검토
