package com.resumeiq.auth;

import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestFixtures;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The refresh-token table, and the queries that make a session revocable.
 *
 * <p>These are the assertions that would fail if somebody "simplified" the design later: that a
 * revoked row survives so reuse can be recognised, that a family revocation does not overwrite
 * the reason a row was already spent for, and that the unique index on the hash is real. Each one
 * corresponds to a way the rotation scheme quietly stops working.
 */
@RepositoryTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private TestEntityManager em;

    private User owner;

    @BeforeEach
    void createOwner() {
        owner = users.saveAndFlush(TestFixtures.user("sessions@example.com"));
    }

    @Test
    @DisplayName("a token is found by its hash, with its owner already loaded")
    void findsByHashWithOwnerLoaded() {
        RefreshToken saved = refreshTokens.saveAndFlush(
                RefreshToken.startFamily(owner, digest("first"), inDays(7)));
        em.clear();

        RefreshToken found = refreshTokens.findByTokenHash(saved.getTokenHash()).orElseThrow();

        // The entity graph on the finder is what makes this safe outside a transaction; without
        // it this line would be a lazy-loading failure in production, not in this test.
        assertThat(found.getUser().getEmail()).isEqualTo("sessions@example.com");
        assertThat(found.getFamilyId()).isEqualTo(saved.getFamilyId());
        assertThat(found.isUsableAt(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("the same hash cannot be stored twice")
    void enforcesUniqueHash() {
        String hash = digest("collision");
        refreshTokens.saveAndFlush(RefreshToken.startFamily(owner, hash, inDays(7)));
        em.clear();

        // Two live rows with one hash would make reuse detection ambiguous: the finder would
        // return whichever the database felt like, and a spent token could resolve to a live row.
        assertThatThrownBy(() -> refreshTokens.saveAndFlush(
                RefreshToken.startFamily(owner, hash, inDays(7))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a revoked token is kept, so a replay can still be recognised")
    void keepsRevokedRows() {
        RefreshToken saved = refreshTokens.saveAndFlush(
                RefreshToken.startFamily(owner, digest("spent"), inDays(7)));
        saved.revoke(RevocationReason.ROTATED, Instant.now());
        refreshTokens.flush();
        em.clear();

        RefreshToken found = refreshTokens.findByTokenHash(digest("spent")).orElseThrow();

        assertThat(found.isRevoked()).isTrue();
        assertThat(found.getRevokedReason()).isEqualTo(RevocationReason.ROTATED);
        assertThat(found.isUsableAt(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("revoking a family ends its live tokens and leaves earlier reasons intact")
    void revokesWholeFamilyWithoutRewritingHistory() {
        UUID family = UUID.randomUUID();
        RefreshToken spent = refreshTokens.save(
                RefreshToken.issue(owner, digest("gen1"), family, inDays(7)));
        spent.revoke(RevocationReason.ROTATED, Instant.now());
        refreshTokens.save(RefreshToken.issue(owner, digest("gen2"), family, inDays(7)));

        // A second family, to prove the update is scoped.
        RefreshToken untouched = refreshTokens.save(
                RefreshToken.startFamily(owner, digest("other-family"), inDays(7)));
        refreshTokens.flush();

        int revoked = refreshTokens.revokeFamily(
                family, RevocationReason.REUSE_DETECTED, Instant.now());

        assertThat(revoked).isOne();
        assertThat(refreshTokens.findByTokenHash(digest("gen2")).orElseThrow().getRevokedReason())
                .isEqualTo(RevocationReason.REUSE_DETECTED);
        // Still ROTATED: the reason a token was spent is what tells reuse detection it was spent,
        // so an overwrite here would erase the evidence the check depends on.
        assertThat(refreshTokens.findByTokenHash(digest("gen1")).orElseThrow().getRevokedReason())
                .isEqualTo(RevocationReason.ROTATED);
        assertThat(refreshTokens.findByTokenHash(untouched.getTokenHash()).orElseThrow().isRevoked())
                .isFalse();
    }

    @Test
    @DisplayName("revoking a user ends every live session they hold and nobody else's")
    void revokesEveryLiveSessionForOneUser() {
        User stranger = users.saveAndFlush(TestFixtures.user("stranger@example.com"));
        refreshTokens.save(RefreshToken.startFamily(owner, digest("phone"), inDays(7)));
        refreshTokens.save(RefreshToken.startFamily(owner, digest("laptop"), inDays(7)));
        refreshTokens.save(RefreshToken.startFamily(stranger, digest("theirs"), inDays(7)));
        refreshTokens.flush();

        int revoked = refreshTokens.revokeAllForUser(
                owner.getId(), RevocationReason.SIGNED_OUT, Instant.now());

        assertThat(revoked).isEqualTo(2);
        assertThat(refreshTokens.countByUserIdAndRevokedAtIsNull(owner.getId())).isZero();
        assertThat(refreshTokens.countByUserIdAndRevokedAtIsNull(stranger.getId())).isOne();
    }

    @Test
    @DisplayName("live sessions are counted, revoked ones are not")
    void countsOnlyLiveSessions() {
        RefreshToken spent = refreshTokens.save(
                RefreshToken.startFamily(owner, digest("old"), inDays(7)));
        spent.revoke(RevocationReason.SIGNED_OUT, Instant.now());
        refreshTokens.save(RefreshToken.startFamily(owner, digest("current"), inDays(7)));
        refreshTokens.flush();

        assertThat(refreshTokens.countByUserIdAndRevokedAtIsNull(owner.getId())).isOne();
    }

    @Test
    @DisplayName("live sessions come back oldest first, which is the order the cap evicts in")
    void ordersLiveSessionsOldestFirst() {
        RefreshToken older = refreshTokens.save(
                RefreshToken.startFamily(owner, digest("older"), inDays(7)));
        RefreshToken newer = refreshTokens.save(
                RefreshToken.startFamily(owner, digest("newer"), inDays(7)));
        refreshTokens.flush();
        // Two rows inserted in the same millisecond would make this assertion a coin toss.
        TestFixtures.backdate(em.getEntityManager(), "refresh_tokens", older.getId(),
                Instant.now().minus(3, ChronoUnit.DAYS));
        em.clear();

        List<RefreshToken> ordered =
                refreshTokens.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(owner.getId());

        assertThat(ordered).extracting(RefreshToken::getTokenHash)
                .containsExactly(older.getTokenHash(), newer.getTokenHash());
    }

    @Test
    @DisplayName("only expired rows are swept")
    void deletesOnlyExpiredRows() {
        refreshTokens.save(RefreshToken.startFamily(
                owner, digest("stale"), Instant.now().minus(1, ChronoUnit.DAYS)));
        refreshTokens.save(RefreshToken.startFamily(owner, digest("live"), inDays(7)));
        refreshTokens.flush();
        em.clear();

        int deleted = refreshTokens.deleteByExpiresAtBefore(Instant.now());

        assertThat(deleted).isOne();
        assertThat(refreshTokens.findByTokenHash(digest("stale"))).isEmpty();
        assertThat(refreshTokens.findByTokenHash(digest("live"))).isPresent();
    }

    @Test
    @DisplayName("an expired token is not usable even though it was never revoked")
    void treatsExpiryAsUnusable() {
        RefreshToken expired = refreshTokens.saveAndFlush(RefreshToken.startFamily(
                owner, digest("aged-out"), Instant.now().minus(1, ChronoUnit.MINUTES)));

        assertThat(expired.isRevoked()).isFalse();
        assertThat(expired.isUsableAt(Instant.now())).isFalse();
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    /**
     * A 64-character hex string derived from a label, so each test row has a stable, distinct
     * hash without any test needing to know how the real digest is computed.
     */
    private static String digest(String label) {
        String padded = (label + "-").repeat(1 + 64 / (label.length() + 1));
        return HexFormat.of().formatHex(padded.getBytes()).substring(0, 64);
    }
}
