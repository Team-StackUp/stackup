CREATE TABLE oauth_states (
    id BIGSERIAL PRIMARY KEY,
    state VARCHAR(128) NOT NULL UNIQUE,
    code_verifier VARCHAR(128) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_oauth_states_provider CHECK (provider IN ('GITHUB'))
);

CREATE INDEX idx_oauth_states_state ON oauth_states (state);
CREATE INDEX idx_oauth_states_expires_at ON oauth_states (expires_at);
