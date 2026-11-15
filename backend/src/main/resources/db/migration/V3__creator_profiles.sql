-- Creator profiles: one per user, with a unique lowercase URL handle.

CREATE TABLE creator_profiles (
    id           UUID         NOT NULL PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    handle       VARCHAR(30)  NOT NULL,
    display_name VARCHAR(60)  NOT NULL,
    bio          VARCHAR(500) NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_creator_profiles_user_id UNIQUE (user_id),
    CONSTRAINT uk_creator_profiles_handle UNIQUE (handle),
    CONSTRAINT ck_creator_profiles_handle_lower CHECK (handle = lower(handle))
);
