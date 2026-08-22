# RabbitMQ 메시징 규약

> Core ↔ AI 비동기 통신의 메시지 스펙. 토폴로지는 `infra/rabbitmq/definitions.json`에 정의되어 있고, 본 문서가 그 운영 규약을 기술한다.

---

## 1. 토폴로지 (현재 정의 기준)

### Exchanges (durable)

| Exchange | Type | 방향 |
|----------|------|------|
| `stackup.core-to-ai` | topic | Core → AI 작업 요청 |
| `stackup.ai-to-core` | topic | AI → Core 결과 회신 |
| `stackup.realtime` | topic | Core·AI → RealTime 알림 (세션/상태, AI 분석 진행) |
| `stackup.dlx` | direct | 처리 실패 메시지 격리 (Dead Letter Exchange) |

### Queues (durable)

| Queue | Bound to | Routing Key | Consumer |
|-------|----------|-------------|----------|
| `ai.analyze.repository` | `stackup.core-to-ai` | `analyze.repository` | AI Server |
| `ai.analyze.resume` | `stackup.core-to-ai` | `analyze.resume` | AI Server |
| `ai.analyze.cover_letter` | `stackup.core-to-ai` | `analyze.cover_letter` | AI Server |
| `ai.analyze.web` | `stackup.core-to-ai` | `analyze.web` | AI Server |
| `ai.generate.questions` | `stackup.core-to-ai` | `generate.questions` | AI Server |
| `ai.generate.followup` | `stackup.core-to-ai` | `generate.followup` | AI Server |
| `ai.generate.feedback` | `stackup.core-to-ai` | `generate.feedback` | AI Server |
| `ai.analyze.voice` | `stackup.core-to-ai` | `analyze.voice` | AI Server |
| `ai.generate.tts` | `stackup.core-to-ai` | `generate.tts` | AI Server |
| `core.callback.analysis` | `stackup.ai-to-core` | `callback.analysis` | Core Server |
| `core.callback.questions` | `stackup.ai-to-core` | `callback.questions` | Core Server |
| `core.callback.feedback` | `stackup.ai-to-core` | `callback.feedback` | Core Server |
| `core.callback.voice` | `stackup.ai-to-core` | `callback.voice` | Core Server |
| `core.callback.tts` | `stackup.ai-to-core` | `callback.tts` | Core Server |
| `q.realtime.session.notify` | `stackup.realtime` | `realtime.session.*` · `realtime.user.*` · `realtime.document.*` | RealTime Server |

### Dead Letter Queues (durable)

각 work queue 는 `x-dead-letter-exchange=stackup.dlx` + `x-dead-letter-routing-key=dlq.<queue>` 인자를 가진다.
재시도 한도 초과 또는 `requeue=false` reject 시 DLX 로 라우팅되어 짝이 되는 DLQ 로 격리된다.

| DLQ | Bound to | Routing Key | 격리 대상 |
|-----|----------|-------------|-----------|
| `dlq.ai.analyze.resume` | `stackup.dlx` | `dlq.ai.analyze.resume` | `ai.analyze.resume` 처리 실패 |
| `dlq.ai.analyze.cover_letter` | `stackup.dlx` | `dlq.ai.analyze.cover_letter` | `ai.analyze.cover_letter` 처리 실패 |
| `dlq.ai.analyze.repository` | `stackup.dlx` | `dlq.ai.analyze.repository` | `ai.analyze.repository` 처리 실패 |
| `dlq.ai.analyze.web` | `stackup.dlx` | `dlq.ai.analyze.web` | `ai.analyze.web` 처리 실패 |
| `dlq.ai.generate.questions` | `stackup.dlx` | `dlq.ai.generate.questions` | `ai.generate.questions` 처리 실패 |
| `dlq.ai.generate.followup` | `stackup.dlx` | `dlq.ai.generate.followup` | `ai.generate.followup` 처리 실패 |
| `dlq.ai.generate.feedback` | `stackup.dlx` | `dlq.ai.generate.feedback` | `ai.generate.feedback` 처리 실패 |
| `dlq.ai.analyze.voice` | `stackup.dlx` | `dlq.ai.analyze.voice` | `ai.analyze.voice` 처리 실패 |
| `dlq.ai.generate.tts` | `stackup.dlx` | `dlq.ai.generate.tts` | `ai.generate.tts` 처리 실패 |
| `dlq.core.callback.analysis` | `stackup.dlx` | `dlq.core.callback.analysis` | `core.callback.analysis` 처리 실패 |
| `dlq.core.callback.questions` | `stackup.dlx` | `dlq.core.callback.questions` | `core.callback.questions` 처리 실패 |
| `dlq.core.callback.feedback` | `stackup.dlx` | `dlq.core.callback.feedback` | `core.callback.feedback` 처리 실패 |
| `dlq.core.callback.voice` | `stackup.dlx` | `dlq.core.callback.voice` | `core.callback.voice` 처리 실패 |
| `dlq.core.callback.tts` | `stackup.dlx` | `dlq.core.callback.tts` | `core.callback.tts` 처리 실패 |
| `dlq.q.realtime.session.notify` | `stackup.dlx` | `dlq.q.realtime.session.notify` | `q.realtime.session.notify` 처리 실패 |

