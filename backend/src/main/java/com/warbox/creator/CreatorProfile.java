package com.warbox.creator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_profiles")
public class CreatorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 30, unique = true)
    private String handle;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(length = 500)
    private String bio;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreatorProfile() {}

    public CreatorProfile(UUID userId, String handle, String displayName, String bio, Instant now) {
        this.userId = userId;
        this.handle = handle;
        this.displayName = displayName;
        this.bio = bio;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String handle, String displayName, String bio, Instant now) {
        this.handle = handle;
        this.displayName = displayName;
        this.bio = bio;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }

    public String getHandle() { return handle; }

    public String getDisplayName() { return displayName; }

    public String getBio() { return bio; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
