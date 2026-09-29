-- 직군 확장: 개발 4종 → 개발 8종 + 비개발 12종.
--
-- 서비스는 "IT 직군"이 아니라 취준생 전반을 대상으로 하는데 직군 선택지에 개발 직무만
-- 있었다. 기존 4개는 이름을 그대로 두므로 이미 쌓인 세션·통계는 영향이 없다.
--
-- CHECK 제약이 두 곳에 있다(V1 의 interview_sessions.job_category, V14 의
-- session_job_categories.job_category). 한 곳만 고치면 다른 쪽에서 INSERT 가 터진다.

-- 제약 이름은 운영 DB 에서 직접 확인했다(pg_constraint) — 추측한 이름으로 DROP 하면
-- IF EXISTS 가 조용히 넘어가고 옛 제약이 남아 새 값 INSERT 가 터진다.
ALTER TABLE interview_sessions DROP CONSTRAINT IF EXISTS chk_interview_sessions_job_category;
ALTER TABLE interview_sessions
    ADD CONSTRAINT chk_interview_sessions_job_category
    CHECK (job_category IN (
        'FRONTEND', 'BACKEND', 'INFRA', 'DBA', 'MOBILE', 'DATA_AI', 'SECURITY', 'QA',
        'PLANNING', 'MARKETING', 'SALES', 'HR', 'FINANCE', 'DESIGN',
        'MANUFACTURING', 'RND', 'CUSTOMER_SERVICE', 'LOGISTICS', 'LEGAL', 'PUBLIC'
    ));

ALTER TABLE session_job_categories DROP CONSTRAINT IF EXISTS chk_session_job_category;
ALTER TABLE session_job_categories
    ADD CONSTRAINT chk_session_job_category
    CHECK (job_category IN (
        'FRONTEND', 'BACKEND', 'INFRA', 'DBA', 'MOBILE', 'DATA_AI', 'SECURITY', 'QA',
        'PLANNING', 'MARKETING', 'SALES', 'HR', 'FINANCE', 'DESIGN',
        'MANUFACTURING', 'RND', 'CUSTOMER_SERVICE', 'LOGISTICS', 'LEGAL', 'PUBLIC'
    ));
