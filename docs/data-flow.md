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

### 3.1 세션 생성 단계

```
[사용자] 세션 설정 (모드/유형/직군/최대 질문 수/참조 문서)
  → [Core] interview_sessions INSERT (status=READY)
        → session_contexts INSERT (선택된 analyzed_documents 연결)
  → [Core] RabbitMQ publish: stackup.core-to-ai / generate.questions
  → [AI] RAG 검색 (pgvector 유사도, Core API 경유) → 컨텍스트 추출
  → [AI] Gemini 3.1 Pro → 첫 질문 1개 생성 (RAG 컨텍스트 기반)
  → [AI] RabbitMQ publish: stackup.ai-to-core / callback.questions (kind=FIRST)
  → [Core] interview_messages INSERT (첫 INTERVIEWER 메시지) + session.markInProgress() + SSE push
```

### 3.2 질문-답변 사이클 (반복)

```
[Core] SSE → 첫 질문 push (kind=FIRST 콜백 처리 후)
  → [Frontend] 표시 + 마이크 활성화
  → [사용자] 음성 답변 (Phase 2) 또는 텍스트
  → [Frontend] (음성 모드) WebRTC stream → RealTime
        ㄴ Phase 2 RealTime 분리 전: 청크를 REST POST로 업로드
  → [AI] Whisper API → 텍스트 변환
  → [Frontend → Core] POST /api/sessions/{id}/messages (텍스트 답변)
  → [Core] interview_messages INSERT (role=INTERVIEWEE, parent=직전 질문)
  → [Core] RabbitMQ publish: stackup.core-to-ai / generate.followup
  → [AI] 답변 평가 + 꼬리질문 생성 (Gemini 3.1 Flash + RAG)
        → 답변이 음성이면 음성 분석도 병행
  → [AI] RabbitMQ publish: stackup.ai-to-core / callback.questions (kind=FOLLOWUP 또는 kind=END)
  → [Core] kind=FOLLOWUP: interview_messages INSERT (role=INTERVIEWER, 꼬리질문)
                         + message_voice_analyses INSERT (음성 모드일 경우)
                         + max 도달 시 자동 종료 + SSE push
           kind=END: AI 조기 종료 신호 → session.end() + SSE STATE push
  → [Core] SSE SESSION_MESSAGE → 다음 질문 push
```

### 3.3 세션 종료

세션은 다음 세 경로로 종료된다:

1. **최대 질문 수 도달** — FOLLOWUP 콜백 처리 시 Core 카운터가 maxQuestions 도달을 감지 → 자동으로 `session.end()` 호출 + SSE SESSION_STATE push
2. **AI `kind=END` 조기 종료 신호** — AI가 답변 품질 등을 판단해 FOLLOWUP 대신 END 신호 전송 → Core `session.end()` + SSE SESSION_STATE push
3. **사용자 수동 종료** — `POST /api/sessions/{id}/end` 호출 → 즉시 COMPLETED 또는 CANCELLED 처리

```
(공통 종료 이후)
  → [Core] interview_sessions.status = COMPLETED, ended_at = now()
  → [Core] RabbitMQ publish: stackup.core-to-ai / generate.feedback (예정 — Sprint 3)
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
  → [Core] consume → DB 상태 갱신 + 인메모리 채널로 push
  → [Core] SSE 엔드포인트가 user별 구독자에게 broadcast
  → [Frontend] EventSource 수신 → 상태 UI 갱신

SSE 끊김 시:
  → [Frontend] 자동 재연결 (EventSource 기본 동작)
  → [Frontend] 폴링 fallback: GET /api/documents/{id} (5초 간격)
```

> 단일 Core 인스턴스에서는 인메모리 채널로 충분. 멀티 인스턴스 시점에 RabbitMQ fanout exchange 도입 (Redis 미사용 결정 — [`architecture.md §4.5`](./architecture.md))

상세 이벤트 스펙은 [`event-stream.md`](./event-stream.md) 참조.

---

## 6. 시퀀스 다이어그램이 필요한 시나리오

다음 항목들은 별도 시퀀스 다이어그램(mermaid 또는 PlantUML)을 추가할 후보:

- [ ] WebRTC SDP/ICE 교환 (Phase 2)
- [ ] 멀티 에이전트 면접 (LangGraph) (Phase 4)
- [ ] AI Worker 수평 확장 시 메시지 분배

> 작성 시 본 문서에 inline mermaid 또는 별도 `docs/sequence/*.md` 로 분리.
