# StackUp — Claude Code 프로젝트 컨텍스트

> IT 직군 멀티모달 AI 면접 시뮬레이터

---

## 1. 프로젝트 개요

StackUp은 IT 취업 준비생 및 이직 준비자(프론트엔드, 백엔드, 인프라, DBA)를 대상으로 한 AI 기반 모의 면접 플랫폼이다. GitHub 레포지토리와 이력서를 분석하여 개인 맞춤 면접 질문을 생성하고, 실시간 음성 기반 면접 세션을 제공하며, 비언어적 피드백(시선, 자세, 음성 분석)까지 포함한 종합 리포트를 생성한다.

### 핵심 차별점 (단순 ChatGPT 질문과의 차이)
- 이력서 + GitHub 레포 연동 기반 RAG 개인화 질문 생성
- 실시간 음성 대화 (STT/TTS) 기반 꼬리질문 자동 생성
- 비언어적 분석 (시선 처리, 자세, 말 속도, 간투어 빈도)
- 세션 히스토리 추적 및 점수 추이 시각화
- 면접 세션 리플레이 및 습관 파악

### 팀 구성 (4인)
| 이름 | 역할 | 주 담당 |
|------|------|---------|
| 박상우 | 백엔드 (Core) | Spring Boot, OAuth, 세션/리포트 API, DB |
| 정준모 | AI 및 풀스택 | AI 서빙, LangChain/RAG, STT/TTS, 프롬프트 설계 |
| 조서현 | 풀스택 | RealTime 서버(Go), Core-AI 연동, GitHub 레포 분석 |
| 신재호 | 프론트엔드 | React UI, 미디어 스트림, 웹캠 연동 |

---

## 2. 시스템 아키텍처 (확정)

```
                        ┌─────────────────────────┐
                        │  Frontend (React)        │
                        │  CloudFront + S3 배포     │
                        └────────┬────────────────┘
                                 │
                        ┌────────▼────────────────┐
                        │  Nginx API Gateway       │
                        └───┬─────────────┬───────┘
                            │             │
              ┌─────────────▼──┐   ┌──────▼──────────────┐
              │ Core Server    │   │ RealTime Server      │
              │ (Spring Boot)  │   │ (Go)                 │
              │                │   │ - WebRTC             │
              │ - GitHub OAuth │   │ - WebSocket          │
              │ - 회원관리      │   │ - SSE                │
              │ - 세션/리포트   │   │ - 세션 실시간 관리     │
              │ - CRUD API     │   └──────────────────────┘
              └───────┬───────┘
                      │
              ┌───────▼───────┐
              │  RabbitMQ      │ ← Message Broker (Core ↔ AI 비동기 통신)
              └───────┬───────┘
                      │
              ┌───────▼────────────────────┐
              │  AI Server (Python/FastAPI) │
              │  - LangChain 기반 RAG       │
              │  - 질문 생성 / 꼬리질문      │
              │  - 이력서·레포 분석          │
              │  - 음성 분석                │
              └───┬───────┬───────┬───────┘
                  │       │       │
          ┌───────▼┐ ┌───▼────┐ ┌▼──────────┐
          │External │ │Local   │ │ VectorDB  │
          │LLM APIs │ │LLM     │ │           │
          │(Gemini, │ │        │ │           │
          │ChatGPT) │ │        │ │           │
          └────────┘ └────────┘ └───────────┘

              ┌──────────────┐     ┌────────────────┐
              │ PostgreSQL   │     │ Content S3     │
              │ + pgvector   │     │ (이력서 PDF,    │
              │              │     │  분석 MD,       │
              │              │     │  면접 오디오)    │
              └──────────────┘     └────────────────┘

              ┌──────────────┐
              │ Redis        │ ← ephemeral session state
              └──────────────┘
```

