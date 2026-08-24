package com.resumeiq.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Accounts.
 *
 * <p>The only repository in the project whose finders are not scoped to a user id — this is
 * where a caller becomes a user in the first place.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Expects an already-normalised address. Prefer {@link #findByEmailNormalized(String)} at
     * call sites that handle raw input.
     */
    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(UUID publicId);

    boolean existsByEmail(String email);

    /**
     * Login and registration both take whatever the person typed, so normalisation belongs
     * here rather than being remembered at each call site. It applies exactly the same rule
     * the entity applies on write, which is the property that makes the lookup reliable.
     */
    default Optional<User> findByEmailNormalized(String rawEmail) {
        return findByEmail(User.normalizeEmail(rawEmail));
    }

    /** Registration guard. Same normalisation, same reason. */
    default boolean existsByEmailNormalized(String rawEmail) {
        return existsByEmail(User.normalizeEmail(rawEmail));
    }
}
