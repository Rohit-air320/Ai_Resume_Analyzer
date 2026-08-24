package com.resumeiq.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens, looked up by hash.
 *
 * <p>There is deliberately no finder that takes a raw token. The service hashes first and
 * queries by the digest, so a plaintext token never reaches a SQL statement and never appears
 * in a slow-query log or a bind-parameter trace.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * The one read on the refresh path. Hits the unique index on {@code token_hash}, and
     * returns the row whether it is live, spent or expired — the service needs to tell those
     * three apart, and a finder that filtered to live rows only would make a replayed token
     * indistinguishable from a token that was never issued.
     *
     * <p>The owner is fetched in the same statement. Without that, {@code getUser()} on the
     * returned row is a lazy proxy, and with {@code open-in-view: false} the proxy would be
     * dead the moment the service's transaction ended — the refresh handler needs the account
     * to sign the next access token, so it must arrive loaded rather than promised.
     */
    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Ends every token descended from one login, in a single statement.
     *
     * <p>A bulk update rather than a load-and-mutate loop: the row count is the answer the
     * caller wants, and a family that has grown over a week of rotations should not be pulled
     * into memory to be marked dead. The {@code revoked_at IS NULL} guard is what preserves
     * the original reason on rows that were already spent, so a token revoked by {@code ROTATED}
     * still reads as rotated after the family is torn down.
     *
     * <p>{@code clearAutomatically} and {@code flushAutomatically} matter because this runs
     * inside the refresh transaction: without them, a stale copy of a revoked token could still
     * be sitting in the persistence context, and pending changes could be written after the
     * update rather than before it.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :moment,
                   t.revokedReason = :reason
             where t.familyId = :familyId
               and t.revokedAt is null
            """)
    int revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("reason") RevocationReason reason,
            @Param("moment") Instant moment);

    /**
     * Ends every live token a user holds, on every device.
     *
     * <p>Used when an account is deleted or a password changes. Scoped by the internal id
     * because the caller already has the authenticated user in hand.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :moment,
                   t.revokedReason = :reason
             where t.user.id = :userId
               and t.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("userId") Long userId,
            @Param("reason") RevocationReason reason,
            @Param("moment") Instant moment);

    /**
     * Removes rows whose expiry has passed.
     *
     * <p>Safe to delete rather than keep: an expired token fails the expiry check before reuse
     * detection is ever consulted, so its history has no further value. Rows revoked but not
     * yet expired stay, because those are the ones reuse detection reads.
     *
     * <p>{@code @Transactional} for the same reason as elsewhere in this project: Spring Data's
     * generated implementation is read-only, and a derived delete in a read-only transaction
     * reports a row count and writes nothing.
     */
    @Transactional
    int deleteByExpiresAtBefore(Instant moment);

    /** How many sessions a user currently has open. Backs the session cap on login. */
    int countByUserIdAndRevokedAtIsNull(Long userId);

    /**
     * The user's live tokens, oldest first — the order in which the session cap evicts them.
     */
    List<RefreshToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(Long userId);
}
