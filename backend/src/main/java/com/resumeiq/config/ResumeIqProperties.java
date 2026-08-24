package com.resumeiq.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Type-safe binding for every {@code resumeiq.*} setting.
 *
 * <p>Validated at startup, so a missing or malformed value fails fast with a clear
 * message instead of surfacing as a null pointer during the first request.
 *
 * @param app  application identity reported by the health endpoint
 * @param cors browser origins permitted to call this API
 * @param seed reference data loaded at startup
 * @param auth token lifetimes, cookie flags and login throttling
 */
@Validated
@ConfigurationProperties(prefix = "resumeiq")
public record ResumeIqProperties(
        @Valid @NotNull App app,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Seed seed,
        @Valid @NotNull Auth auth
) {

    /**
     * @param name    display name of the product
     * @param version version reported by {@code GET /api/health}
     */
    public record App(@NotBlank String name, @NotBlank String version) {
    }

    /**
     * @param allowedOrigins exact browser origins allowed to call the API.
     *                       Wildcards are intentionally unsupported: credentialed
     *                       requests (Phase 3) require explicit origins.
     */
    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /**
     * @param skills whether to load the skill taxonomy from {@code data/skills.json} on startup.
     *               On by default because an empty taxonomy silently degrades every analysis;
     *               switchable off for tests that want to control the table themselves.
     */
    public record Seed(@NotNull Boolean skills) {
    }

    /**
     * Everything the authentication phase needs to be tuned without a rebuild.
     *
     * @param jwtSecret          HMAC key for access tokens. Deliberately not {@code @NotBlank}:
     *                           an empty value is legal in development, where
     *                           {@code JwtService} generates a throwaway key rather than let a
     *                           default secret exist anywhere in this repository. Outside
     *                           development a blank value stops startup.
     * @param accessTokenMinutes access-token lifetime. Short by design — the token lives in
     *                           browser memory and is refreshed silently, so the cost of a
     *                           leaked one is bounded by this number.
     * @param refreshTokenDays   how long a session can be resumed without re-entering a
     *                           password. Each use rotates the token, so this is a cap on
     *                           inactivity rather than on how long one token is valid.
     * @param bcryptStrength     BCrypt cost factor. 12 is roughly a quarter of a second per
     *                           hash on a laptop, which is the point; tests override it to 4
     *                           so a suite that logs in fifty times still runs in seconds.
     * @param refreshCookieName  name of the httpOnly cookie carrying the refresh token
     * @param refreshCookieSameSite {@code Lax}, {@code Strict} or {@code None}. {@code Lax} is
     *                           what makes the refresh endpoint safe without a CSRF token: a
     *                           cross-site POST does not carry the cookie at all.
     * @param refreshCookieSecure whether to mark the cookie {@code Secure}. False in local
     *                           development because {@code http://localhost} is not a secure
     *                           context; true everywhere else.
     * @param maxLoginAttempts   failed attempts allowed per email and per IP before the
     *                           lockout window starts
     * @param lockoutMinutes     how long a locked-out email or address must wait
     */
    public record Auth(
            String jwtSecret,
            @Positive Integer accessTokenMinutes,
            @Positive Integer refreshTokenDays,
            @Min(4) Integer bcryptStrength,
            @NotBlank String refreshCookieName,
            @Pattern(regexp = "Lax|Strict|None") String refreshCookieSameSite,
            @NotNull Boolean refreshCookieSecure,
            @Positive Integer maxLoginAttempts,
            @Positive Integer lockoutMinutes
    ) {

        /** HS256 needs a 256-bit key, and jjwt refuses anything shorter. */
        public static final int MINIMUM_SECRET_LENGTH = 32;

        /** True when the environment supplied a key long enough to sign with. */
        public boolean hasUsableSecret() {
            return jwtSecret != null && jwtSecret.strip().length() >= MINIMUM_SECRET_LENGTH;
        }
    }
}
