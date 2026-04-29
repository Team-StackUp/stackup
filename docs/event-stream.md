# SSE 이벤트 스펙

> AI 작업 진행 상태와 면접 메시지를 프론트엔드에 실시간 푸시하기 위한 Server-Sent Events 스펙.
> **양방향 WebSocket 미사용** — 본 프로젝트의 푸시는 모두 SSE 단일 경로. 트래픽·인프라 효율 + EventSource 자동 재연결의 이점.
> 미디어 스트림(음성/영상)만 WebRTC 사용 (별도).

---

## 1. 엔드포인트

```
GET /api/stream/user/{userId}
GET /api/stream/sessions/{sessionId}
GET /api/stream/documents/{documentId}
```

- 제공 주체: Phase 1은 **Core Server**가 직접 제공. RealTime Server 분리 시점에 그쪽으로 이전.
- 인증: `Authorization: Bearer ...` 또는 쿼리 토큰 (`?access_token=...` — EventSource 한계 우회용)
- 권한: 본인 리소스만 구독 가능
- 연결 유지: 30초마다 `:keep-alive` 코멘트 송신

---

## 2. 이벤트 포맷

표준 SSE 프레임:
```
event: <eventName>
id: <eventId>
data: <JSON>

```

`<JSON>`:
```json
{
  "type": "DOC_STATE",
  "payload": { ... },
  "timestamp": "2026-04-28T15:00:00Z",
  "traceId": "..."
}
```

---

## 3. 이벤트 카탈로그

### 3.1 분석 상태 (`event: doc.state`)
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

### 3.2 레포 분석 (`event: repo.state`)
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

### 3.3 세션 메시지 푸시 (`event: session.message`)
```json
{
  "type": "SESSION_MESSAGE",
  "payload": {
    "sessionId": 99,
    "messageId": 503,
    "role": "INTERVIEWER",
    "content": "왜 그 시점에 ...",
    "parentMessageId": 502,
    "sequenceNumber": 7
  }
}
```

### 3.4 세션 상태 (`event: session.state`)
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

### 3.5 피드백 생성 완료 (`event: feedback.ready`)
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

### 3.6 에러 (`event: error`)
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
