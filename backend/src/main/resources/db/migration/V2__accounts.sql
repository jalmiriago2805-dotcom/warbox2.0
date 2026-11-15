-- Accounts: users and single-use e-mail tokens (verification and password reset).

CREATE TABLE users (
    id                UUID         NOT NULL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    birth_date        DATE         NOT NULL,
    terms_accepted_at TIMESTAMPTZ  NOT NULL,
    email_verified_at TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE account_tokens (
    id          UUID        NOT NULL PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(32) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_account_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX idx_account_tokens_user_purpose_created
    ON account_tokens (user_id, purpose, created_at);
