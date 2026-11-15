package com.warbox.account;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    /** Row-locks the token ({@code SELECT ... FOR UPDATE}) so that concurrent consumers serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /** Marks every still-pending token of the user for that purpose as consumed. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update AccountToken t set t.consumedAt = :now
            where t.user.id = :userId and t.purpose = :purpose and t.consumedAt is null
            """)
    int invalidatePending(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose,
            @Param("now") Instant now);

    @Query("select max(t.createdAt) from AccountToken t where t.user.id = :userId and t.purpose = :purpose")
    Optional<Instant> findLatestCreatedAt(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose);
}
