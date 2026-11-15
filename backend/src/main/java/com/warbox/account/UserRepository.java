package com.warbox.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** @param normalizedEmail output of {@link AccountService#normalizeEmail(String)} */
    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);
}
