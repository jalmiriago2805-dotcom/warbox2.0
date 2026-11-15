package com.warbox.creator;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {

    Optional<CreatorProfile> findByUserId(UUID userId);

    Optional<CreatorProfile> findByHandle(String handle);

    boolean existsByUserId(UUID userId);

    boolean existsByHandle(String handle);
}
