# 용어집 (Glossary)

> 한국어 도메인 용어 ↔ 영어 식별자 매핑. 신규 도메인 용어 도입 시 본 표에 등록.

---

## 1. 사용자·인증

| 한국어 | 영어 (식별자) | 설명 |
|--------|---------------|------|
| 사용자 | `user` | GitHub OAuth로 가입한 사람 |
| 면접 준비생 | (그냥 user) | 페르소나 표현일 뿐 별도 엔티티 없음 |
| 회원 탈퇴 | `account withdrawal` / soft delete | `is_deleted = TRUE` |
| 액세스 토큰 | `access token` | JWT, 15분 |
| 리프레시 토큰 | `refresh token` | 14일, DB에 해시 저장 |
| 개인정보처리동의 | `user consent` | TOS / PRIVACY |

---

## 2. 자료

| 한국어 | 영어 | 설명 |
|--------|------|------|
| 이력서 | `resume` | PDF 파일 |
| 레포지토리 / 레포 | `repository` / `repo` | GitHub 저장소 |
| 분석 문서 | `analyzed document` | AI가 생성한 마크다운 |
| 컨텍스트 | `context` | 세션이 참조하는 분석 문서 집합 (`session_contexts`) |
| 청크 | `chunk` | 임베딩 단위 (분할된 텍스트 조각) |
| 임베딩 | `embedding` | 벡터 표현 |

---

## 3. 면접 세션

| 한국어 | 영어 | 비고 |
|--------|------|------|
| 면접 세션 | `interview session` | DB: `interview_sessions` |
| 면접 모드 | `mode` | `TECHNICAL` / `PERSONALITY` / `INTEGRATED` / `JOB_TAILORED` |
| 직무 맞춤 면접 | `job-tailored interview` | `mode=JOB_TAILORED`. 회사명+채용공고(JD)로 적합도·지원동기 질문 + 직무 적합도 피드백 |
| 면접 유형 | `interview type` | legacy 용어. MVP에서는 `mode`로 통합 |
| 직군 | `job category` | `FRONTEND` / `BACKEND` / `INFRA` / `DBA` |
| 인성 면접 | `personality interview` | `PERSONALITY` |
| 기술 면접 | `technical interview` | `TECHNICAL` |
| 종합 면접 | `integrated interview` | `INTEGRATED` |
| 질문 풀 | `question pool` | 세션 시작 시 Pro 모델이 생성 |
| 꼬리질문 | `follow-up question` | Flash + RAG로 실시간 생성 |
| 면접관 | `interviewer` | `interview_messages.role = INTERVIEWER` |
| 응시자 | `interviewee` / `candidate` | `INTERVIEWEE` (DB 컬럼 기준) |
| 시퀀스 번호 | `sequence_number` | 세션 내 메시지 순서 |
| 세션 메모 | `session memo` | 사용자 메모 (`interview_sessions.memo`) |

> 응시자: User Story에서는 `CANDIDATE`로 표기되기도 하나 DB schema는 `INTERVIEWEE`. **DB 기준으로 통일**.

---

## 4. 피드백·평가

| 한국어 | 영어 | 비고 |
|--------|------|------|
| 종합 피드백 | `session feedback` | 세션 종료 후 생성 |
| 종합 점수 | `overall score` | 0~100 |
| 기술 정확도 | `technical accuracy` | |
| 논리 점수 | `logic score` | |
| 커뮤니케이션 점수 | `communication score` | |
| 잘한 점 | `strengths summary` | |
| 아쉬운 점 | `weaknesses summary` | |
| 보완 키워드 | `improvement keywords` | JSONB |
| 성장 추이 | `growth trend` | 세션 간 점수 변화 |

---

## 5. 음성 분석

| 한국어 | 영어 | 비고 |
|--------|------|------|
| 말 속도 | `speaking rate (WPM)` | words per minute |
| 간투어 | `filler word` | "음", "어", "그" 등 |
| 침묵 시간 | `silence duration` | 초 단위 |
| 발음 정확도 | `pronunciation accuracy` | 0~1 |
| STT (음성 → 텍스트) | `Speech-to-Text` | |
| TTS (텍스트 → 음성) | `Text-to-Speech` | |

---

## 6. AI / RAG

| 한국어 | 영어 | 비고 |
|--------|------|------|
| 대형 언어 모델 | `LLM (Large Language Model)` | |
| 검색 증강 생성 | `RAG (Retrieval-Augmented Generation)` | |
| 벡터 검색 | `vector search` | pgvector |
| 유사도 | `similarity` | cosine 기본 |
| Pro 모델 | `pro model` | 품질 우선, 세션 시작 시 |
| Flash 모델 | `flash model` | 저지연, 꼬리질문 |
| 프롬프트 템플릿 | `prompt template` | LangChain |

---

## 7. 시스템·인프라

| 한국어 | 영어 | 비고 |
|--------|------|------|
| Core 서버 | `Core Server` | Spring Boot |
| AI 서버 | `AI Server` | FastAPI |
| RealTime 서버 | `RealTime Server` | Go |
| 메시지 브로커 | `message broker` | RabbitMQ |
| 객체 스토리지 | `object storage` | S3 / MinIO |
| 데드 레터 큐 | `Dead Letter Queue (DLQ)` | |
| 분산 추적 ID | `trace ID` | `X-Trace-Id` |
| 활동 로그 | `activity log` | |
| AI 요청 로그 | `AI request log` | |

---

## 8. 상태 (Status) — DB ENUM 기준

### resume / repository / document
| 코드 | 의미 |
|------|------|
| `PENDING` | 대기 중 (분석 미요청 또는 큐에 적재됨) |
| `ANALYZING` | 분석 진행 중 |
| `ANALYZED` | 분석 완료 |
| `FAILED` | 실패 |
| `ACTIVE` | 활성 (analyzed_documents 한정) |
| `ARCHIVED` | 보관 (analyzed_documents 한정) |

### interview_session
| 코드 | 의미 |
|------|------|
| `READY` | 생성 완료, 시작 전 |
| `IN_PROGRESS` | 진행 중 |
| `INTERRUPTED` | 비정상 중단 |
| `COMPLETED` | 정상 종료 |
| `CANCELLED` | 사용자가 취소 |

### interview_message
| 코드 | 의미 |
|------|------|
| `CREATED` | 생성됨 (음성 처리 대기 등) |
| `COMPLETED` | 완료 |
| `FAILED` | 실패 |

### message role
| 코드 | 의미 |
|------|------|
| `INTERVIEWER` | AI 면접관 |
| `INTERVIEWEE` | 사용자 |
| `SYSTEM` | 시스템 메시지 (세션 시작/종료 등) |

---

## 9. 페이즈 (Phase)

| Phase | 범위 |
|-------|------|
| `Phase 1 (MVP)` | 인증, 자료, AI 분석, 텍스트 면접 (US-01~20) |
| `Phase 2` | 음성 면접 (US-21~23) |
| `Phase 3` | 리포트 고도화·히스토리 (US-24~27) |
| `Phase 4` | 비언어 분석 (웹캠), 멀티 에이전트 |

---

## 10. 사용 규칙

- 코드·DB 식별자는 **영어** (위 표 기준)
- UI 표시 한국어는 **사용자 친화적 표현**으로 (예: `INTERVIEWEE` → "응시자")
- 문서 본문은 한국어 우선, 식별자는 영어 그대로
- 신규 용어 도입 시 본 표에 추가 + 변경 PR 본문에 명시
