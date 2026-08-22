# 데이터베이스 가이드

> PostgreSQL + pgvector. 스키마는 Flyway로 버전 관리한다. **Core Server만 직접 접근**한다 ([architecture.md §4.1](./architecture.md)).

---

## 1. 스키마 개요 (13 테이블)

```
users → refresh_tokens, user_consents, repositories, resumes,
        interview_sessions, activity_logs, ai_request_logs

repositories ─┐
resumes ──────┼→ analyzed_documents → session_contexts ← interview_sessions

interview_sessions → interview_messages → message_voice_analyses
interview_sessions → session_feedbacks
```

### 테이블 인벤토리

| # | 테이블 | 역할 |
|---|--------|------|
| 1 | `users` | OAuth 사용자 (GitHub · Google) |
| 2 | `refresh_tokens` | JWT refresh token (해시 저장) |
| 3 | `user_consents` | 개인정보처리동의 이력 |
| 4 | `repositories` | 면접 분석용 GitHub 레포 메타 |
| 5 | `resumes` | 이력서 메타 — PDF(실 파일은 S3) + 웹 URL 자료(`file_type='WEB'`, `source_url`) |
| 5-1 | `cover_letters` | 자소서(공채) 문항별 텍스트 (`items` JSONB: `[{question,answer}]`). V20 |
| 6 | `analyzed_documents` | AI 분석 결과 메타 + S3 경로. 다형성 FK `resume_id`/`repository_id`/`cover_letter_id`(V20) 중 정확히 하나 |
| 7 | `interview_sessions` | 면접 세션 설정·상태·히스토리 |
| 7-1 | `session_job_categories` | 세션 직군 다중 선택 (대표 직군은 interview_sessions) |
| 8 | `session_contexts` | 세션 ↔ 분석 문서 N:M |
| 9 | `interview_messages` | 면접 질문·답변 시퀀스 (트리) |
| 10 | `message_voice_analyses` | 답변별 음성 분석 (1:1) |
| 11 | `session_feedbacks` | 종합 피드백 리포트 (1:1) |
| 12 | `activity_logs` | 사용자 행동 로그 |
| 13 | `ai_request_logs` | AI 서버 요청/응답 로그 |

---

## 2. DDL (V1__init.sql 베이스)

