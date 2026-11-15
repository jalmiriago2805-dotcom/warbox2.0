package com.warbox.video;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Direct-to-database helpers to arrange video rows without going through Cloudflare. */
final class VideoTestSupport {

    private VideoTestSupport() {}

    static UUID creatorId(JdbcTemplate jdbc, String handle) {
        return jdbc.queryForObject("SELECT id FROM creator_profiles WHERE handle = ?", UUID.class, handle);
    }

    static UUID insertVideo(JdbcTemplate jdbc, UUID creatorId, String uid, String title, String accessType,
            String visibility, String status, Instant publishedAt, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO videos (id, creator_id, cloudflare_uid, title, access_type, visibility, processing_status,
                                    duration_seconds, published_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 90, ?, ?, ?)
                """, id, creatorId, uid, title, accessType, visibility, status,
                publishedAt == null ? null : Timestamp.from(publishedAt), Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return id;
    }
}
