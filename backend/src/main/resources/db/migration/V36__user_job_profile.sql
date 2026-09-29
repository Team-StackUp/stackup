-- 사용자 취업 프로필: 희망 직군(다중) · 희망 산업(자유 입력) · 경력 수준.
--
-- 왜 필요한가
--  1) 면접을 만들 때마다 직군을 다시 고르게 했다. 대부분 사용자는 매번 같은 값을 넣는다.
--  2) 직군만으로는 질문 맥락이 얇다. 같은 '생산·품질' 이라도 반도체 공정과 건설 현장은
--     묻는 것이 전혀 다르다. 산업을 알면 질문이 그 현장의 언어로 나온다.
--
-- 산업은 **CHECK 를 걸지 않는다.** 직군은 평가 관점을 큐레이션해야 해서 유한 집합이지만,
-- 산업은 프롬프트 맥락으로만 쓰이므로 열거하면 빠진 산업의 지원자가 또 배제된다
-- (반도체·건설·토목을 넣어도 조선·방산·바이오… 끝이 없다). 자유 입력 + 제안 목록으로 둔다.
--
-- users 테이블에 컬럼을 붙이지 않고 별도 테이블로 둔다: 프로필은 선택 기능이라 대부분 NULL 이
-- 되고, 무엇보다 도메인 슬라이스를 분리해야 한다 — User 가 JobCategory(session 슬라이스)를
-- 참조하면 user→session→user 순환이 생겨 ArchUnit 이 막는다(OAuthProvider 를 user.domain 에
-- 둔 것과 같은 이유). profile 슬라이스가 user·session 을 의존하고 역방향이 없어 순환이 없다.

CREATE TABLE user_job_profiles (
    user_id          BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    desired_industry VARCHAR(100),
    career_level     VARCHAR(20),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_job_profiles_career_level
        CHECK (career_level IS NULL
               OR career_level IN ('NEW', 'EXPERIENCED', 'INTERN', 'CAREER_CHANGE'))
);

CREATE TABLE user_job_profile_categories (
    user_id      BIGINT      NOT NULL
                 REFERENCES user_job_profiles(user_id) ON DELETE CASCADE,
    job_category VARCHAR(30) NOT NULL,
    CONSTRAINT uq_user_job_profile_category UNIQUE (user_id, job_category),
    CONSTRAINT chk_user_job_profile_category
        CHECK (job_category IN (
            'FRONTEND', 'BACKEND', 'INFRA', 'DBA', 'MOBILE', 'DATA_AI', 'SECURITY', 'QA',
            'PLANNING', 'MARKETING', 'SALES', 'HR', 'FINANCE', 'DESIGN',
            'MANUFACTURING', 'RND', 'CUSTOMER_SERVICE', 'LOGISTICS', 'LEGAL', 'PUBLIC'
        ))
);

-- 세션에도 그때 쓴 산업을 남긴다. 프로필은 나중에 바뀔 수 있으므로 "이 면접이 어떤 맥락에서
-- 진행됐는지" 는 세션이 스스로 기록해야 한다(직군을 세션에 복사해 두는 것과 같은 이유).
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS industry VARCHAR(100);
