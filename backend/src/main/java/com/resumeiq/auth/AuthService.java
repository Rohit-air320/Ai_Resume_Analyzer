package com.resumeiq.auth;

import com.resumeiq.auth.RefreshTokenService.IssuedRefreshToken;
import com.resumeiq.auth.RefreshTokenService.RotatedSession;
import com.resumeiq.common.exception.ConflictException;
import com.resumeiq.common.exception.ResourceNotFoundException;
import com.resumeiq.common.exception.UnauthorizedException;
import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.security.JwtService;
import com.resumeiq.user.User;
import com.resumeiq.user.UserProfileResponse;
import com.resumeiq.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration, sign-in, silent refresh and sign-out.
 *
 * <p>This is the only class that turns a password into a session, so the decisions that protect
 * that boundary all live here.
 *
 * <p><strong>Sign-in takes the same time whether the email exists or not.</strong> The obvious
 * implementation returns early when the lookup misses, which skips BCrypt — and a request that
 * comes back in two milliseconds instead of two hundred has just answered "is this address
 * registered?" without meaning to. So a miss is verified against a throwaway hash instead. The
 * work is wasted on purpose; that waste is the feature.
 *
 * <p><strong>The throttle is consulted before the password is compared.</strong> That ordering
 * bounds the number of BCrypt operations an attacker can force — otherwise a throttle that only
 * rejected after checking would still let them spend the server's CPU — and it is also what keeps
 * a locked-out key from having its lockout extended, since a locked request never reaches the
 * counter.
 *
 * <p><strong>{@link #refresh} is not transactional, and that is deliberate.</strong> Rotation has
 * to be able to write and then fail: reuse detection revokes a whole family and answers 401. If
 * this method opened a transaction, the rotation service would join it, the 401 would mark it for
 * rollback, and the revocation would vanish — a stolen session left alive by the very check meant
 * to kill it. Keeping the transaction inside {@link RefreshTokenService} means it commits before
 * this method decides to throw.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;

    /**
     * A valid BCrypt hash of a value nobody knows, used to spend the same CPU on an unknown
     * email as on a known one.
     *
     * <p>Computed once at startup rather than written as a constant: a hash in source is a hash
     * an attacker can recognise, and computing it from a fresh random value means there is
     * nothing to recognise. It costs one BCrypt operation per boot.
     */
    private final String comparisonHash;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokens,
                       LoginAttemptService loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.comparisonHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Creates an account and signs it straight in.
     *
     * <p>Signing in immediately is a product decision with a security consequence worth naming:
     * it means registration issues a session without any email verification, so an address that
     * belongs to somebody else can be claimed. That is the right trade for a portfolio tool
     * nobody is emailed by — and the moment this application sends mail, verification comes
     * before the session, not after.
     */
    @Transactional
    public AuthenticatedSession register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with that email already exists.");
        }

        User user;
        try {
            user = users.save(User.register(
                    email, passwordEncoder.encode(request.password()), request.fullName().trim()));
            // Forces the insert now, so a collision surfaces here as a catchable exception
            // instead of at commit time, where it would escape as a 500.
            users.flush();
        } catch (DataIntegrityViolationException ex) {
            // Two registrations for the same address, in flight at the same time. The check
            // above lost the race; the unique constraint is what actually decides, which is why
            // the constraint exists rather than trusting the read.
            throw new ConflictException("An account with that email already exists.");
        }

        log.info("Registered account {}", user.getPublicId());
        return startSession(user);
    }

    /**
     * Verifies a password and starts a session.
     *
     * @param ipAddress caller's address, used only as a throttle key
     */
    @Transactional
    public AuthenticatedSession login(LoginRequest request, String ipAddress) {
        loginAttempts.assertAttemptAllowed(request.email(), ipAddress);

        Optional<User> found = users.findByEmailNormalized(request.email());
        // Runs in both branches. See the note on constant time in the class javadoc.
        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                found.map(User::getPasswordHash).orElse(comparisonHash));

        if (found.isEmpty() || !passwordMatches) {
            loginAttempts.recordFailure(request.email(), ipAddress);
            // No email in the log line: a failed-login log is exactly the sort of file that gets
            // shared while debugging, and it should not double as a list of valid addresses.
            log.debug("Failed sign-in attempt from {}", ipAddress);
            throw UnauthorizedException.invalidCredentials();
        }

        loginAttempts.recordSuccess(request.email(), ipAddress);

        User user = found.get();
        user.setLastLoginAt(Instant.now());
        // Saved before the session is created: creating one can evict an older session with a
        // bulk update that clears the persistence context, and a pending change left unflushed
        // at that point would be silently discarded.
        User persisted = users.save(user);

        log.info("Signed in account {}", persisted.getPublicId());
        return startSession(persisted);
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token.
     *
     * <p>Not annotated {@code @Transactional} — see the class javadoc, where the reason is the
     * whole point.
     *
     * @throws UnauthorizedException with {@code SESSION_EXPIRED} if the token is missing,
     *                               expired, or already spent
     */
    public AuthenticatedSession refresh(String presentedToken) {
        RotatedSession rotated = refreshTokens.rotate(presentedToken)
                .orElseThrow(UnauthorizedException::sessionExpired);

        User user = rotated.user();
        return new AuthenticatedSession(
                AuthResponse.of(jwtService.issueAccessToken(user), jwtService.accessTokenLifetime(), user),
                rotated.refreshToken());
    }

    /**
     * Ends the session behind a refresh token.
     *
     * <p>Succeeds whether or not the token was real. Sign-out is not a place to report which
     * tokens exist, and a client clearing a cookie the server has no row for should still get a
     * clean answer rather than an error it cannot act on.
     */
    public void logout(String presentedToken) {
        refreshTokens.endSession(presentedToken);
    }

    /**
     * The full account behind an authenticated request.
     *
     * <p>Reads the row again rather than dressing up the principal. The principal is deliberately
     * the reduced form — id, email, name, role, the things an authorisation decision needs — and
     * padding it out with a target role and a member-since date so that one endpoint can echo
     * them would put profile data into every request's security context, where it would go stale
     * the moment the profile was edited.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse profileOf(AuthenticatedUser caller) {
        return users.findByPublicId(caller.publicId())
                .map(UserProfileResponse::from)
                // The filter loaded this account moments ago, so an empty result means it was
                // deleted mid-request. A 404 is the honest answer: the account is gone.
                .orElseThrow(() -> new ResourceNotFoundException("That account no longer exists."));
    }

    private AuthenticatedSession startSession(User user) {
        IssuedRefreshToken refreshToken = refreshTokens.startSession(user);
        return new AuthenticatedSession(
                AuthResponse.of(jwtService.issueAccessToken(user), jwtService.accessTokenLifetime(), user),
                refreshToken);
    }

    /**
     * The two halves of a session, kept apart until the controller can put each in its place.
     *
     * <p>The body goes to the client as JSON; the refresh token goes into a {@code Set-Cookie}
     * header and must never reach the body. Returning them as one object from the service, and
     * splitting them in the controller, is what makes that impossible to get wrong by accident —
     * the service has no way to serialise the cookie half.
     *
     * @param body         the response payload, access token included
     * @param refreshToken the cookie half, written by {@link RefreshCookieWriter}
     */
    public record AuthenticatedSession(AuthResponse body, IssuedRefreshToken refreshToken) {
    }
}
