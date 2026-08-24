package com.resumeiq.auth;

import com.resumeiq.common.domain.BaseEntity;
import com.resumeiq.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token, stored as a hash.
 *
 * <p>This table is what makes a long-lived session revocable. An access token cannot be
 * withdrawn once signed — that is the trade a stateless token makes — so the long-lived half
 * of the pair is a database row instead, and ending a session is an update rather than a hope.
 *
 * <p>Three decisions are worth defending:
 *
 * <p><strong>Only the hash is stored.</strong> The column holds SHA-256 of the token, so a
 * dump of this table cannot be replayed against the API. BCrypt is not used here and that is
 * not an oversight: these tokens are 256 bits of output from a CSPRNG, not human-chosen
 * secrets, so there is no dictionary to slow down — and a per-request BCrypt verification
 * would need to scan candidate rows rather than look one up by key.
 *
 * <p><strong>Rotation, tracked by family.</strong> Every exchange issues a new token and
 * revokes the one presented, all sharing a {@code family_id} that traces back to the original
 * login. If a spent token is presented again, the whole family is revoked at once — the thief
 * and the victim both lose the session, which is the outcome that protects the account.
 *
 * <p><strong>Rows are kept after revocation, not deleted.</strong> Reuse detection needs to
 * recognise a token it has already seen; a deleted row is indistinguishable from a token that
 * never existed. Expired rows are swept separately.
 */
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_refresh_tokens_family", columnList = "family_id"),
                @Index(name = "idx_refresh_tokens_user", columnList = "user_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_user"))
    private User user;

    /** SHA-256 of the token, hex encoded. Fixed at 64 characters, hence the exact length. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /**
     * Groups every token descended from one login.
     *
     * <p>Stored as {@code char(36)} to match {@code public_id} elsewhere in this schema: a
     * readable id that can be pasted from a log line into a {@code WHERE} clause is worth
     * more than the sixteen bytes a binary form would save.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "family_id", nullable = false, updatable = false, length = 36)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null while the token is live. Set once, never cleared. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 20)
    private RevocationReason revokedReason;

    /** A new token in a new family — the shape of a fresh login. */
    public static RefreshToken startFamily(User user, String tokenHash, Instant expiresAt) {
        return issue(user, tokenHash, UUID.randomUUID(), expiresAt);
    }

    /** A replacement token that stays in the family of the one it succeeds. */
    public static RefreshToken issue(User user, String tokenHash, UUID familyId, Instant expiresAt) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant moment) {
        return !expiresAt.isAfter(moment);
    }

    /** Usable exactly once, and only while live. */
    public boolean isUsableAt(Instant moment) {
        return !isRevoked() && !isExpiredAt(moment);
    }

    /**
     * Records that this token is finished.
     *
     * <p>The first reason wins. A token revoked by rotation and then presented again must
     * keep saying {@code ROTATED}, because that is what tells the reuse check — and anyone
     * reading the table afterwards — that it had already been spent.
     */
    public void revoke(RevocationReason reason, Instant moment) {
        if (revokedAt == null) {
            this.revokedAt = moment;
            this.revokedReason = reason;
        }
    }
}