---

## 2. Routing Key 명명

```
{action}.{aggregate}
```

`action` ∈ `analyze | generate | callback | realtime`
`aggregate` ∈ `resume | repository | cover_letter | web | questions | followup | tts | voice | analysis | feedback | session`

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
| 질문 TTS 합성 | `generate.tts` | `callback.tts` | `core.callback.tts` |
| 음성 답변 분석 (STT + 지표) | `analyze.voice` | `callback.voice` | `core.callback.voice` |
| 피드백 생성 (US-24) | `generate.feedback` | `callback.feedback` | `core.callback.feedback` |
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

> **발행 시점**: 세션 생성 시가 아니라 **자기소개(첫 질문) 답변을 받은 직후**(`SelfIntroAnsweredEvent`).
> 모든 면접의 첫 질문은 자기소개로 고정이며, 질문 풀은 그 답변(`selfIntroAnswer`)을 1차 근거로 생성한다.
> `initialQuestionCount` 는 자기소개 1자리를 예약해 `generalQuestionCount - 1` 로 보낸다.
> `mode=JOB_TAILORED` 면 `targetCompanyName`·`targetJobDescription`(JD)이 채워져 적합도·지원동기 질문의 근거가 된다(다른 모드는 null).

```json
{
  "messageType": "generate.questions",
  "payload": {
    "sessionId": 99,
    "mode": "JOB_TAILORED",
    "jobCategories": ["BACKEND", "FRONTEND"],
    "documents": [ { "documentId": 42, "sourceType": "RESUME", "summary": "...", "techStack": ["..."], "markdown": "..." } ],
    "initialQuestionCount": 2,
    "maxQuestions": 10,
    "recentQuestions": ["이전 면접 질문 텍스트", "..."],
    "selfIntroAnswer": "안녕하세요, 결제 시스템을 만든 백엔드 3년차입니다…",
    "targetCompanyName": "토스",
    "targetJobDescription": "Kotlin/Spring 백엔드, 대용량 결제 시스템 경험 우대 …",
    "focusAreas": ["LOGIC", "COMMUNICATION"]
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
      {
        "category": "PROJECT_DEEP_DIVE",
        "question": "...",
        "jobCategory": "BACKEND",
        "targetEvidence": "이력서: 결제 시스템에서 재고 차감 동시성 문제를 낙관적 락으로 해결",
        "expectedSignal": "락 전략 선택의 트레이드오프를 DB 레벨까지 설명하는지"
      },
      { "category": "CS_FUNDAMENTAL", "question": "...", "jobCategory": null, "targetEvidence": "", "expectedSignal": "" }
    ],
    "status": "OK"
  }
}
```

