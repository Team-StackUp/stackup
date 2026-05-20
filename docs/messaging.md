# RabbitMQ 메시징 규약

> Core ↔ AI 비동기 통신의 메시지 스펙. 토폴로지는 `infra/rabbitmq/definitions.json`에 정의되어 있고, 본 문서가 그 운영 규약을 기술한다.

---

## 1. 토폴로지 (현재 정의 기준)

### Exchanges (모두 topic, durable)

| Exchange | 방향 |
|----------|------|
| `stackup.core-to-ai` | Core → AI 작업 요청 |
| `stackup.ai-to-core` | AI → Core 결과 회신 |
| `stackup.realtime` | Core → RealTime 세션 알림 |

### Queues (durable)

| Queue | Bound to | Routing Key | Consumer |
|-------|----------|-------------|----------|
| `ai.analyze.repository` | `stackup.core-to-ai` | `analyze.repository` | AI Server |
| `ai.analyze.resume` | `stackup.core-to-ai` | `analyze.resume` | AI Server |
| `ai.analyze.web` | `stackup.core-to-ai` | `analyze.web` | AI Server |
| `ai.generate.questions` | `stackup.core-to-ai` | `generate.questions` | AI Server |
| `ai.generate.followup` | `stackup.core-to-ai` | `generate.followup` | AI Server |
| `core.callback.analysis` | `stackup.ai-to-core` | `callback.analysis` | Core Server |
| `core.callback.questions` | `stackup.ai-to-core` | `callback.questions` | Core Server |
| `q.realtime.session.notify` | `stackup.realtime` | `realtime.session.*` | RealTime Server |

### 추가 예정 (정의 시점에 본 표 갱신)

| 후보 Queue | 용도 |
|------------|------|
| `ai.generate.feedback` | 세션 종료 후 종합 피드백 생성 |
| `core.callback.feedback` | 피드백 콜백 |
| `q.dlq.*` | Dead Letter Queue (각 큐별) |

---

## 2. Routing Key 명명

```
{action}.{aggregate}
```

`action` ∈ `analyze | generate | callback | realtime`
`aggregate` ∈ `resume | repository | web | questions | followup | analysis | feedback | session`

새 routing key 추가 시 본 패턴 유지.

---

## 3. Envelope (공통 메시지 포맷)

모든 메시지는 다음 envelope를 따른다.