### 핵심 통신 흐름
- **Frontend → Nginx → Core Server**: REST API (인증, CRUD, 세션 생성)
- **Frontend → Nginx → RealTime Server**: WebSocket/SSE (실시간 면접 세션)
- **Core Server → RabbitMQ → AI Server**: 비동기 AI 작업 요청 (분석, 질문 생성)
- **AI Server → RabbitMQ → Core Server**: 결과 콜백
- **AI Server → External LLM API / Local LLM**: 실제 추론
- **AI Server → VectorDB**: RAG 검색 (임베딩 기반 유사 문서/청크 검색)
- **Core Server → PostgreSQL**: 영속 데이터 (유일한 DB 직접 접근 주체)
- **Core/AI Server → S3**: 대용량 콘텐츠 저장/조회

### LLM 이중 모델 전략
- **Pro 모델**: 세션 시작 시 사용자 컨텍스트(이력서 + GitHub) 기반 질문 풀 생성
- **Flash 모델 + RAG**: 세션 중 실시간 꼬리질문 생성 (저지연 우선)
- **Local LLM**: 카메라 기반 시선/자세 분석 (MediaPipe)

---

## 3. 기술 스택

### Backend
| 영역 | 기술 | 비고 |
|------|------|------|
| Core Server | Java, Spring Boot, Spring AI | 인증인가, CRUD, 비즈니스 로직 |
| ORM/Query | JPA/Hibernate + QueryDSL | 간단 쿼리는 JPA, 동적 쿼리는 QueryDSL |
| Migration | Flyway | 스키마 버전 관리 |
| AI Server | Python, FastAPI, LangChain | 모델 서빙, RAG, 프롬프트 체이닝 |
| RealTime Server | Go | WebRTC, WebSocket, SSE 처리 |
| Message Broker | RabbitMQ | Core ↔ AI 비동기 통신 |

### Database & Storage
| 영역 | 기술 | 비고 |
|------|------|------|
| Primary DB | PostgreSQL + pgvector | 관계형 데이터 + 벡터 임베딩 |
| Cache | Redis | 세션 상태, 토큰 등 ephemeral 데이터 |
| Object Storage | AWS S3 | 이력서 PDF, 분석 마크다운, 오디오 파일 |

### Frontend
| 영역 | 기술 | 비고 |
|------|------|------|
| Framework | React (TypeScript) | SPA, 반응형 웹 (모바일 웹뷰 대응) |
| API 타입 생성 | springdoc-openapi → openapi-typescript | 타입 안전성 확보 |
| 배포 | CloudFront + S3 | 정적 호스팅 |

### Infra & DevOps
| 영역 | 기술 | 비고 |
|------|------|------|
| API Gateway | Nginx | 리버스 프록시, 라우팅 |
| Container | Docker Compose (로컬), K8s (배포) | 관리형 컨테이너 서비스 미사용 |
| 분산 추적 | X-Trace-Id 헤더 기반 | 서비스 간 요청 추적 |

### AI/ML
| 영역 | 기술 | 비고 |
|------|------|------|
| LLM API | Gemini, ChatGPT | 외부 API |
| Local LLM | 미정 | 카메라 분석 등 |
| 음성 | STT/TTS (구체 서비스 미정) | 개발 용어 영어 혼용 대응 필요 |
| 영상 분석 | MediaPipe | 시선 처리, 자세 분석 |
| 실시간 통신 | WebRTC | 음성/영상 스트리밍 |

---

## 4. 핵심 기능 명세

### MVP (Phase 1)
1. **GitHub OAuth 로그인 및 레포지토리 선택**
   - GitHub OAuth로 로그인
   - 사용자의 레포지토리 목록 조회 → 면접에 사용할 레포 선택
   - 선택된 레포의 파일 구조, README, 코드를 읽어 프로젝트 요약 마크다운 생성 → S3 저장

2. **이력서 업로드 및 분석**
   - PDF/웹 링크 형태 이력서 업로드
   - 텍스트 추출 → 마크다운 변환 → S3 저장
   - 분석 결과(기술 스택, 경력, 프로젝트) 메타데이터를 DB에 저장