| `questions[]` 필드 | 설명 |
|------|------|
| `jobCategory` | 이 질문이 겨냥한 직군(세션 `jobCategories` 중 하나). 다직군 패널 가중에 사용. LLM 이 비우면(null) Core 가 대표 직군으로 폴백 |
| `targetEvidence` | 질문이 근거한 자료 인용 (PROJECT/TECH 는 필수, 그 외 빈 문자열 허용). 라이브 화면에 힌트로 노출 |
| `expectedSignal` | 좋은 답이 드러내야 할 것 — 내부 평가용. 꼬리질문 채점의 `parentExpectedSignal` 로 전달(§5.8). **라이브 비노출** (정답 유출 방지) |

**실패 시** (`generate()` 예외 — LLM 게이트웨이 장애, 파싱 실패 등):
```json
{
  "messageType": "callback.questions",
  "payload": {
    "sessionId": 99,
    "kind": "POOL",
    "questions": [],
    "status": "FAILED",
    "errorCode": "GENERATION_FAILED",
    "errorMessage": "...",
    "retriable": true
  }
}
```
→ `status` 미명시(구버전)는 `OK` 로 취급. Core 는 `FAILED` 수신 시 풀을 세팅하지 않고 `SseEventType.ERROR` 로 세션/유저 채널에 알린다(세션 상태는 그대로 — 재시도 트리거는 후속 과제). `errorCode`: `GENERATION_FAILED`(재시도 가능) | `GENERATION_SCHEMA_INVALID`(LLM 출력이 스키마 불일치, 재시도해도 같은 이유로 실패할 가능성 높음 → `retriable=false`) | `UNEXPECTED`.

### 5.8 `generate.followup`
```json
{
  "messageType": "generate.followup",
  "payload": {
    "sessionId": 99,
    "parentMessageId": 501,
    "answerMessageId": 502,
    "previousQuestion": "...",
    "answerText": "...",
    "mode": "TECHNICAL",
    "jobCategory": "BACKEND",
    "contextDocumentIds": [12, 13],
    "parentCategory": "PROJECT_DEEP_DIVE",
    "parentExpectedSignal": "동시성 제어를 DB 레벨까지 설명하는지",
    "followupMessageId": 503,
    "history": [{ "role": "INTERVIEWER", "content": "..." }]
  }
}
```

> `parentExpectedSignal` = 직전 질문 생성 시 만든 기대 신호(평가 관점). AI 가 specificity/correctness 채점의 핵심 기준으로 사용(없으면 무시). `contextDocumentIds` 로 RAG 검색 → correctness 판정.
> `followupMessageId` = Core 가 답변 직후 선INSERT 한 INTERVIEWER placeholder(content=`"(생성 중)"`) 메시지 id. AI 는 이 id 로 `SESSION_MESSAGE_DELTA` 토큰을 태깅해 발행하고(§5.12-1), 콜백에도 되돌려준다. Core 는 콜백 시 이 id 의 placeholder 를 UPDATE(NORMAL/CLARIFICATION) 또는 DELETE(DONT_KNOW) 한다.

### 5.9 `callback.questions` (꼬리질문)
```json
{
  "messageType": "callback.questions",
  "payload": {
    "sessionId": 99,
    "kind": "FOLLOWUP",
    "parentMessageId": 502,
    "followupMessageId": 503,
    "answerIntent": "NORMAL",
    "followupQuestion": "...",
    "answerEvaluation": {
      "specificity": 3.5,
      "logic": 4.0,
      "structure": "PARTIAL_STAR",
      "correctness": null
    },
    "status": "OK"
  }
}
```

> 음성 지표는 이 콜백에 포함되지 않는다 — 별도 파이프라인 `analyze.voice` → `callback.voice` (§5.9d) 로 전달된다.

**실패 시** (생성 실패 — 스트리밍/비스트리밍 공통):
```json
{
  "messageType": "callback.questions",
  "payload": {
    "sessionId": 99,
    "kind": "FOLLOWUP",
    "parentMessageId": 502,
    "answerMessageId": 502,
    "followupMessageId": 503,
    "followupQuestion": "",
    "status": "FAILED",
    "errorCode": "GENERATION_FAILED",
    "errorMessage": "...",
    "retriable": true
  }
}
```
→ `followupMessageId` 의 placeholder는 Core 가 `InterviewMessage.failFollowup()`으로 확정 짓는다(내용을
"질문 생성에 실패했습니다. 다음 질문으로 넘어갑니다."로 교체, `MessageStatus.FAILED`) — 삭제하지 않아
턴이 사라진 것처럼 보이지 않는다. 처리 후 `DONT_KNOW` 와 동일하게 `advanceToNextGeneral` 로 다음
일반질문으로 진행해 면접이 멈추지 않는다. placeholder 를 못 찾으면(레거시 폴백 경로) `SseEventType.ERROR`
로만 알린다.

