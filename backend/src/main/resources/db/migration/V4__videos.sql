-- Videos uploaded by creators to Cloudflare Stream.

CREATE TABLE videos (
    id                UUID          NOT NULL PRIMARY KEY,
    creator_id        UUID          NOT NULL REFERENCES creator_profiles (id) ON DELETE CASCADE,
    cloudflare_uid    VARCHAR(64)   NOT NULL,
    title             VARCHAR(100)  NOT NULL,
    description       VARCHAR(5000) NULL,
    access_type       VARCHAR(16)   NOT NULL,
    visibility        VARCHAR(16)   NOT NULL,
    processing_status VARCHAR(20)   NOT NULL,
    duration_seconds  INTEGER       NULL,
    error_reason      VARCHAR(100)  NULL,
    published_at      TIMESTAMPTZ   NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_videos_cloudflare_uid UNIQUE (cloudflare_uid),
    CONSTRAINT ck_videos_access_type CHECK (access_type IN ('FREE', 'EXCLUSIVE')),
    CONSTRAINT ck_videos_visibility CHECK (visibility IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_videos_processing_status CHECK (processing_status IN ('PENDING_UPLOAD', 'PROCESSING', 'READY', 'ERROR'))
);

CREATE INDEX idx_videos_creator_created ON videos (creator_id, created_at DESC);