3. **RAG 기반 맞춤 질문 생성**
   - 이력서 + GitHub 분석 마크다운을 청킹 → 임베딩 → pgvector 저장
   - 면접 세션 시작 시 RAG로 관련 컨텍스트 검색 → Pro 모델로 질문 풀 생성
   - 카테고리별 질문: CS 기초, 프로젝트 경험, 기술 선택 이유 등

4. **텍스트 기반 AI 면접 세션**
   - 질문 → 답변 → 꼬리질문 사이클 (Flash 모델 + RAG)
   - 답변 구체성, 논리성, 구조(STAR) 평가
   - 부족한 부분 기반 꼬리질문 자동 생성
   - 세션 설정: 면접 유형(인성/기술/라이브코딩), 직군(프론트/백/인프라/DBA), 최대 질문 수, 최대 시간

5. **면접 히스토리 관리**
   - 세션 리스트 조회, 상세 조회
   - 잘한 점 / 아쉬운 점 텍스트 요약
   - 보완할 기술 키워드 추천

### Phase 2 (음성)
6. **실시간 음성 면접 세션**
   - STT로 사용자 답변 실시간 텍스트 변환
   - TTS로 AI 면접관 음성 출력
   - 음성 분석: 말하기 속도(WPM), 간투어("음", "그", "어") 빈도, 침묵 시간, 발음 정확도

### Phase 3 (리포트 고도화)
7. **종합 피드백 리포트**
   - 기술 정확도, 논리 점수, 커뮤니케이션 점수
   - 음성 분석 결과 통합
   - 다음 학습 키워드 추천 (JPA 영속성, 네트워크 기초 등)
   - 세션 간 점수 추이 시각화

### Phase 4 (확장)
8. **웹캠 기반 비언어 분석** (부가기능)
   - 카메라 응시 비율
   - 시선 방향 분포 (아래/옆 보는 시간 비율)
   - 자세 분석
   - 온라인/오프라인 모드 선택 (상체만 / 전신)

9. **멀티 에이전트 면접** (부가기능)
   - 기술팀장, PM, 인사팀 등 여러 AI 면접관이 순차/동시 질문
   - LangGraph 기반 멀티에이전트 구현

---

## 5. 데이터베이스 스키마 (v4 + 개선사항)

### 현재 스키마 (13 테이블)
```
users → refresh_tokens, user_consents, repositories, resumes,
         interview_sessions, activity_logs, ai_request_logs
repositories ─┐
resumes ──────┼→ analyzed_documents → session_contexts ← interview_sessions
interview_sessions → interview_messages → message_voice_analyses
interview_sessions → session_feedbacks
```

### 테이블 목록 및 역할

| # | 테이블 | 역할 |
|---|--------|------|
| 1 | `users` | GitHub OAuth 사용자 정보 |
| 2 | `refresh_tokens` | JWT refresh token 저장 |
| 3 | `user_consents` | 개인정보처리동의 기록 |
| 4 | `repositories` | 면접 분석용 GitHub 저장소 메타데이터 |
| 5 | `resumes` | 이력서 메타데이터 (PDF/웹 URL), 실 파일은 S3 |
| 6 | `analyzed_documents` | AI 분석 결과 메타데이터 + S3 경로 (이력서/레포 공통) |
| 7 | `interview_sessions` | 면접 세션 설정·상태·히스토리 |
| 8 | `session_contexts` | 세션 ↔ 분석 문서 N:M 연결 |
| 9 | `interview_messages` | 면접 질문·답변 시퀀스 (parent_message_id로 꼬리질문 트리) |
| 10 | `message_voice_analyses` | 답변별 음성 분석 결과 (1:1) |
| 11 | `session_feedbacks` | 종합 피드백 리포트 (1:1) |
| 12 | `activity_logs` | 사용자 행동 로그 |
| 13 | `ai_request_logs` | AI 서버 요청/응답 로그 (토큰, 지연시간, 에러) |

