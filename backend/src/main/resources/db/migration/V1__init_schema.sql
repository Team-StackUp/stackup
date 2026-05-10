CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    github_id BIGINT NOT NULL UNIQUE,
    github_username VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    avatar_url VARCHAR(500),
    encrypted_github_access_token VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_active ON users (id) WHERE is_deleted = FALSE;

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    token_hash VARCHAR(500) NOT NULL UNIQUE,
    device_info VARCHAR(500),
    expires_at TIMESTAMPTZ NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE user_consents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    consent_type VARCHAR(50) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    is_agreed BOOLEAN NOT NULL DEFAULT TRUE,
    agreed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_consents_consent_type
        CHECK (consent_type IN ('TOS', 'PRIVACY', 'MARKETING'))
);

CREATE INDEX idx_user_consents_user_id ON user_consents (user_id);
CREATE INDEX idx_user_consents_user_type ON user_consents (user_id, consent_type);

CREATE TABLE repositories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    github_repo_id BIGINT NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    repo_full_name VARCHAR(500) NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    default_branch VARCHAR(100) DEFAULT 'main',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_repositories_user_github_repo UNIQUE (user_id, github_repo_id),
    CONSTRAINT chk_repositories_status
        CHECK (status IN ('PENDING', 'ANALYZING', 'ANALYZED', 'FAILED'))
);

CREATE INDEX idx_repositories_active ON repositories (user_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_repositories_status ON repositories (status);

CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    original_filename VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_resumes_file_type CHECK (file_type IN ('PDF')),
    CONSTRAINT chk_resumes_status
        CHECK (status IN ('PENDING', 'ANALYZING', 'ANALYZED', 'FAILED'))
);

CREATE INDEX idx_resumes_active ON resumes (user_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_resumes_status ON resumes (status);

CREATE TABLE analyzed_documents (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT REFERENCES resumes (id),
    repository_id BIGINT REFERENCES repositories (id),
    document_path VARCHAR(1000) NOT NULL,
    summary VARCHAR(2000),
    tech_stack JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_analyzed_documents_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_analyzed_documents_single_source CHECK (
        (resume_id IS NOT NULL AND repository_id IS NULL)
        OR
        (resume_id IS NULL AND repository_id IS NOT NULL)
    )
);

CREATE INDEX idx_analyzed_documents_resume_id ON analyzed_documents (resume_id);
CREATE INDEX idx_analyzed_documents_repository_id ON analyzed_documents (repository_id);
CREATE INDEX idx_analyzed_documents_active ON analyzed_documents (id) WHERE is_deleted = FALSE;

CREATE TABLE interview_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    title VARCHAR(200),
    memo TEXT,
    mode VARCHAR(20) NOT NULL,
    interview_type VARCHAR(30) NOT NULL,
    job_category VARCHAR(30) NOT NULL,
    max_questions INT NOT NULL DEFAULT 10,
    max_duration_minutes INT NOT NULL DEFAULT 60,
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    total_question_count INT DEFAULT 0,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_interview_sessions_mode CHECK (mode IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT chk_interview_sessions_interview_type
        CHECK (interview_type IN ('PERSONALITY', 'TECHNICAL', 'LIVE_CODING', 'INTEGRATED')),
    CONSTRAINT chk_interview_sessions_job_category
        CHECK (job_category IN ('FRONTEND', 'BACKEND', 'INFRA', 'DBA')),
    CONSTRAINT chk_interview_sessions_status
        CHECK (status IN ('READY', 'IN_PROGRESS', 'INTERRUPTED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_interview_sessions_active
    ON interview_sessions (user_id, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_interview_sessions_user_status ON interview_sessions (user_id, status);

CREATE TABLE session_contexts (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES interview_sessions (id),
    document_id BIGINT NOT NULL REFERENCES analyzed_documents (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_session_contexts_session_document UNIQUE (session_id, document_id)
);

CREATE INDEX idx_session_contexts_document_id ON session_contexts (document_id);

CREATE TABLE interview_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES interview_sessions (id),
    sequence_number INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    audio_file_path VARCHAR(1000),
    parent_message_id BIGINT REFERENCES interview_messages (id),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_interview_messages_session_sequence UNIQUE (session_id, sequence_number),
    CONSTRAINT chk_interview_messages_role
        CHECK (role IN ('INTERVIEWER', 'INTERVIEWEE', 'SYSTEM')),
    CONSTRAINT chk_interview_messages_status
        CHECK (status IN ('CREATED', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_interview_messages_content_or_audio
        CHECK (content IS NOT NULL OR audio_file_path IS NOT NULL)
);

CREATE INDEX idx_interview_messages_parent_message_id ON interview_messages (parent_message_id);

CREATE TABLE message_voice_analyses (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL UNIQUE REFERENCES interview_messages (id),
    speaking_rate_wpm DOUBLE PRECISION,
    silence_duration_sec DOUBLE PRECISION,
    filler_word_counts JSONB,
    pronunciation_accuracy DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE session_feedbacks (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL UNIQUE REFERENCES interview_sessions (id),
    overall_score DOUBLE PRECISION,
    technical_accuracy DOUBLE PRECISION,
    logic_score DOUBLE PRECISION,
    communication_score DOUBLE PRECISION,
    strengths_summary TEXT,
    weaknesses_summary TEXT,
    improvement_keywords JSONB,
    report_file_path VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE activity_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users (id),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(30),
    resource_id BIGINT,
    detail JSONB,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_logs_user_created_at ON activity_logs (user_id, created_at DESC);
CREATE INDEX idx_activity_logs_action_created_at ON activity_logs (action, created_at DESC);
CREATE INDEX idx_activity_logs_created_at ON activity_logs (created_at DESC);

CREATE TABLE ai_request_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users (id),
    session_id BIGINT REFERENCES interview_sessions (id),
    request_type VARCHAR(50) NOT NULL,
    model_name VARCHAR(100),
    input_tokens INT,
    output_tokens INT,
    latency_ms INT,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_request_logs_status CHECK (status IN ('SUCCESS', 'FAILED', 'TIMEOUT'))
);

CREATE INDEX idx_ai_request_logs_user_created_at ON ai_request_logs (user_id, created_at DESC);
CREATE INDEX idx_ai_request_logs_session_id ON ai_request_logs (session_id);
CREATE INDEX idx_ai_request_logs_request_type_created_at ON ai_request_logs (request_type, created_at DESC);
CREATE INDEX idx_ai_request_logs_created_at ON ai_request_logs (created_at DESC);
