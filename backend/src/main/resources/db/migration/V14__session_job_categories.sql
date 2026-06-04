-- 직군 다중 선택: 한 세션이 여러 직군 질문을 함께 다룰 수 있다.
-- 기존 interview_sessions.job_category 는 "대표 직군"(첫 선택)으로 유지(하위호환).
CREATE TABLE session_job_categories (
    session_id BIGINT NOT NULL REFERENCES interview_sessions (id),
    job_category VARCHAR(30) NOT NULL,
    CONSTRAINT uq_session_job_category UNIQUE (session_id, job_category),
    CONSTRAINT chk_session_job_category
        CHECK (job_category IN ('FRONTEND', 'BACKEND', 'INFRA', 'DBA'))
);

CREATE INDEX idx_session_job_categories_session ON session_job_categories (session_id);

-- 기존 세션의 대표 직군을 다중 테이블에 백필.
INSERT INTO session_job_categories (session_id, job_category)
SELECT id, job_category FROM interview_sessions;
