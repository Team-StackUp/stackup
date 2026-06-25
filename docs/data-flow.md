# 데이터 흐름

> 핵심 시나리오 4개의 end-to-end 데이터 흐름. 각 단계는 [어떤 컴포넌트] → [어떤 저장소·메시지] 형식으로 표기한다.

---

## 1. GitHub 레포 분석 파이프라인 (US-10)

```
[사용자] 레포 선택
  → [Core] repositories INSERT (status=PENDING)
  → [Core] RabbitMQ publish: stackup.core-to-ai / analyze.repository
  → [AI] consume → GitHub API (파일 구조 + README + 주요 코드)
  → [AI] LLM 호출 (Gemini 3.1 Pro) → 프로젝트 요약 마크다운 생성
  → [AI] S3 PUT: analyzed/repository/{repo_id}/summary.md
  → [AI] 청킹 → 임베딩 → pgvector INSERT (Core API 경유)
  → [AI] RabbitMQ publish: stackup.ai-to-core / callback.analysis
  → [Core] consume → analyzed_documents INSERT
                  → repositories.status = ANALYZED
  → [Core] SSE broadcast: { type: 'REPO_STATE', state: 'ANALYZED', repositoryId }
  → [Frontend] EventSource 수신 → 상태 자동 갱신
```

**실패 시**:
- AI 분석 실패 → `callback.analysis` 발행 (`status: FAILED`) → `repositories.status = FAILED`
- 재시도 한도 초과 시 DLQ 이동, 운영자 수동 재시도

---

## 2. 이력서 분석 파이프라인 (US-09)

```
[사용자] PDF 업로드
  → [Core] S3 PUT: resumes/raw/{user_id}/{uuid}.pdf
  → [Core] resumes INSERT (status=PENDING, file_path=S3 key)
  → [Core] RabbitMQ publish: stackup.core-to-ai / analyze.resume
  → [AI] consume → S3 GET (PDF) → 텍스트 추출
  → [AI] LLM (Gemini 3.1 Pro) → 마크다운 변환 (기술 스택, 경력, 프로젝트 구조화)
  → [AI] S3 PUT: analyzed/resume/{resume_id}/summary.md
  → [AI] 청킹 → 임베딩 → pgvector INSERT (Core API 경유)
  → [AI] RabbitMQ publish: stackup.ai-to-core / callback.analysis
  → [Core] analyzed_documents INSERT (summary, tech_stack JSONB)
        → resumes.status = ANALYZED
  → [Frontend] SSE 수신 → UI 갱신
```

---

## 3. 면접 세션 흐름 (US-13 ~ US-20)

### 3.1 세션 생성 단계 — 첫 질문은 자기소개 고정

> **모든 면접의 첫 질문은 자기소개로 고정**한다. 이력서/레포 기반 질문 풀은 세션 생성 시점이
> 아니라 **자기소개 답변을 받은 뒤** 그 답변을 1차 근거로 생성한다 (실제 면접과 동일).

```
[사용자] 세션 설정 (모드/유형/직군/최대 질문 수/참조 문서)
  → [Core] interview_sessions INSERT (status=READY)
        → session_contexts INSERT (선택된 analyzed_documents 연결)
  → [Core] SessionCreatedEvent(AFTER_COMMIT) → 자기소개 질문(seq=1, category=SELF_INTRODUCTION) 고정 삽입
        (AI 호출 없음. TTS 만 요청. totalQuestionCount=1 — 자기소개가 첫 일반질문 1자리를 차지)
  → [Core] QuestionPersistedEvent → 첫 질문(자기소개) push + TTS
```

### 3.2 질문-답변 사이클 (반복)

```
[Core] 영속 후 RealtimeNotifyPublisher.publishToSession → [RealTime] SSE/WS → 첫 질문(자기소개) push
  → [Frontend] 표시 + 마이크 활성화
  → [사용자] 음성 답변 (Phase 2) 또는 텍스트
  → [Frontend] (음성 모드) WebRTC stream → RealTime
        ㄴ Phase 2 RealTime 분리 전: 청크를 REST POST로 업로드
  → [AI] Whisper API → 텍스트 변환
  → [Frontend] (텍스트 모드) WS send {type:"answer", content} → [RealTime] → POST /api/internal/sessions/{id}/messages
        ㄴ 레거시 직접 경로: POST /api/sessions/{id}/messages (프론트 WS 전환 전까지 병행)
  → [Core] interview_messages INSERT (role=INTERVIEWEE, parent=직전 질문)
  ── 직전 질문이 자기소개(SELF_INTRODUCTION)인 경우 ─────────────────────────────
  → [Core] SelfIntroAnsweredEvent → RabbitMQ publish: stackup.core-to-ai / generate.questions
        (payload.selfIntroAnswer = 자기소개 답변. 풀 크기 = generalQuestionCount-1 — 자기소개 1자리 예약. 꼬리질문 없음)
  → [AI] RAG 검색 + Gemini 3.1 Pro → 자기소개 답변을 1차 근거로 질문 풀 생성
  → [AI] RabbitMQ publish: stackup.ai-to-core / callback.questions (kind=POOL)
  → [Core] session_question_pool 저장 + 첫 일반질문 삽입 → 다음 질문 push
  ── 그 외 일반/꼬리질문 답변인 경우 ───────────────────────────────────────────
  → [Core] RabbitMQ publish: stackup.core-to-ai / generate.followup
  → [AI] 답변 평가 + 꼬리질문 생성 (Gemini 3.1 Flash + RAG)
        → 답변이 음성이면 음성 분석도 병행
  → [AI] RabbitMQ publish: stackup.ai-to-core / callback.questions (kind=FOLLOWUP)
  → [Core] interview_messages INSERT (role=INTERVIEWER, 꼬리질문)
        → message_voice_analyses INSERT (음성 모드일 경우)
  → [Core] 영속 후 RealtimeNotifyPublisher.publishToSession → [RealTime] WS/SSE → 다음 질문 push
```

