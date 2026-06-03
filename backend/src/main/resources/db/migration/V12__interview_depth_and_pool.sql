-- 면접 깊이 제어(n/m/k) + 질문 풀.
-- n = 일반질문 수, m = 질문당 최대 꼬리질문 수, k(max_questions) = 총 상한(기존).
ALTER TABLE interview_sessions
    ADD COLUMN general_question_count INT NOT NULL DEFAULT 3,
    ADD COLUMN max_followups_per_question INT NOT NULL DEFAULT 2;

-- AI 가 생성한 일반질문 풀. POOL 콜백에서 n개 저장 후, 진행하며 하나씩 꺼내 일반질문으로 삽입.
CREATE TABLE session_question_pool (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES interview_sessions (id),
    idx INT NOT NULL,
    question TEXT NOT NULL,
    category VARCHAR(40),
    target_evidence TEXT,
    expected_signal TEXT,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pool_session_idx UNIQUE (session_id, idx)
);

CREATE INDEX idx_pool_session_unused ON session_question_pool (session_id, idx) WHERE used = FALSE;