> `callback.questions` 큐는 두 종류(`POOL`, `FOLLOWUP`)를 받으므로 consumer는 `payload.kind`로 분기.
> 어느 kind 든 `status: "FAILED"` 면 `payload.kind` 분기 전에 실패 처리 분기를 먼저 태운다
> (`QuestionsCallbackService.apply`).

### 5.9a `generate.tts`
```json
{
  "messageType": "generate.tts",
  "payload": {
    "sessionId": 99,
    "messageId": 502,
    "text": "당신의 프로젝트에서 가장 어려웠던 점은 무엇인가요?",
    "mode": "TECHNICAL",
    "jobCategory": "BACKEND"
  },
  "context": { "userId": 123, "sessionId": 99 }
}
```

> 질문(INTERVIEWER) 메시지 영속 후 Core 가 발행. `messageId` 는 `interview_messages.id`. AI 가 `text` 를 TTS 합성 → S3 PUT → `callback.tts` 회신.

### 5.9b `callback.tts`
```json
{
  "messageType": "callback.tts",
  "payload": {
    "sessionId": 99,
    "messageId": 502,
    "status": "SUCCEEDED",
    "audioKey": "interview/tts/99/502.mp3",
    "durationSec": 4.2,
    "errorCode": null
  }
}
```

> 실패 시 `status: "FAILED"` + `errorCode`(`TTS_API_ERROR`/`TTS_STORAGE_FAILED` 등), `audioKey`/`durationSec` 는 null. OpenAI TTS 는 duration 을 주지 않으므로 `durationSec` 는 null 일 수 있다.

### 5.9c `analyze.voice`
```json
{
  "messageType": "analyze.voice",
  "payload": {
    "sessionId": 99,
    "messageId": 502,
    "parentQuestionMessageId": 501,
    "audioS3Key": "interview/answers/99/502.webm",
    "contentType": "audio/webm",
    "previousQuestionText": "...",
    "mode": "TECHNICAL",
    "jobCategory": "BACKEND"
  },
  "context": { "userId": 123, "sessionId": 99 }
}
```

> Core 가 음성 답변 업로드 commit 후 발행(§5.14). `messageId` 는 `interview_messages.id` — STT 후 content 를 채울 placeholder. AI 가 S3 오디오를 받아 STT + 음성 지표(WPM/간투어/침묵) 계산 후 `callback.voice` 회신.

### 5.9d `callback.voice`
```json
{
  "messageType": "callback.voice",
  "payload": {
    "sessionId": 99,
    "interviewMessageId": 502,
    "transcript": "네, 저는 결제 시스템에서...",
    "speakingRateWpm": 142.0,
    "silenceDurationSec": 8.2,
    "fillerWordCounts": { "음": 5, "어": 3 },
    "pronunciationAccuracy": 0.93,
    "errorCode": null
  }
}
```

> STT 결과 + 음성 지표. 지표 필드는 전부 nullable — 계산 불가 시 null. 실패 시 `errorCode` 가 채워지고 Core 는 해당 메시지를 FAILED 로 마킹한다. 세션 종합 피드백에는 이 지표를 Core 가 집계해 `generate.feedback` 의 `voiceAnalysisSummary` 로 동봉한다(개별 콜백을 AI 가 재수집하지 않음).

### 5.10 `generate.feedback`

> `messages[]` 의 각 항목은 `category` 를 포함한다(질문 유형). AI 는 `category=SELF_INTRODUCTION`
> 질문과 그 답변을 찾아 **첫인상(전달력·구성·직무적합성)** 을 별도 평가한다.
> `mode=JOB_TAILORED` 면 `targetCompanyName`·`targetJobDescription`(JD)이 채워져 **직무 적합도** 평가의 근거가 된다.

