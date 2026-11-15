package com.warbox.video;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    List<Video> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    Optional<Video> findByIdAndCreatorId(UUID id, UUID creatorId);

    Optional<Video> findByCloudflareUid(String cloudflareUid);

    List<Video> findByCreatorIdAndVisibilityAndProcessingStatusOrderByPublishedAtDesc(
            UUID creatorId, VideoVisibility visibility, VideoProcessingStatus processingStatus);
}