### 현재 스키마 DDL (v4)

```sql
-- 1. users
CREATE TABLE users (
    id                    BIGSERIAL     PRIMARY KEY,
    github_id             BIGINT        NOT NULL UNIQUE,
    github_username       VARCHAR(100)  NOT NULL,
    email                 VARCHAR(255),
    avatar_url            VARCHAR(500),
    github_access_token   VARCHAR(500)  NOT NULL,  -- ⚠️ 암호화 필요
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    is_deleted            BOOLEAN       NOT NULL DEFAULT FALSE
);

-- 2. refresh_tokens
CREATE TABLE refresh_tokens (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(500)  NOT NULL UNIQUE,
    device_info VARCHAR(500),
    expires_at  TIMESTAMP     NOT NULL,
    is_revoked  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 3. user_consents
CREATE TABLE user_consents (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    consent_type    VARCHAR(50)  NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    is_agreed       BOOLEAN      NOT NULL DEFAULT TRUE,
    agreed_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMP,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 4. repositories
CREATE TABLE repositories (
    id              BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    github_repo_id  BIGINT        NOT NULL,
    repo_name       VARCHAR(255)  NOT NULL,
    repo_full_name  VARCHAR(500)  NOT NULL,
    repo_url        VARCHAR(500)  NOT NULL,
    default_branch  VARCHAR(100)  DEFAULT 'main',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    last_synced_at  TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE
);

-- 5. resumes
CREATE TABLE resumes (
    id                BIGSERIAL      PRIMARY KEY,
    user_id           BIGINT         NOT NULL REFERENCES users(id),
    original_filename VARCHAR(500)   NOT NULL,
    file_path         VARCHAR(1000)  NOT NULL,  -- S3 key
    file_type         VARCHAR(20)    NOT NULL,
    file_size         BIGINT,
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE
);

-- 6. analyzed_documents
CREATE TABLE analyzed_documents (
    id             BIGSERIAL      PRIMARY KEY,
    source_type    VARCHAR(20)    NOT NULL,    -- 'RESUME' | 'REPOSITORY'
    source_id      BIGINT         NOT NULL,    -- ⚠️ Polymorphic FK (FK 강제 불가)
    document_path  VARCHAR(1000)  NOT NULL,    -- S3 key (분석 마크다운)
    summary        VARCHAR(2000),
    tech_stack     JSONB,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted     BOOLEAN        NOT NULL DEFAULT FALSE
);

-- 7. interview_sessions
CREATE TABLE interview_sessions (
    id                    BIGSERIAL    PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users(id),
    title                 VARCHAR(200),
    memo                  TEXT,
    mode                  VARCHAR(20)  NOT NULL,   -- ONLINE / OFFLINE
    interview_type        VARCHAR(30)  NOT NULL,   -- PERSONALITY / TECHNICAL / LIVE_CODING / INTEGRATED
    job_category          VARCHAR(30)  NOT NULL,   -- FRONTEND / BACKEND / INFRA / DBA
    max_questions         INT          NOT NULL DEFAULT 10,
    max_duration_minutes  INT          NOT NULL DEFAULT 60,
    status                VARCHAR(20)  NOT NULL DEFAULT 'READY',
    -- READY / IN_PROGRESS / INTERRUPTED / COMPLETED / CANCELLED
    total_question_count  INT          DEFAULT 0,
    started_at            TIMESTAMP,
    ended_at              TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 8. session_contexts
CREATE TABLE session_contexts (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT    NOT NULL REFERENCES interview_sessions(id),
    document_id BIGINT    NOT NULL REFERENCES analyzed_documents(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 9. interview_messages
CREATE TABLE interview_messages (
    id                BIGSERIAL      PRIMARY KEY,
    session_id        BIGINT         NOT NULL REFERENCES interview_sessions(id),
    sequence_number   INT            NOT NULL,
    role              VARCHAR(20)    NOT NULL,  -- INTERVIEWER / INTERVIEWEE / SYSTEM
    content           TEXT,
    audio_file_path   VARCHAR(1000),            -- S3 key
    parent_message_id BIGINT         REFERENCES interview_messages(id),
    status            VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    -- CREATED / COMPLETED / FAILED
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- 10. message_voice_analyses
CREATE TABLE message_voice_analyses (
    id                     BIGSERIAL PRIMARY KEY,
    message_id             BIGINT    NOT NULL UNIQUE REFERENCES interview_messages(id),
    speaking_rate_wpm      FLOAT,
    silence_duration_sec   FLOAT,
    filler_word_counts     JSONB,
    pronunciation_accuracy FLOAT,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 11. session_feedbacks
CREATE TABLE session_feedbacks (
    id                   BIGSERIAL      PRIMARY KEY,
    session_id           BIGINT         NOT NULL UNIQUE REFERENCES interview_sessions(id),
    overall_score        FLOAT,
    technical_accuracy   FLOAT,
    logic_score          FLOAT,
    communication_score  FLOAT,
    strengths_summary    TEXT,
    weaknesses_summary   TEXT,
    improvement_keywords JSONB,
    report_file_path     VARCHAR(1000),
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted           BOOLEAN        NOT NULL DEFAULT FALSE
);

-- 12. activity_logs
CREATE TABLE activity_logs (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       REFERENCES users(id),
    action        VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(30),
    resource_id   BIGINT,
    detail        JSONB,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 13. ai_request_logs
CREATE TABLE ai_request_logs (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       REFERENCES users(id),
    session_id    BIGINT       REFERENCES interview_sessions(id),
    request_type  VARCHAR(50)  NOT NULL,
    model_name    VARCHAR(100),
    input_tokens  INT,
    output_tokens INT,
    latency_ms    INT,
    status        VARCHAR(20)  NOT NULL,
    error_message TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### ⚠️ 식별된 스키마 개선 필수 사항 (우선순위순)

**즉시 수정 (Flyway V1 마이그레이션 전)**
1. **TIMESTAMP → TIMESTAMPTZ 전환**: 모든 테이블의 TIMESTAMP을 TIMESTAMPTZ로 변경 (타임존 이슈 방지)
2. **CHECK 제약조건 추가**: status, mode, interview_type, job_category, role 등 모든 분류 컬럼에 허용값 제한
3. **interview_messages.sequence_number UNIQUE 제약**: `(session_id, sequence_number)` 복합 유니크 인덱스
4. **analyzed_documents 다형성 FK 해결**: `source_type + source_id` → `resume_id BIGINT NULL REFERENCES resumes(id)` + `repository_id BIGINT NULL REFERENCES repositories(id)` + CHECK (둘 중 하나만 NOT NULL)
5. **github_access_token 암호화**: 평문 저장 금지, 애플리케이션 레벨 AES 암호화 적용, 컬럼명을 `encrypted_github_access_token`으로 변경
6. **interview_messages 빈 메시지 방지**: `CHECK (content IS NOT NULL OR audio_file_path IS NOT NULL)`

**후속 개선**
7. **로그 테이블 파티셔닝**: activity_logs, ai_request_logs에 `PARTITION BY RANGE (created_at)` 적용
8. **Partial Index 활용**: soft delete 테이블에 `WHERE is_deleted = FALSE` partial index
9. **session_feedbacks 점수 확장**: 면접 타입별 다른 평가 항목 → JSONB scores 필드 고려
10. **S3 path 컨벤션 통일**: bucket은 환경변수, key만 DB 저장

### 미반영 도메인 (추후 테이블 추가 예정)
- 시선/자세 분석 결과 테이블 (Phase 4)
- 면접 노트 (질문별 사용자 메모 + LLM Enrich)
- 면접 대상 회사 관리 (target_companies)

---

## 6. 데이터 흐름 상세

### 6.1 GitHub 레포 분석 파이프라인
```
사용자 레포 선택
  → Core Server: repositories 테이블에 레코드 생성 (status: PENDING)
  → Core Server → RabbitMQ: 분석 요청 발행
  → AI Server: GitHub API로 레포 탐색 (파일 구조, README, 코드)
  → AI Server: 프로젝트 요약 마크다운 생성
  → AI Server → S3: 마크다운 업로드
  → AI Server: 마크다운 청킹 → 임베딩 생성 → pgvector 저장
  → AI Server → RabbitMQ: 분석 완료 콜백
  → Core Server: analyzed_documents 레코드 생성, repositories.status → ANALYZED
