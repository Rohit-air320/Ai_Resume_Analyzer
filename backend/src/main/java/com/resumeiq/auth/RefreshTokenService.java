package com.resumeiq.auth;

import com.resumeiq.common.domain.Timestamps;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>The rules, in one place:
 * <ul>
 *   <li>a token is 256 bits from {@link SecureRandom}, so it cannot be guessed and does not
 *       need to be signed — there is nothing in it to forge;</li>
 *   <li>the database stores only its SHA-256 digest, so this table is worthless if leaked;</li>
 *   <li>every exchange rotates: the presented token dies, a new one is issued in the same
 *       family;</li>
 *   <li>a token presented twice ends the entire family, on the assumption that the second
 *       presenter is not the first.</li>
 * </ul>
 *
 * <p><strong>Why {@link #rotate} returns an empty {@code Optional} instead of throwing.</strong>
 * The reuse case has to write — it revokes the family — and then report failure. If it threw,
 * the exception would mark the surrounding transaction for rollback and the revocation would be
 * undone: the endpoint would answer 401 while quietly leaving the stolen family alive, which is
 * the exact opposite of what reuse detection is for. Returning a value lets the transaction
 * commit, and the caller turns the empty result into a 401 after the write has landed. The
 * reason a rotation was refused is logged here and never sent to the client, because "that
 * token was already used" is information only an attacker benefits from.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 32 bytes — the same 256 bits the digest that stores it produces. */
    private static final int TOKEN_BYTES = 32;

    /**
     * Live tokens one account may hold at once.
     *
     * <p>Sessions are per device, so a handful is normal and a hundred is not. The cap keeps a
     * stolen-password spree from accumulating an unbounded set of resumable sessions, and keeps
     * the table from growing without limit for one user. Oldest is evicted first, so the device
     * you used least recently is the one that asks for a password again.
     */
    private static final int MAX_ACTIVE_SESSIONS = 5;

    /** URL-safe and unpadded, so the value is clean in a cookie and in a log line. */
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokens;
    private final SecureRandom random = new SecureRandom();
    private final Duration lifetime;

    public RefreshTokenService(RefreshTokenRepository refreshTokens, ResumeIqProperties properties) {
        this.refreshTokens = refreshTokens;
        this.lifetime = Duration.ofDays(properties.auth().refreshTokenDays());
    }

    /** How long a new refresh token lasts. The cookie's {@code Max-Age} is set from this. */
    public Duration lifetime() {
        return lifetime;
    }

    /**
     * Starts a session: a new family, and a new token in it.
     *
     * <p>Called after registration and after a successful login — the two moments a password
     * was actually presented.
     */
    @Transactional
    public IssuedRefreshToken startSession(User user) {
        enforceSessionCap(user);
        String token = generateToken();
        RefreshToken saved = refreshTokens.save(
                RefreshToken.startFamily(user, hash(token), Timestamps.now().plus(lifetime)));
        return new IssuedRefreshToken(token, saved.getExpiresAt());
    }

    /**
     * Exchanges a refresh token for its successor.
     *
     * @return the account and its new token, or empty if the presented token was unknown,
     *         expired, or had already been spent
     */
    @Transactional
    public Optional<RotatedSession> rotate(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> found = refreshTokens.findByTokenHash(hash(presentedToken));
        if (found.isEmpty()) {
            // Never issued, or swept after expiry. Nothing to revoke.
            log.debug("Refresh rejected: token not on file");
            return Optional.empty();
        }

        RefreshToken current = found.get();
        Instant now = Timestamps.now();

        if (current.isRevoked()) {
            // Presented after it was spent. Assume the copy in play is not the owner's.
            int revoked = refreshTokens.revokeFamily(
                    current.getFamilyId(), RevocationReason.REUSE_DETECTED, now);
            log.warn("Refresh token reuse detected for family {} (previous reason {}); "
                            + "revoked {} remaining token(s) in the family",
                    current.getFamilyId(), current.getRevokedReason(), revoked);
            return Optional.empty();
        }

        if (current.isExpiredAt(now)) {
            log.debug("Refresh rejected: token expired at {}", current.getExpiresAt());
            return Optional.empty();
        }

        // Normal path: spend the old token, issue its successor in the same family.
        current.revoke(RevocationReason.ROTATED, now);
        String token = generateToken();
        RefreshToken next = refreshTokens.save(RefreshToken.issue(
                current.getUser(), hash(token), current.getFamilyId(), now.plus(lifetime)));

        return Optional.of(new RotatedSession(
                current.getUser(), new IssuedRefreshToken(token, next.getExpiresAt())));
    }

    /**
     * Ends the session a token belongs to.
     *
     * <p>Deliberately silent about whether the token existed. Sign-out is not a place to tell a
     * caller which tokens are real, and a client clearing a cookie it no longer has a row for
     * should still see a clean 204 rather than an error it cannot act on.
     */
    @Transactional
    public void endSession(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(presentedToken)).ifPresent(token ->
                refreshTokens.revokeFamily(
                        token.getFamilyId(), RevocationReason.SIGNED_OUT, Timestamps.now()));
    }

    /**
     * Ends every session an account has. For account deletion and, later, a password change —
     * the two events after which an old session should not survive.
     */
    @Transactional
    public int endAllSessions(Long userId) {
        return refreshTokens.revokeAllForUser(userId, RevocationReason.SIGNED_OUT, Timestamps.now());
    }

    /** Removes expired rows. Wired to a schedule in a later phase; safe to call at any time. */
    @Transactional
    public int purgeExpired() {
        return refreshTokens.deleteByExpiresAtBefore(Timestamps.now());
    }

    /**
     * Revokes the oldest live tokens until there is room for one more.
     *
     * <p>Runs on login rather than on a timer: the moment a new session is created is the only
     * moment the count can exceed the cap.
     */
    private void enforceSessionCap(User user) {
        if (!user.isPersisted()) {
            // A brand new account cannot have sessions, and querying on a null id would fail.
            return;
        }
        int active = refreshTokens.countByUserIdAndRevokedAtIsNull(user.getId());
        if (active < MAX_ACTIVE_SESSIONS) {
            return;
        }

        List<RefreshToken> oldestFirst =
                refreshTokens.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(user.getId());
        int excess = active - MAX_ACTIVE_SESSIONS + 1;
        Instant now = Timestamps.now();
        for (RefreshToken token : oldestFirst.subList(0, Math.min(excess, oldestFirst.size()))) {
            // The whole family, not the single row: a rotated descendant of an evicted session
            // would otherwise keep it alive.
            refreshTokens.revokeFamily(token.getFamilyId(), RevocationReason.SIGNED_OUT, now);
        }
        log.debug("Session cap reached for user {}; evicted {} oldest session(s)",
                user.getId(), excess);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    /**
     * SHA-256, hex encoded — the form the {@code token_hash} column holds.
     *
     * <p>A plain digest rather than BCrypt, and that is the right call here: BCrypt's cost
     * factor exists to slow down guessing a human-chosen password, and there is nothing to
     * guess in 256 random bits. A digest is also a value that can be looked up by index, where
     * BCrypt would force a scan of candidate rows on every refresh.
     *
     * <p>Static and stateless because {@link MessageDigest} instances are not thread safe; a
     * fresh one per call costs microseconds and removes the question entirely.
     */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // Mandated by the JDK specification, so unreachable on a working runtime.
            throw new IllegalStateException("This JVM cannot compute SHA-256", ex);
        }
    }

    /**
     * A token in the only form the client ever sees it, plus when it stops working.
     *
     * @param token     the raw value, which exists in memory for the length of one response and
     *                  is never stored anywhere in that form
     * @param expiresAt when the row behind it stops being accepted
     */
    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }

    /**
     * The result of a successful rotation.
     *
     * @param user         the account the presented token belonged to, loaded and ready to sign
     *                     a new access token for
     * @param refreshToken the successor token, to be written to the cookie
     */
    public record RotatedSession(User user, IssuedRefreshToken refreshToken) {
    }
}
