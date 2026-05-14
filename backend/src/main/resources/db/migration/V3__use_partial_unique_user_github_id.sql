ALTER TABLE users DROP CONSTRAINT IF EXISTS users_github_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_github_id_active
    ON users (github_id)
    WHERE is_deleted = FALSE;
