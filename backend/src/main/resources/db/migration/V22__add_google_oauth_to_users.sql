-- Google 로그인 도입.
--
-- 기존 users 는 GitHub 전용이라 github_id / github_username / encrypted_github_access_token
-- 이 모두 NOT NULL 이었다. Google 계정은 이 셋을 가질 수 없으므로 nullable 로 바꾸고,
-- 어떤 provider 로 만들어진 계정인지를 provider 컬럼으로 명시한다.
--
-- provider 별 식별자를 한 컬럼(provider_user_id)으로 합치지 않은 이유 — 기존 행의 identity
-- 를 옮기는 백필은 살아있는 계정을 건드리는 작업이고, GitHub 식별자는 레포 연동에서 계속
-- 쓰인다. 컬럼을 하나 더 두는 편이 위험이 낮다. (provider 3개째가 필요해지면 그때 통합)

ALTER TABLE users
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'GITHUB',
    ADD COLUMN google_id VARCHAR(255),
    ADD COLUMN display_name VARCHAR(100);

-- 화면에 노출되는 이름. 지금까지는 github_username 이 그 역할을 했다.
UPDATE users SET display_name = github_username WHERE display_name IS NULL;

ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL,
    ALTER COLUMN github_id DROP NOT NULL,
    ALTER COLUMN github_username DROP NOT NULL,
    ALTER COLUMN encrypted_github_access_token DROP NOT NULL;

-- github_id 와 같은 규약: 살아있는 계정끼리만 유일. Postgres 는 NULL 을 서로 다른 값으로
-- 취급하므로 google_id 가 없는 GitHub 계정들은 이 인덱스에 걸리지 않는다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_id_active
    ON users (google_id)
    WHERE is_deleted = FALSE;

ALTER TABLE users
    ADD CONSTRAINT ck_users_provider_identity CHECK (
        (provider = 'GITHUB' AND github_id IS NOT NULL AND encrypted_github_access_token IS NOT NULL)
        OR (provider = 'GOOGLE' AND google_id IS NOT NULL)
    );
