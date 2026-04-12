ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS email text;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS password_hash text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_email
    ON app_user(email);

CREATE TABLE IF NOT EXISTS user_session (
                                            id uuid PRIMARY KEY,
                                            user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    session_token text NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_session_token
    ON user_session(session_token);

CREATE INDEX IF NOT EXISTS idx_user_session_user
    ON user_session(user_id);