```json
{
  "messageId": "uuid-v4",
  "messageType": "analyze.resume",
  "version": "v1",
  "traceId": "9f4e5b...",
  "publishedAt": "2026-04-27T15:00:00Z",
  "publisher": "core-server",
  "payload": { ... },
  "context": {
    "userId": 42,
    "sessionId": null
  }
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| `messageId` | ✓ | UUID v4. 멱등 처리 키. |
| `messageType` | ✓ | routing key와 동일 (`analyze.resume`, `callback.analysis`) |
| `version` | ✓ | 페이로드 스키마 버전. Breaking Change 시 `v2` |
| `traceId` | ✓ | 분산 추적 ID (요청 traceId 전파) |
| `publishedAt` | ✓ | RFC 3339 |
| `publisher` | ✓ | `core-server` / `ai-server` |
| `payload` | ✓ | messageType별 스키마 (§5) |
| `context.userId` | 권장 | 권한·로깅용 |
| `context.sessionId` | 세션 관련 시 필수 | |

### AMQP Properties

| Property | 값 |
|----------|----|
| `content_type` | `application/json` |
| `content_encoding` | `utf-8` |
| `delivery_mode` | `2` (persistent) |
| `message_id` | envelope의 `messageId`와 동일 |
| `correlation_id` | request의 messageId (callback에서 사용) |
| `headers.x-trace-id` | envelope의 `traceId`와 동일 |
| `headers.x-attempt` | 재시도 횟수 (0부터) |

---

## 4. 흐름 매핑 (Routing Key ↔ Use Case)

| Use Case | Request RK | Callback RK | Callback Queue |
|----------|------------|-------------|-----------------|
| 이력서(PDF) 분석 (US-09) | `analyze.resume` | `callback.analysis` | `core.callback.analysis` |
| 웹 이력서(URL) 분석 (US-09) | `analyze.web` | `callback.analysis` | `core.callback.analysis` |
| 레포 분석 (US-10) | `analyze.repository` | `callback.analysis` | `core.callback.analysis` |
| 질문 풀 생성 (US-18) | `generate.questions` | `callback.questions` | `core.callback.questions` |
| 꼬리질문 생성 (US-19) | `generate.followup` | `callback.questions` | `core.callback.questions` |
| 피드백 생성 (US-24) | `generate.feedback` *(예정)* | `callback.feedback` *(예정)* | `core.callback.feedback` *(예정)* |
| 세션 알림 (RT2 SSE) | `realtime.session.notify` | (없음 — 단방향 push) | `q.realtime.session.notify` |

> `callback.analysis` 큐는 resume/web/repo 세 use case가 공유. consumer는 `payload.targetType` 으로 분기한다.

---

## 5. 메시지 스키마 카탈로그

### 5.1 `analyze.resume`
```json
{
  "messageType": "analyze.resume",
  "payload": {
    "resumeId": 42,
    "filePath": "resumes/raw/123/abc.pdf",
    "analyzedDocumentId": 101
  },
  "context": { "userId": 123 }
}
```

| 필드 | 설명 |
|------|------|
| `resumeId` | `resumes.id` |
| `filePath` | 객체 스토리지 키 (Core가 업로드 시 저장) |
| `analyzedDocumentId` | Core가 publish 직전 `analyzed_documents`에 PROCESSING row를 미리 생성하고 그 id를 전달 — AI는 embedding upsert 시 이 id를 사용 |

### 5.2 `analyze.repository`
```json
{
  "messageType": "analyze.repository",
  "payload": {
    "repositoryId": 7,
    "repoFullName": "octocat/hello-world",
    "defaultBranch": "main",
    "analyzedDocumentId": 102
  },
  "context": { "userId": 123 }
}
```

> 구 스펙(`githubAccessTokenEncrypted` envelope 동봉)은 폐기. AI Server는 분석 시점에 Core 내부 API `GET /api/internal/users/{userId}/github-token` 으로 평문 토큰을 짧게 위임받아 사용. envelope에는 비밀이 절대 포함되지 않는다.

### 5.3 `analyze.web`
```json
{
  "messageType": "analyze.web",
  "payload": {
    "resumeId": 42,
    "url": "https://example.com/me",
    "analyzedDocumentId": 103
  },
  "context": { "userId": 123 }
}
```

> 웹 이력서(URL)는 AI Server가 trafilatura로 본문을 추출 → 동일 분석 체인으로 처리. resume 도메인을 재사용하므로 callback의 `targetType` 은 `WEB`.

### 5.4 `callback.analysis` (성공)
```json
{
  "messageType": "callback.analysis",
  "payload": {
    "targetType": "RESUME",
    "targetId": 42,
    "status": "ANALYZED",
    "summary": "Java/Spring 3년차, 결제 시스템 개발...",
    "techStack": ["Java", "Spring Boot", "PostgreSQL"],
    "documentPath": "analyzed/resume/42/summary.md",
    "embeddingChunkCount": 18
  }
}
```

### 5.5 `callback.analysis` (실패)
```json
{
  "messageType": "callback.analysis",
  "payload": {
    "targetType": "RESUME",
    "targetId": 42,
    "status": "FAILED",
    "errorCode": "PDF_PARSE_FAILED",
    "errorMessage": "PDF에 텍스트 레이어가 없습니다",
    "retriable": false
  }
}
```

→ `targetType` ∈ `RESUME | REPOSITORY | WEB`
→ `documentPath` 는 객체 스토리지 키 (bucket 제외). Core는 같은 storage 추상화로 fetch.

### 5.6 `generate.questions`
```json
{
  "messageType": "generate.questions",
  "payload": {
    "sessionId": 99,
    "interviewType": "TECHNICAL",
    "jobCategory": "BACKEND",
    "documentIds": [42, 17],
    "maxQuestions": 10
  },
  "context": { "userId": 123, "sessionId": 99 }
}
```

### 5.7 `callback.questions` (질문 풀)
```json
{
  "messageType": "callback.questions",
  "payload": {
    "sessionId": 99,
    "kind": "POOL",
    "questions": [
      { "category": "PROJECT_DEEP_DIVE", "question": "..." },
      { "category": "CS_FUNDAMENTAL", "question": "..." }
    ]
  }
}
```

### 5.8 `generate.followup`
```json
{
  "messageType": "generate.followup",
  "payload": {
    "sessionId": 99,
    "questionMessageId": 501,
    "answerMessageId": 502,
    "answerText": "...",
    "audioS3Key": "session/99/audio/502.webm"
  }
}
```

### 5.9 `callback.questions` (꼬리질문)
```json
{
  "messageType": "callback.questions",
  "payload": {
    "sessionId": 99,
    "kind": "FOLLOWUP",
    "parentMessageId": 502,
    "followupQuestion": "...",
    "answerEvaluation": {
      "specificity": 3.5,
      "logic": 4.0,
      "structure": "PARTIAL_STAR"
    },
    "voiceAnalysis": {
      "speakingRateWpm": 142.0,
      "fillerWordCounts": { "음": 5, "어": 3 },
      "silenceDurationSec": 8.2
    }
  }
}
```

> `callback.questions` 큐는 두 종류(`POOL`, `FOLLOWUP`)를 받으므로 consumer는 `payload.kind`로 분기.

### 5.10 `generate.feedback` *(예정)*
```json
{
  "messageType": "generate.feedback",
  "payload": { "sessionId": 99 }
}
```

### 5.11 `callback.feedback` *(예정)*
```json
{
  "messageType": "callback.feedback",
  "payload": {
    "sessionId": 99,
    "overallScore": 76.5,
    "technicalAccuracy": 80.0,
    "logicScore": 72.0,
    "communicationScore": 78.0,
    "strengthsSummary": "...",
    "weaknessesSummary": "...",
    "improvementKeywords": ["JPA 영속성 컨텍스트", "TCP 3-way handshake"],
    "reportS3Key": "feedback/99/report.md"
  }
}
```

### 5.12 `realtime.session.notify`
```json
{
  "messageType": "realtime.session.notify",
  "payload": {
    "eventType": "question.created",
    "data": { /* 임의 JSON, RealTime이 그대로 SSE data 필드에 전달 */ }
  },
  "context": { "sessionId": 99, "userId": 123 }
}
```

- 발행자: Core 서버 (AI callback 처리 후 또는 자체 상태 변화 시)
- 소비자: RealTime 서버 (SSE 구독자에게 fan-out)
- `context.sessionId` 필수 — 라우팅 키
- `payload.eventType`은 SSE `event:` 필드로 매핑

---

## 6. 재시도·DLQ 정책

| 시나리오 | 정책 |
|----------|------|
| Consumer 일시 오류 (네트워크, LLM 일시 장애) | NACK + requeue, 최대 3회 |
| 재시도 횟수 초과 | DLQ 이동 + 실패 callback 발행 (`status: FAILED`, `retriable: false`) |
| 메시지 파싱 실패 (스키마 위반) | 즉시 DLQ (재시도 무의미) |
| 멱등 충돌 (이미 처리된 messageId) | ACK + 처리 skip |

### Quorum Queue 권장 설정 (도입 시)
```
x-queue-type: quorum
x-delivery-limit: 3
x-dead-letter-exchange: stackup.dlx
```

> 현재 `definitions.json`은 기본 큐 (classic) — Phase 2에 quorum + DLX 도입.

### 멱등 처리
- Consumer는 `messageId`를 PostgreSQL `processed_messages` 테이블 (`UNIQUE(message_id)`)에 INSERT 시도
  - 충돌(duplicate key) 시 skip + ACK
  - 24h 이상 된 row는 cron으로 정리
- AI Server는 인메모리 LRU + RabbitMQ delivery_tag 조합도 허용 (재시작 시 RabbitMQ가 미ACK 메시지 재전달)
- (Redis 미사용 — [`architecture.md §4.5`](./architecture.md))

---

## 7. 메시지 버전 변경 절차

Breaking Change (필드 제거·타입 변경):
1. 새 `version` 값 부여 (`v2`)
2. Consumer는 `v1`, `v2` 모두 핸들링하도록 분기 추가
3. Publisher가 `v2`로 전환
4. 1주일 후 `v1` 핸들러 제거 + 본 문서에서 v1 스펙 삭제

Non-breaking (필드 추가):
- 같은 version에서 추가 가능
- Consumer는 unknown 필드 무시 (Jackson `FAIL_ON_UNKNOWN_PROPERTIES = false`, Pydantic `extra="ignore"`)

---

## 8. 새 큐 추가 절차

1. `infra/rabbitmq/definitions.json` 에 exchange/queue/binding 추가
2. `docker compose restart rabbitmq` (또는 management UI에서 import)
3. 본 문서 §1 토폴로지 표 갱신
4. §5 스키마 카탈로그에 메시지 스키마 추가
5. Publisher (Core) + Consumer (AI 또는 Core) 코드 작성
6. 통합 테스트 (Testcontainer)

---

## 9. 로컬 개발

관리 콘솔: http://localhost:15672 (default `stackup`/`stackup`)

스모크 테스트:
```bash
# AI 큐에 직접 발행
docker exec stackup-rabbitmq rabbitmqadmin \
  -u stackup -p stackup \
  publish exchange=stackup.core-to-ai \
  routing_key=analyze.resume \
  payload='{"messageId":"smoke-1","messageType":"analyze.resume","version":"v1","traceId":"local-test","publishedAt":"2026-04-27T15:00:00Z","publisher":"manual","payload":{"resumeId":1,"filePath":"resumes/raw/1/test.pdf","analyzedDocumentId":1},"context":{"userId":1}}'
```

---

## 10. AI ↔ Core 내부 API (RabbitMQ 외)

분석 파이프라인 일부는 동기적 데이터 위임이 필요해 Core가 내부 전용 REST endpoint를 노출한다. 모두 `X-Internal-API-Key` 헤더 검증.

| Method | Path | 호출자 | 용도 |
|--------|------|--------|------|
| `GET`  | `/api/internal/users/{userId}/github-token` | AI | 사용자별 GitHub access token을 분석 시점에 짧게 위임 (envelope에 비밀 미동봉) |
| `PUT`  | `/api/internal/documents/{documentId}/embeddings` | AI | 청크 + 임베딩을 `document_embeddings`에 idempotent upsert |

요청·응답 스키마 및 인증 규약은 [`/docs/api-conventions.md §10`](./api-conventions.md) 참조.

큐 상태 확인:
```bash
docker exec stackup-rabbitmq rabbitmqctl list_queues -q name messages consumers
```