```json
{
  "messageType": "generate.feedback",
  "payload": {
    "sessionId": 99,
    "mode": "JOB_TAILORED",
    "jobCategory": "BACKEND",
    "messages": [
      { "id": 1, "sequenceNumber": 1, "role": "INTERVIEWER", "content": "자기소개…", "category": "SELF_INTRODUCTION" },
      { "id": 2, "sequenceNumber": 2, "role": "INTERVIEWEE", "content": "…", "parentMessageId": 1 }
    ],
    "targetCompanyName": "토스",
    "targetJobDescription": "Kotlin/Spring 백엔드, 대용량 결제 …"
  }
}
```

### 5.11 `callback.feedback`

> `answerCoaching[]` 은 질문별 복기 — 답변(INTERVIEWEE) 메시지별 모범 답안·리라이트·한 줄 코칭(자기소개 제외).
> Core 가 각 `messageId` 의 메시지에 기록하고 종료 세션 조회에서만 노출한다.
> `panelBreakdown[]` 에 평가위원별 항목이 담긴다. 자기소개가 있던 세션은 **`evaluator="첫인상"`**,
> 직무 맞춤 모드는 **`evaluator="직무 적합도"`(역량 매칭)** + **`evaluator="직무 이해도"`(직무 이해·동기)**
> 항목이 추가로 포함된다 — 모두 **종합 점수(overallScore) 집계에서 제외**된 별도 정성 평가다(메인
> generator 가 모른 채 overall 계산 후 표시용으로 append).
> `highlights[]` 는 강점·개선 본문에서 발췌한 핵심 구절 — 프론트가 부분 문자열 매칭으로 리포트에
> 하이라이트 표시한다(`HighlightedText`). 빈 리스트 허용.

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
    "studyPlan": ["..."],
    "highlights": ["결론부터 말하는 답변 구조", "동시성 제어 경험"],
    "answerCoaching": [
      { "messageId": 203, "modelAnswer": "이 질문에 강한 답변 예시…", "answerRewrite": "내 답변을 이렇게 고치면…", "coachingComment": "결론을 먼저 말하세요." }
    ],
    "panelBreakdown": [
      { "evaluator": "백엔드", "dimension": "기술 정확도·깊이", "score": 80.0, "detail": "...", "scoreRationale": "..." },
      { "evaluator": "첫인상", "dimension": "자기소개 전달력·구성·직무적합성", "score": 78.0, "detail": "...", "scoreRationale": "..." }
    ],
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

### 5.13 `realtime.user.notify` · `realtime.document.notify`

`realtime.session.notify` 와 동일 구조. 채널(messageType)만 다르다.

- `realtime.user.notify` — `context.userId` 필수. 분석 상태(`DOC_STATE`/`REPO_STATE`) 등 사용자 단위 알림.
- `realtime.document.notify` — `context.documentId` 필수. 문서 단위 분석 상태.
- 발행: Core `RealtimeNotifyPublisher.publishToUser(userId, ...)` / `publishToDocument(documentId, ...)`.
- `payload.eventType` 은 `SseEventType` enum 이름(`DOC_STATE` 등). RealTime이 SSE `event:` 필드로 전달.
- RealTime 측은 envelope `messageType`(`realtime.{kind}.notify`)으로 채널을 판별해 해당 채널 구독자에게 fan-out.

> 단일 큐 `q.realtime.session.notify` 가 세 라우팅 키(`realtime.session.*`/`realtime.user.*`/`realtime.document.*`)를 모두 바인딩한다.

#### AI → RealTime 직접 발행 (분석 단계 진행)

분석 **종료** 상태(`ANALYZED`/`FAILED`)는 AI→Core 콜백(`callback.analysis`) → Core 가 `REPO_STATE`/`DOC_STATE` 로 RealTime 에 전달한다. 반면 분석 **진행 중** 단계(휘발성, DB 영속 불필요)는 AI 서버가 `stackup.realtime` exchange 의 `realtime.user.notify` 로 **직접** 발행해 user 채널 SSE 로 흘린다(Core 미경유).