> 아래는 [필수 개선 사항](#5-필수-개선-사항)을 반영한 **권장 최종형** DDL이다. 초기 V1 마이그레이션은 이 형태로 작성한다.

```sql
-- 1. users  (V22 에서 Google 로그인 대응으로 확장됨)
CREATE TABLE users (
    id                              BIGSERIAL     PRIMARY KEY,
    provider                        VARCHAR(20)   NOT NULL DEFAULT 'GITHUB',  -- GITHUB | GOOGLE
    display_name                    VARCHAR(100)  NOT NULL,                   -- 화면 노출 이름
    github_id                       BIGINT,                                   -- GitHub 계정만
    github_username                 VARCHAR(100),                             -- GitHub 계정만
    encrypted_github_access_token   VARCHAR(1000),                            -- AES 암호화 (security.md)
    google_id                       VARCHAR(255),                             -- Google 계정만 (OIDC sub)
    email                           VARCHAR(255),
    avatar_url                      VARCHAR(500),
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    is_deleted                      BOOLEAN       NOT NULL DEFAULT FALSE,
    -- provider 별로 필요한 식별자가 반드시 있도록. 애플리케이션 버그가 반쪽짜리 계정을
    -- 만들어도 DB 에서 걸린다.
    CONSTRAINT ck_users_provider_identity CHECK (
        (provider = 'GITHUB' AND github_id IS NOT NULL AND encrypted_github_access_token IS NOT NULL)
        OR (provider = 'GOOGLE' AND google_id IS NOT NULL)
    )
);
CREATE INDEX idx_users_active ON users(id) WHERE is_deleted = FALSE;
-- 살아있는 계정끼리만 유일. NULL 은 서로 다른 값이라 해당 provider 가 아닌 행은 걸리지 않는다.
CREATE UNIQUE INDEX uq_users_github_id_active ON users (github_id) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX uq_users_google_id_active ON users (google_id) WHERE is_deleted = FALSE;

-- 2. refresh_tokens
CREATE TABLE refresh_tokens (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(500)  NOT NULL UNIQUE,
    device_info VARCHAR(500),
    expires_at  TIMESTAMPTZ   NOT NULL,
    is_revoked  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- 3. user_consents
CREATE TABLE user_consents (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    consent_type    VARCHAR(50)  NOT NULL CHECK (consent_type IN ('TOS', 'PRIVACY', 'MARKETING')),
    consent_version VARCHAR(20)  NOT NULL,
    is_agreed       BOOLEAN      NOT NULL DEFAULT TRUE,
    agreed_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
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
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','ANALYZING','ANALYZED','FAILED')),
    last_synced_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, github_repo_id)
);

-- 5. resumes  (PDF 업로드 + 웹 URL 자료를 함께 담는다 — V24)
CREATE TABLE resumes (
    id                BIGSERIAL      PRIMARY KEY,
    user_id           BIGINT         NOT NULL REFERENCES users(id),
    original_filename VARCHAR(500)   NOT NULL,           -- WEB 은 host+path 를 표시명으로
    file_path         VARCHAR(1000),                     -- S3 key only. WEB 은 NULL
    file_type         VARCHAR(20)    NOT NULL CHECK (file_type IN ('PDF','WEB')),
    file_size         BIGINT,                            -- WEB 은 NULL
    source_url        VARCHAR(2000),                     -- WEB 전용 원문 URL. PDF 는 NULL
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','ANALYZING','ANALYZED','FAILED')),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    -- 타입별 필수 locator 를 DB 에서 강제
    CONSTRAINT chk_resumes_locator_by_type CHECK (
        (file_type = 'PDF' AND file_path IS NOT NULL)
        OR (file_type = 'WEB' AND source_url IS NOT NULL)
    )
);

-- 6. analyzed_documents
-- ⚠️ 다형성 FK 제거: resume_id / repository_id 둘 중 하나만 NOT NULL
CREATE TABLE analyzed_documents (
    id             BIGSERIAL      PRIMARY KEY,
    resume_id      BIGINT         REFERENCES resumes(id),
    repository_id  BIGINT         REFERENCES repositories(id),
    document_path  VARCHAR(1000)  NOT NULL,           -- S3 key
    summary        VARCHAR(2000),
    tech_stack     JSONB,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    is_deleted     BOOLEAN        NOT NULL DEFAULT FALSE,
    CHECK (
        (resume_id IS NOT NULL AND repository_id IS NULL)
        OR
        (resume_id IS NULL AND repository_id IS NOT NULL)
    )
);

-- 7. interview_sessions
CREATE TABLE interview_sessions (
    id                    BIGSERIAL    PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users(id),
    title                 VARCHAR(200),
    memo                  TEXT,
    mode                  VARCHAR(20)  NOT NULL CHECK (mode IN ('TECHNICAL','PERSONALITY','INTEGRATED','JOB_TAILORED')),
    -- 대표 직군(다중 선택 시 첫 항목). 전체 직군은 session_job_categories 참조.
    job_category          VARCHAR(30)  NOT NULL
                          CHECK (job_category IN ('FRONTEND','BACKEND','INFRA','DBA')),
    max_questions         INT          NOT NULL DEFAULT 10,
    max_duration_minutes  INT          NOT NULL DEFAULT 60,
    -- 직무 맞춤(JOB_TAILORED) 모드 전용. 지원 회사명 + 채용공고(JD) 원문. 다른 모드는 NULL. (V18)
    target_company_name   VARCHAR(200),
    target_job_description TEXT,
    resumed_at             TIMESTAMPTZ,               -- 이어하기로 재개한 시각. 시간 한도를 이 값 기준으로 다시 잰다(V27)
    focus_areas            JSONB,                     -- 약점 집중 재도전의 겨냥 축 배열(TECHNICAL|LOGIC|COMMUNICATION). 일반 면접은 NULL
    -- 피드백 생성 실패 마커(V29). callback.feedback FAILED 수신 시 기록, 성공 콜백·재생성 요청 시 클리어.
    -- 새로고침한 클라이언트가 GET 피드백에서 "생성 중"(FEEDBACK_NOT_READY)과 "실패"(FEEDBACK_GENERATION_FAILED)를 구분하는 근거.
    feedback_failed_at     TIMESTAMPTZ,
    feedback_fail_retriable BOOLEAN,
    status                VARCHAR(20)  NOT NULL DEFAULT 'READY'
                          CHECK (status IN ('READY','IN_PROGRESS','INTERRUPTED','COMPLETED','CANCELLED')),
    total_question_count  INT          DEFAULT 0,
    started_at            TIMESTAMPTZ,
    ended_at              TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 7-1. session_job_categories (직군 다중 선택; 대표 직군은 interview_sessions.job_category)
CREATE TABLE session_job_categories (
    session_id   BIGINT      NOT NULL REFERENCES interview_sessions(id),
    job_category VARCHAR(30) NOT NULL
                 CHECK (job_category IN ('FRONTEND','BACKEND','INFRA','DBA')),
    UNIQUE (session_id, job_category)
);

-- 8. session_contexts
CREATE TABLE session_contexts (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT    NOT NULL REFERENCES interview_sessions(id),
    document_id BIGINT    NOT NULL REFERENCES analyzed_documents(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, document_id)
);

-- 9. interview_messages
CREATE TABLE interview_messages (
    id                BIGSERIAL      PRIMARY KEY,
    session_id        BIGINT         NOT NULL REFERENCES interview_sessions(id),
    sequence_number   INT            NOT NULL,
    role              VARCHAR(20)    NOT NULL
                      CHECK (role IN ('INTERVIEWER','INTERVIEWEE','SYSTEM')),
    content           TEXT,
    audio_file_path   VARCHAR(1000),
    parent_message_id BIGINT         REFERENCES interview_messages(id),
    status            VARCHAR(20)    NOT NULL DEFAULT 'CREATED'
                      CHECK (status IN ('CREATED','COMPLETED','FAILED')),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, sequence_number),
    CHECK (content IS NOT NULL OR audio_file_path IS NOT NULL)
);
-- interview_messages 는 이후 마이그레이션으로 컬럼 추가:
--   tts_* (V7), category/target_evidence/expected_signal (V9), answer_* 평가 4종 (V10),
--   clarification (V13), 그리고 질문별 복기 3종 (V19):
--     model_answer TEXT, answer_rewrite TEXT, coaching_comment TEXT  -- 답변(INTERVIEWEE)에만, 종료 세션 조회에서만 노출
--   bookmarked BOOLEAN NOT NULL DEFAULT FALSE (V26)  -- 오답노트. 질문(INTERVIEWER)에만 의미.
--     부분 인덱스 idx_interview_messages_bookmarked ON (session_id) WHERE bookmarked = TRUE

-- 10. message_voice_analyses
CREATE TABLE message_voice_analyses (
    id                     BIGSERIAL PRIMARY KEY,
    message_id             BIGINT    NOT NULL UNIQUE REFERENCES interview_messages(id),
    speaking_rate_wpm      FLOAT,
    silence_duration_sec   FLOAT,
    filler_word_counts     JSONB,
    pronunciation_accuracy FLOAT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    is_deleted           BOOLEAN        NOT NULL DEFAULT FALSE
);

-- 12. activity_logs (파티셔닝 권장 — §6 참조)
CREATE TABLE activity_logs (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       REFERENCES users(id),
    action        VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(30),
    resource_id   BIGINT,
    detail        JSONB,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 13. ai_request_logs (파티셔닝 권장)
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
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

---

## 3. ENUM 카탈로그

> 코드(Enum)와 DB(VARCHAR + CHECK)는 **반드시 1:1 매칭**. Enum 추가 시 Flyway 마이그레이션도 같이 작성.

```
mode             : TECHNICAL | PERSONALITY | INTEGRATED | JOB_TAILORED
job_category     : FRONTEND | BACKEND | INFRA | DBA
session_status   : READY | IN_PROGRESS | INTERRUPTED | COMPLETED | CANCELLED
message_role     : INTERVIEWER | INTERVIEWEE | SYSTEM
message_status   : CREATED | COMPLETED | FAILED
repo_status      : PENDING | ANALYZING | ANALYZED | FAILED
resume_status    : PENDING | ANALYZING | ANALYZED | FAILED
doc_status       : ACTIVE | ARCHIVED
consent_type     : TOS | PRIVACY | MARKETING
file_type        : PDF
```

---

## 4. 인덱스 전략

### 필수 인덱스
| 테이블 | 컬럼 | 이유 |
|--------|------|------|
| users | github_id (UNIQUE) | 로그인 lookup |
| refresh_tokens | token_hash (UNIQUE) | refresh 검증 |
| repositories | (user_id, github_repo_id) UNIQUE | 중복 등록 방지 |
| analyzed_documents | resume_id, repository_id | FK 조회 |
| interview_sessions | user_id, created_at DESC | 히스토리 목록 |
| interview_messages | (session_id, sequence_number) UNIQUE | 시퀀스 조회 |
| interview_messages | parent_message_id | 꼬리질문 트리 traverse |
| activity_logs | (user_id, created_at DESC) | 사용자별 로그 조회 |

### Partial Index (soft delete 패턴)
```sql
CREATE INDEX idx_repositories_active ON repositories(user_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_resumes_active     ON resumes(user_id)      WHERE is_deleted = FALSE;
CREATE INDEX idx_sessions_active    ON interview_sessions(user_id, created_at DESC) WHERE is_deleted = FALSE;
```

### pgvector 인덱스
```sql
-- 임베딩 테이블 (별도 마이그레이션에서 추가)
CREATE TABLE document_embeddings (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT NOT NULL REFERENCES analyzed_documents(id),
    chunk_index  INT NOT NULL,
    chunk_text   TEXT NOT NULL,
    embedding    VECTOR(1536) NOT NULL,  -- 모델별 차원수 확인
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_embeddings_ivfflat
    ON document_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

---

## 5. 필수 개선 사항 (Flyway V1 작성 전 적용)

| 항목 | 조치 | 이유 |
|------|------|------|
| TIMESTAMP → TIMESTAMPTZ | 위 DDL에 반영 | 타임존 이슈 방지 |
| CHECK 제약 추가 | 위 DDL에 반영 | 잘못된 값 차단 |
| `(session_id, sequence_number)` UNIQUE | 반영 | 시퀀스 중복 방지 |
| `analyzed_documents` 다형성 FK 해소 | `resume_id` / `repository_id` 분리 + CHECK | FK 무결성 확보 |
| `github_access_token` 암호화 | 컬럼명 변경 + AES 암호화 (애플리케이션 레벨) | 평문 저장 금지 |
| 빈 메시지 방지 | `CHECK (content IS NOT NULL OR audio_file_path IS NOT NULL)` | 무의미 row 차단 |

---

## 6. 후속 개선 (운영 단계)

### 로그 테이블 파티셔닝
```sql
-- activity_logs / ai_request_logs 를 월 단위 파티셔닝
CREATE TABLE activity_logs (...) PARTITION BY RANGE (created_at);
CREATE TABLE activity_logs_2026_05 PARTITION OF activity_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
```

### 보관 정책
- `activity_logs`: 90일 후 cold storage (S3 export)
- `ai_request_logs`: 30일 보관, 통계 요약만 별도 테이블에 보존

### S3 path 컨벤션
- bucket은 환경변수, key만 DB 저장 → [`storage.md`](./storage.md)

---

## 7. JPA / QueryDSL 사용 가이드

- 단순 CRUD → JPA Repository
- 동적 조건, 조인 다수 → QueryDSL
- Native Query는 `pgvector` 검색 등 ORM이 표현 어려운 경우만
- N+1 방지: `@EntityGraph` 또는 fetch join

```java
@Entity
@Table(name = "interview_sessions")
public class InterviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionMode mode;   // DB ENUM과 1:1
    // ...
}
```

---

## 8. Flyway 운영 룰

- 마이그레이션 파일명: `V{버전}__{snake_case_설명}.sql`
- 한 PR에 여러 마이그레이션 파일 OK, 단 dependency 순서 보장
- 적용 후 수정 절대 금지 (수정해야 한다면 `V{n+1}__fix_*.sql` 신규 추가)
- DDL과 DML(시드 데이터)은 분리: `V1__init_schema.sql`, `V2__seed_*.sql`

---

## 9. 미반영 도메인 (Phase 4 이후)

- 시선/자세 분석 결과 테이블 (Phase 4)
- 면접 노트 (질문별 사용자 메모 + LLM Enrich)
- 면접 대상 회사 관리 (`target_companies`)

추가 시 본 문서 §1, §2, §3에 반영.