#### 3.2-1 음성 답변 — 실시간 스트리밍 분기 (RT3, Phase 2)

배치 업로드(`POST .../messages/voice` multipart → `analyze.voice`) 대신, 마이크 오디오를 WS로 흘려 실시간 자막 + 즉시 다음 질문을 받는 저지연 경로.

```
[Frontend] POST /api/sessions/{id}/messages/voice/stream-begin
  → [Core] placeholder voiceInterviewee("(transcribing)") INSERT → { messageId, parentMessageId } 반환
[Frontend] WS /realtime/sessions/{id}/audio?access_token=&messageId=M
  → [RealTime] WSAudioHandler: 브라우저 WS ↔ AI WS(/internal/voice/stream) 순수 오디오 프록시 (PG·MQ 미접근)
  → [Frontend] 바이너리 오디오 프레임 업 ↕ AI가 transcript.partial/final JSON 다운
[AI] DeepgramLive(또는 MockLive)로 부분 자막 실시간 반환
  → [Frontend] {"type":"stop"} OR Deepgram UtteranceEnd → 발화 종료
[AI] 최종 transcript + 메트릭(WPM/filler/silence) 계산
  → RabbitMQ publish: stackup.ai-to-core / callback.voice { sessionId, interviewMessageId, transcript, metrics }
[Core] VoiceCallbackService(무변경) → placeholder completeWithTranscript + message_voice_analyses INSERT
  → AnswerSubmittedEvent → generate.followup → 기존 followup 파이프라인(다음 질문 + Phase1 TTS) 재사용
```

> `callback.voice` 이후는 배치 경로와 100% 동일(`VoiceCallbackService` 재사용). 신규는 "오디오가 AI에 닿는 경로(브라우저→RealTime WS→AI WS)" + Deepgram Live STT 뿐.

### 3.3 세션 종료

```
[사용자] 종료 버튼  OR  최대 질문/시간 도달
  → [Core] interview_sessions.status = COMPLETED, ended_at = now()
  → [Core] RabbitMQ publish: stackup.core-to-ai / generate.feedback (예정)
  → [AI] 전체 메시지 + 음성 분석 → 종합 평가 (Gemini 3.1 Pro)
  → [AI] S3 PUT: feedback/{session_id}/report.md
  → [AI] RabbitMQ publish: stackup.ai-to-core / callback.feedback (예정)
  → [Core] session_feedbacks INSERT
  → [Frontend] SSE → 리포트 페이지 자동 라우팅
```

---

## 4. 인증 흐름 (US-01)

```
[Frontend] /api/auth/github 호출
  → [Core] GitHub OAuth URL 발급 + state 발급
        → state는 PostgreSQL `oauth_states` 테이블에 저장 (TTL 5분)
        → 302 redirect
  → [GitHub] 사용자 동의 → callback URL?code=xxx&state=yyy
  → [Frontend] /api/auth/github/callback?code=xxx&state=yyy
  → [Core] state 검증 (oauth_states 조회 + 만료 체크 + DELETE)
        → GitHub Token Exchange → access_token
        → users UPSERT (github_id 기준, encrypted_github_access_token)
        → JWT access_token 발급 (15분)
        → JWT refresh_token 발급 → refresh_tokens INSERT (해시만 저장)
  → [Core] Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict
  → [Frontend] localStorage에 access_token 보관
```

**Refresh 흐름**:
```
[Frontend] 401 수신 → POST /api/auth/refresh (쿠키의 refresh_token)
  → [Core] refresh_tokens 조회 (해시 비교, is_revoked, expires_at 체크)
        → 새 access_token 발급, refresh_token 회전(rotate)
  → [Frontend] 원 요청 재시도
```

---

## 5. 분석 상태 실시간 알림 (US-11) — SSE 단일 경로

```
[AI 작업 시작/진행/완료]
  → AI Server: RabbitMQ publish (callback.analysis 등)
  → [Core] consume → DB 상태 갱신 + RealtimeNotifyPublisher.publishToUser/publishToDocument 발행
  → [RealTime] stackup.realtime consume → user/document 채널 SSE fan-out
  → [Frontend] EventSource 수신 → 상태 UI 갱신

SSE 끊김 시:
  → [Frontend] 자동 재연결 (EventSource 기본 동작)
  → [Frontend] 폴링 fallback: GET /api/documents/{id} (5초 간격)
```

> SSE 서빙 주체는 **RealTime 서버**다. Core는 `stackup.realtime`으로 발행만 하고, DB 영속(AFTER_COMMIT/트랜잭션 내 INSERT 이후) 시점에 발행하므로 SSE 페이로드의 ID는 항상 조회 가능하다. 멀티 인스턴스 시점에 RealTime fanout exchange + 인스턴스별 큐 도입 (Redis 미사용 결정 — [`architecture.md §4.5`](./architecture.md))

상세 이벤트 스펙은 [`event-stream.md`](./event-stream.md) 참조.

---

## 6. 시퀀스 다이어그램이 필요한 시나리오

다음 항목들은 별도 시퀀스 다이어그램(mermaid 또는 PlantUML)을 추가할 후보:

- [ ] WebRTC SDP/ICE 교환 (Phase 2)
- [ ] 멀티 에이전트 면접 (LangGraph) (Phase 4)
- [ ] AI Worker 수평 확장 시 메시지 분배

> 작성 시 본 문서에 inline mermaid 또는 별도 `docs/sequence/*.md` 로 분리.