- 발행: AI `AnalysisProgressNotifier` (envelope 구조는 Core `RealtimeNotifyPublisher` 와 동일 — `payload.eventType` + `context.userId`).
- `payload.eventType` = `ANALYSIS_PROGRESS`, `payload.data` = `{ targetType, targetId, phase, message }`. phase ∈ `EXTRACTING | SUMMARIZING | EMBEDDING`.

### 5.12-1 `realtime.session.notify` — 꼬리질문 토큰 델타 (AI 직접 발행)
분석 진행(ANALYSIS_PROGRESS)과 동일 패턴으로, AI 서버가 꼬리질문 생성 중 토큰을 `stackup.realtime` exchange 의 `realtime.session.notify` 로 **직접** 발행(Core 미경유)해 세션 채널로 흘린다. 휘발성(영속 불필요)이며 발행 실패는 무시(경고만).
- `context.sessionId` 로 세션 채널 라우팅. `payload.eventType` = `SESSION_MESSAGE_DELTA`, `payload.data` = `{ messageId, seq, text }` (§event-stream.md 3.3-1).
- `messageId` = `generate.followup` 의 `followupMessageId`(placeholder). 종료/정본은 기존대로 Core 가 `callback.questions` 수신 후 `SESSION_MESSAGE`(messageId) 로 통지.

### 5.12-2 `realtime.session.notify` — 문장 단위 TTS 세그먼트 (AI 직접 발행)
AI followup consumer 가 토큰 스트림 중 문장 경계마다 그 문장만 인라인 TTS 합성 → S3 세그먼트 PUT → `SESSION_MESSAGE_AUDIO` 를 `realtime.session.notify` 로 직접 발행(휘발성). 문장 합성은 `asyncio.create_task` 백그라운드(텍스트 델타 비차단), 콜백 발행 전 `gather` 로 수거.
- `payload.eventType` = `SESSION_MESSAGE_AUDIO`, `payload.data` = `{ messageId, seq, ext, durationSec }`. `seq` 는 오디오 전용(델타 seq 와 독립).
- 세그먼트 S3 키 규칙(AI·Core 공유): `interview/tts/{sessionId}/{messageId}/seg-{seq}.{ext}`. Core 는 DB 미기록, `GET …/messages/{mid}/audio/segments/{seq}?ext=` 프록시에서 소유권 검증 후 규칙으로 키 재구성(ext 화이트리스트 wav|mp3|ogg|m4a).
- 상세 SSE 스펙: [`event-stream.md §3.2-1`](./event-stream.md).

### 5.14 발행 시점 규약 — 반드시 커밋 후에 발행한다

**Core 의 모든 작업 요청 발행(`stackup.core-to-ai`)은 DB 커밋 이후에 일어나야 한다.**
트랜잭션 안에서 발행하면 이후 커밋이 실패했을 때 AI 는 존재하지 않는 행을 대상으로 작업하고,
결과 콜백은 "not found" 로 드롭되어 **사용자 입력이 조용히 사라진다**.

구현 패턴 — 도메인 이벤트 + `AFTER_COMMIT` 리스너:

```java
// 1) 트랜잭션 안: DB 쓰기 + 도메인 이벤트만
events.publishEvent(new VoiceAnswerUploadedEvent(userId, sessionId, messageId, key, contentType));

// 2) 커밋 후: envelope 발행
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onVoiceAnswerUploaded(VoiceAnswerUploadedEvent event) { … publisher.publishToAi(…); }
```

| Routing Key | 발행 주체 (`AFTER_COMMIT`) | 트리거 이벤트 |
|---|---|---|
| `generate.questions` | `SessionQuestionsRequester` | `SessionCreatedEvent` · `SelfIntroAnsweredEvent` |
| `generate.followup` | `SessionFollowupRequester` | `AnswerSubmittedEvent` |
| `generate.tts` | `SessionTtsRequester` | `QuestionPersistedEvent` |
| `generate.feedback` | `SessionFeedbackRequester` | `SessionEndedEvent` |
| `analyze.voice` | `VoiceAnalysisRequester` | `VoiceAnswerUploadedEvent` |
| `analyze.resume` · `analyze.repository` · `analyze.cover_letter` | `AnalysisRequestService` | `*AnalysisRequestedEvent` |