```

### 6.2 이력서 분석 파이프라인
```
사용자 이력서 업로드 (PDF)
  → Core Server → S3: 원본 파일 업로드
  → Core Server: resumes 테이블에 레코드 생성 (status: PENDING)
  → Core Server → RabbitMQ: 분석 요청 발행
  → AI Server: PDF → 텍스트 추출 → 마크다운 변환
  → AI Server → S3: 분석 마크다운 업로드
  → AI Server: 마크다운 청킹 → 임베딩 생성 → pgvector 저장
  → AI Server → RabbitMQ: 분석 완료 콜백
  → Core Server: analyzed_documents 레코드 생성, resumes.status → ANALYZED
```

### 6.3 면접 세션 흐름
```
세션 생성 (설정: 유형, 직군, 참고 문서 선택)
  → Core Server: interview_sessions 생성, session_contexts 연결
  → Core Server → RabbitMQ: 질문 풀 생성 요청 (Pro 모델)
  → AI Server: RAG로 관련 컨텍스트 검색 → 질문 풀 생성
  → 세션 시작

질문-답변 사이클 (반복):
  → RealTime Server → Frontend: 질문 전송 (WebSocket/SSE)
  → Frontend: 사용자 답변 (텍스트 or 음성 → STT)
  → RealTime Server: interview_messages 기록 요청
  → Core Server: 메시지 저장
  → Core Server → RabbitMQ: 꼬리질문 생성 요청 (Flash 모델 + RAG)
  → AI Server: 답변 평가 + 꼬리질문 생성
  → 반복...

