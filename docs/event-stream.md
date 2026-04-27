# SSE 이벤트 스펙

> AI 작업 진행 상태를 프론트엔드에 실시간 푸시하기 위한 Server-Sent Events 스펙. RealTime Server가 엔드포인트 제공.

---

## 1. 엔드포인트

```
GET /api/stream/user/{userId}
GET /api/stream/sessions/{sessionId}
GET /api/stream/documents/{documentId}
```

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
  "type": "DOC_ANALYZE_STATE",
  "payload": { ... },
  "timestamp": "2026-04-27T15:00:00Z",
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

### Core ↔ RealTime ↔ Frontend
- Core Server는 RabbitMQ `ai.status.*` 메시지를 consume
- consume 후 Redis Pub/Sub (`channel: stream:user:{userId}`)에 publish
- RealTime Server는 Redis subscribe → 연결된 SSE 클라이언트로 broadcast

### 처리량 메모
- 동시 SSE 연결 1만 이하: 단일 RealTime 인스턴스로 처리 가능
- 그 이상: Redis Cluster + RealTime 수평 확장
