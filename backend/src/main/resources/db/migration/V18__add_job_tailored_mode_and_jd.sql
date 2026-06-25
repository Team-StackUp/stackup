-- 직무 맞춤 면접 모드(JOB_TAILORED) + 타깃 회사/채용공고(JD) 컨텍스트.
-- 이 모드에서만 회사명·JD 를 받아 적합도·지원동기 질문과 '직무 적합도' 피드백을 생성한다.

ALTER TABLE interview_sessions
    DROP CONSTRAINT IF EXISTS chk_interview_sessions_mode;

ALTER TABLE interview_sessions
    ADD CONSTRAINT chk_interview_sessions_mode
    CHECK (mode IN ('TECHNICAL', 'PERSONALITY', 'INTEGRATED', 'JOB_TAILORED'));

ALTER TABLE interview_sessions
    ADD COLUMN target_company_name   VARCHAR(200),
    ADD COLUMN target_job_description TEXT;
