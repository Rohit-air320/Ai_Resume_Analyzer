package com.resumeiq.security;

import com.resumeiq.common.domain.Timestamps;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.config.ResumeIqProperties.Auth;
import com.resumeiq.user.Role;
import com.resumeiq.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and verifies access tokens.
 *
 * <p>What is in a token is as much a design decision as how it is signed:
 * <ul>
 *   <li>the subject is the account's {@code publicId}, never the database id, so a captured
 *       token tells an attacker nothing about how many accounts exist;</li>
 *   <li>the role travels as a claim, because authorisation decisions should not need a
 *       database round trip;</li>
 *   <li>nothing else does. No name, no email, no plan — a token is not a place to cache
 *       profile data, and every claim added is a claim that goes stale the moment it is
 *       signed.</li>
 * </ul>
 *
 * <p>The identity is still loaded from the database on each request (see
 * {@link JwtAuthenticationFilter}). That is one indexed lookup per call, and it buys
 * something a self-contained token cannot: deleting or disabling an account takes effect
 * immediately rather than whenever the last issued token happens to expire.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Rejected on parse, so a token minted by some other system is never accepted here. */
    static final String ISSUER = "resumeiq";

    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration accessTokenLifetime;

    public JwtService(ResumeIqProperties properties, Environment environment) {
        Auth auth = properties.auth();
        this.signingKey = resolveSigningKey(auth, environment);
        this.accessTokenLifetime = Duration.ofMinutes(auth.accessTokenMinutes());
    }

    /**
     * Where the HMAC key comes from, and why there is no fallback constant.
     *
     * <p>A default secret committed to a repository is not a convenience, it is a published
     * private key: every deployment that forgets to override it can be issued tokens by
     * anyone who has read the source. So a configured key is used when present, development
     * gets a random key generated per JVM — tokens stop working when you restart, which is
     * the correct and obvious signal — and any other profile refuses to start without one.
     */
    private static SecretKey resolveSigningKey(Auth auth, Environment environment) {
        if (auth.hasUsableSecret()) {
            return Keys.hmacShaKeyFor(auth.jwtSecret().strip().getBytes(StandardCharsets.UTF_8));
        }
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException(
                    "JWT_SECRET is required outside the dev profile and must be at least "
                            + Auth.MINIMUM_SECRET_LENGTH + " characters. "
                            + "Generate one with: openssl rand -base64 48");
        }
        log.warn("No JWT_SECRET configured. Generating a throwaway key for this run — "
                + "every access token becomes invalid when the API restarts. "
                + "Set JWT_SECRET in .env to keep sessions across restarts.");
        return generateDevelopmentKey();
    }

    private static SecretKey generateDevelopmentKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
            generator.init(256);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException ex) {
            // HmacSHA256 is mandated by the JDK specification, so this cannot happen on a
            // working runtime — but swallowing it would turn a broken JVM into a null key.
            throw new IllegalStateException("This JVM cannot generate an HMAC-SHA256 key", ex);
        }
    }

    /** How long a freshly issued token is good for. Reported to the client so it can refresh early. */
    public Duration accessTokenLifetime() {
        return accessTokenLifetime;
    }

    public String issueAccessToken(User user) {
        return issueAccessToken(user, Timestamps.now());
    }

    /**
     * Package-private so a test can date a token into the past and prove expiry is enforced.
     *
     * <p>The alternative — injecting a {@code Clock} — would put a seam in production code
     * that exists only for tests. This way production has one call path and the test still
     * gets a real expired token rather than a mocked one.
     */
    String issueAccessToken(User user, Instant issuedAt) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getPublicId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenLifetime)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, and returns what the token asserts.
     *
     * @throws JwtException if the token is malformed, expired, signed with another key, or
     *                      issued by something other than this application
     */
    public VerifiedAccessToken verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new VerifiedAccessToken(
                parseSubject(claims.getSubject()),
                parseRole(claims.get(CLAIM_ROLE, String.class)),
                claims.getExpiration().toInstant());
    }

    private static UUID parseSubject(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new JwtException("Token subject is not a public id");
        }
    }

    private static Role parseRole(String role) {
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // A role that no longer exists in the enum must fail closed, not default to USER.
            throw new JwtException("Token carries an unknown role");
        }
    }

    /**
     * What a valid token asserts. Not an identity — the account behind it is loaded per
     * request, so a deleted user cannot keep making calls with a token that has not expired.
     *
     * @param subject   the account's public id
     * @param role      role claimed by the token
     * @param expiresAt when this token stops being accepted
     */
    public record VerifiedAccessToken(UUID subject, Role role, Instant expiresAt) {
    }
}