세션 종료:
  → Core Server → RabbitMQ: 피드백 리포트 생성 요청
  → AI Server: 종합 평가 생성
  → Core Server: session_feedbacks 저장
```

### 6.4 저장소 전략 (Hybrid)
| 데이터 종류 | 저장소 | 이유 |
|-------------|--------|------|
| 사용자/세션/메시지 메타 | PostgreSQL | 관계형 쿼리, 트랜잭션 |
| 벡터 임베딩 | PostgreSQL (pgvector) | 단일 DB 운영 |
| 분석 마크다운 (이력서/레포) | S3 | 대용량 텍스트 |
| 면접 오디오 파일 | S3 | 대용량 바이너리 |
| 이력서 원본 PDF | S3 | 대용량 바이너리 |
| 실시간 세션 상태 | Redis | 빠른 읽기/쓰기, TTL |

---

## 7. API 설계 가이드라인

### 접근 원칙
- **PostgreSQL 직접 접근**: Core Server만 가능 (AI Server, RealTime Server는 API 또는 RabbitMQ 경유)
- **분산 추적**: 모든 요청에 `X-Trace-Id` 헤더 전파
- **API 문서**: springdoc-openapi로 OpenAPI 스펙 자동 생성 → 프론트에서 openapi-typescript로 타입 생성
- **AI 작업 상태**: SSE로 프론트에 실시간 전달

### 주요 API 그룹 (Core Server)
```
POST   /api/auth/github          # GitHub OAuth 로그인
POST   /api/auth/refresh          # 토큰 갱신
DELETE /api/auth/logout           # 로그아웃

