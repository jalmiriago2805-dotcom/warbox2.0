package com.warbox.video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "cloudflare_uid", nullable = false, length = 64, unique = true)
    private String cloudflareUid;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 16)
    private VideoAccessType accessType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VideoVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private VideoProcessingStatus processingStatus;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "error_reason", length = 100)
    private String errorReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Video() {}

    public Video(UUID creatorId, String cloudflareUid, String title, String description,
            VideoAccessType accessType, Instant now) {
        this.creatorId = creatorId;
        this.cloudflareUid = cloudflareUid;
        this.title = title;
        this.description = description;
        this.accessType = accessType;
        this.visibility = VideoVisibility.DRAFT;
        this.processingStatus = VideoProcessingStatus.PENDING_UPLOAD;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateMetadata(String title, String description, VideoAccessType accessType, Instant now) {
        this.title = title;
        this.description = description;
        this.accessType = accessType;
        this.updatedAt = now;
    }

    public void publish(Instant now) {
        if (visibility == VideoVisibility.PUBLISHED) {
            return;
        }
        this.visibility = VideoVisibility.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void unpublish(Instant now) {
        if (visibility == VideoVisibility.DRAFT) {
            return;
        }
        this.visibility = VideoVisibility.DRAFT;
        this.publishedAt = null;
        this.updatedAt = now;
    }

    public void applyStatus(VideoProcessingStatus status, Integer durationSeconds, String errorReason, Instant now) {
        this.processingStatus = status;
        if (durationSeconds != null) {
            this.durationSeconds = durationSeconds;
        }
        this.errorReason = status == VideoProcessingStatus.ERROR ? errorReason : null;
        this.updatedAt = now;
    }

    public boolean isFinal() {
        return processingStatus.isFinal();
    }

    public UUID getId() { return id; }

    public UUID getCreatorId() { return creatorId; }

    public String getCloudflareUid() { return cloudflareUid; }

    public String getTitle() { return title; }

    public String getDescription() { return description; }

    public VideoAccessType getAccessType() { return accessType; }

    public VideoVisibility getVisibility() { return visibility; }

    public VideoProcessingStatus getProcessingStatus() { return processingStatus; }

    public Integer getDurationSeconds() { return durationSeconds; }

    public String getErrorReason() { return errorReason; }

    public Instant getPublishedAt() { return publishedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