**S3 PUT 도 트랜잭션 밖에서 한다.** 업로드(최대 25MB)가 끝날 때까지 DB 커넥션을 붙잡으면
커넥션 풀을 잠식한다. 업로드 후 별도 트랜잭션에서 키를 붙이고, 업로드가 실패하면 이미 커밋된
placeholder 를 `FAILED` 로 확정해 클라이언트의 턴이 잠기지 않게 보상한다.

---

## 6. 재시도·DLQ 정책

| 시나리오 | 정책 |
|----------|------|
| Consumer 일시 오류 (네트워크, LLM 일시 장애) | in-process 재시도, 최대 3회 + exponential backoff |
| 재시도 횟수 초과 | reject(requeue=false) → DLX → DLQ (`dlq.<work-queue>`) |
| 메시지 파싱 실패 (스키마 위반) | 즉시 reject(requeue=false) → DLQ (재시도 무의미) |
| 멱등 충돌 (이미 처리된 messageId) | ACK + 처리 skip (`processed_messages`) |
| 영구 분석 실패 (PDF 손상 등) | ACK + 실패 callback 발행 (`status: FAILED`, `retriable: false`) — DLQ 미사용 |
| 질문 풀/꼬리질문 생성 실패 (LLM 게이트웨이 장애, 스키마 위반 등) | ACK + 실패 callback 발행 (`status: FAILED`) — 세션이 "생성 중"에 무기한 멈추지 않게 항상 콜백을 보낸다. DLQ 미사용 |

### Core (Spring AMQP)
- `RabbitMqConfig#rabbitListenerContainerFactory` 가 stateless retry interceptor (`RetryInterceptorBuilder.stateless()`) 를 attach.

### AI Server (aio-pika)
- 컨슈머는 `async with message.process(requeue=False)` 패턴.
- 도메인 예외 (`ResumeAnalyzeError` 등) 는 catch 하여 실패 callback 발행 (재시도 무의미).
- `questions_consumer`/`followup_consumer` 도 동일 패턴 — 생성 호출을 catch 해 항상 콜백을 발행한다
  (`QuestionPoolCallbackPayload`/`FollowupCallbackPayload` 의 `status: FAILED`). Core 가 이미 선INSERT 한
  꼬리질문 placeholder 가 영원히 "생성 중"으로 남는 것을 방지.
- 그 외 예외는 re-raise → nack(requeue=false) → DLX 로 routing.
- 일시 장애의 in-process 재시도는 미구현 (Phase 2 — 아래 Quorum Queue 도입과 함께).

### RealTime Server (amqp091-go)
- `_ = d.Nack(false, false)` (drop, requeue 없음) → DLX 로 routing.

### Quorum Queue 권장 설정 (Phase 2 검토)
```
x-queue-type: quorum
x-delivery-limit: 3
```
`x-delivery-limit` 은 quorum queue 에서만 동작. 도입 시 컨슈머 단의 in-process 재시도 인터셉터를 제거하고 브로커-레벨 재시도로 일원화.

> 현재 `definitions.json`은 classic queue + DLX. Phase 2 에 quorum 전환 검토.

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
| `POST` | `/api/internal/embeddings/search` | AI | RAG 검색 — pgvector cosine topK (queryText 동봉 시 벡터+BM25 RRF 하이브리드). 실패 시 AI 는 빈 결과로 폴백 (non-fatal) |
| `POST` | `/api/internal/ai-logs` | AI | LLM 호출별 토큰·지연시간을 `ai_request_logs` 에 기록 (fire-and-forget, 실패 무시) |

요청·응답 스키마 및 인증 규약은 [`/docs/api-conventions.md §10`](./api-conventions.md) 참조.

큐 상태 확인:
```bash
docker exec stackup-rabbitmq rabbitmqctl list_queues -q name messages consumers
```