GET    /api/users/me              # 내 정보 조회
PUT    /api/users/me              # 내 정보 수정
DELETE /api/users/me              # 회원 탈퇴 (soft delete)

GET    /api/repositories          # 내 GitHub 레포 목록
POST   /api/repositories          # 분석용 레포 등록
GET    /api/repositories/:id      # 레포 상세 (분석 상태 포함)

POST   /api/resumes               # 이력서 업로드
GET    /api/resumes               # 내 이력서 목록
GET    /api/resumes/:id           # 이력서 상세

GET    /api/documents             # 분석된 문서 목록
GET    /api/documents/:id         # 분석 문서 상세

POST   /api/sessions              # 면접 세션 생성
GET    /api/sessions              # 세션 히스토리 목록
GET    /api/sessions/:id          # 세션 상세 (메시지, 피드백 포함)
PATCH  /api/sessions/:id/start    # 세션 시작
PATCH  /api/sessions/:id/end      # 세션 종료

GET    /api/sessions/:id/feedback # 세션 피드백 조회
GET    /api/users/me/stats        # 내 면접 통계 (점수 추이 등)
```

---

## 8. 개발 환경 및 컨벤션

### 로컬 개발 환경
- Docker Compose로 PostgreSQL, Redis, RabbitMQ, MinIO(S3 대체) 실행
- 각 서버(Core, AI, RealTime)는 독립 프로세스로 실행

### 코드 컨벤션
- **Java (Core Server)**: Spring Boot 표준 패키지 구조
  ```
  com.stackup.core
  ├── config/        # 설정 (Security, RabbitMQ, S3 등)
  ├── domain/        # Entity, Repository, Enum
  ├── service/       # 비즈니스 로직
  ├── controller/    # REST API
  ├── dto/           # Request/Response DTO
  ├── exception/     # 커스텀 예외
  └── infra/         # 외부 연동 (GitHub API, S3, RabbitMQ publisher)
  ```
- **Python (AI Server)**: FastAPI + LangChain
  ```
  ai_server/
  ├── api/           # FastAPI 라우터 (RabbitMQ consumer 포함)
  ├── chain/         # LangChain 체인 정의
  ├── rag/           # RAG 파이프라인 (청킹, 임베딩, 검색)
  ├── analyzer/      # 이력서/레포 분석 로직
  ├── voice/         # STT/TTS, 음성 분석
  ├── model/         # Pydantic 모델
  └── config/        # 설정
  ```
- **Go (RealTime Server)**
  ```
  realtime/
  ├── handler/       # WebSocket, SSE, WebRTC 핸들러
  ├── session/       # 세션 상태 관리
  ├── middleware/     # 인증, 로깅
  └── config/
  ```
- **React (Frontend)**
  ```
  src/
  ├── components/    # 재사용 컴포넌트
  ├── pages/         # 라우트별 페이지
  ├── hooks/         # 커스텀 훅
  ├── api/           # API 호출 (자동 생성 타입 활용)
  ├── stores/        # 상태 관리
  └── types/         # 타입 정의 (openapi-typescript 생성)
  ```

### Git 브랜치 전략
- `main`: 배포 브랜치
- `develop`: 통합 개발 브랜치
- `feature/*`: 기능 개발
- `hotfix/*`: 긴급 수정

---

## 9. MVP 개발 순서 (권장)

```
Week 1-2: 프로젝트 세팅 + DB 스키마 확정
  ├── Docker Compose 환경 구성 (PostgreSQL, Redis, RabbitMQ, MinIO)
  ├── Flyway 초기 마이그레이션 (V1__init.sql)
  ├── Spring Boot 프로젝트 스캐폴딩 + JPA Entity 매핑
  ├── FastAPI 프로젝트 스캐폴딩
  └── Go RealTime 서버 스캐폴딩

Week 3-4: 인증 + GitHub 연동
  ├── GitHub OAuth 플로우 구현 (Core Server)
  ├── JWT 발급/갱신/검증 (access + refresh)
  ├── GitHub API 연동: 레포 목록 조회, 파일/README 읽기
  ├── 레포 분석 파이프라인: 레포 → 마크다운 요약 → S3 저장
  └── RabbitMQ 기본 연동 (Core ↔ AI 메시지 교환)

Week 5-6: 이력서 분석 + RAG 파이프라인
  ├── 이력서 업로드 API (S3 연동)
  ├── PDF → 텍스트 → 마크다운 변환
  ├── 청킹 전략 구현 + 임베딩 생성
  ├── pgvector 저장 + 유사도 검색
  └── RAG 기반 질문 생성 프롬프트 설계/테스트

Week 7-8: 면접 세션 MVP
  ├── 세션 CRUD API
  ├── 세션 시작 → 질문 풀 생성 → 첫 질문 전달
  ├── 답변 수신 → 평가 → 꼬리질문 생성 사이클
  ├── 세션 종료 → 피드백 생성
  └── React 면접 세션 UI (텍스트 기반)

Week 9-10: STT/TTS + 음성 면접
  ├── STT 연동 (브라우저 → 서버 스트리밍)
  ├── TTS 연동 (면접관 음성 출력)
  ├── 음성 분석 모듈 (WPM, filler words, 침묵)
  └── RealTime 서버 WebSocket 연동

Week 11-12: 리포트 + 히스토리 + 통합 테스트
  ├── 피드백 리포트 고도화
  ├── 세션 히스토리 대시보드
  ├── 점수 추이 시각화
  └── 통합 테스트, 버그 수정

Week 13-14: 사용자 테스트 + 발표 준비
  ├── 사용자 테스트 (5명+)
  ├── 성능 최적화
  ├── UI/UX 개선
  └── 발표 자료 준비
```

---

## 10. 주의사항 및 원칙

### 설계 원칙
- **MVP 우선**: 불필요한 테이블/기능은 과감히 제거. "나중에 추가하겠지" 테이블은 만들지 않음
- **Hybrid Storage**: 구조화 데이터 → PostgreSQL, 대용량 콘텐츠 → S3, 임시 상태 → Redis
- **청킹 기반 RAG**: 전체 문서를 LLM에 넣지 않고, 청킹 → 임베딩 → 유사도 검색 → 관련 컨텍스트만 전달
- **DB 접근 격리**: PostgreSQL 직접 접근은 Core Server만. AI/RealTime 서버는 API 또는 RabbitMQ 경유
- **코드 기반 문서화**: 스키마의 모든 테이블과 컬럼에 역할/용도 코멘트 필수

### 보안
- GitHub access token은 반드시 암호화 저장
- JWT refresh token은 해시만 저장 (원본 저장 금지)
- 이력서에 민감정보 포함 가능 → 개인정보처리동의 기록 필수
- S3 path에는 key만 저장 (bucket은 환경변수)

### 성능
- 꼬리질문 생성 지연: 3초 이내 목표 (Flash 모델 + RAG)
- STT 인식 정확도: 90% 이상 (개발 용어 영어 혼용 환경)
- 로그 테이블(activity_logs, ai_request_logs)은 파티셔닝 + 보관 정책 설계

---

## 부록: 면접 세션 설정값 ENUM 정리

```
mode:           ONLINE | OFFLINE
interview_type: PERSONALITY | TECHNICAL | LIVE_CODING | INTEGRATED
job_category:   FRONTEND | BACKEND | INFRA | DBA
session_status: READY | IN_PROGRESS | INTERRUPTED | COMPLETED | CANCELLED
message_role:   INTERVIEWER | INTERVIEWEE | SYSTEM
message_status: CREATED | COMPLETED | FAILED
repo_status:    PENDING | ANALYZING | ANALYZED | FAILED
resume_status:  PENDING | ANALYZING | ANALYZED | FAILED
doc_status:     ACTIVE | ARCHIVED
```
