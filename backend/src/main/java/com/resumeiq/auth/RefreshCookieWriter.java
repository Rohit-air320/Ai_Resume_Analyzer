package com.resumeiq.auth;

import com.resumeiq.auth.RefreshTokenService.IssuedRefreshToken;
import com.resumeiq.config.ResumeIqProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Builds and reads the refresh-token cookie.
 *
 * <p>Every attribute on it is load-bearing:
 *
 * <p><strong>{@code HttpOnly}.</strong> The reason the token is in a cookie at all. Script on the
 * page cannot read it, so a cross-site scripting bug that would otherwise hand over a week of
 * access is limited to the access token in memory — fifteen minutes, and gone on reload.
 *
 * <p><strong>{@code SameSite}.</strong> {@code Lax} by default, which is what makes the refresh
 * endpoint safe without a CSRF token: a cross-site {@code POST} carries no cookie, so a forged
 * request arrives unauthenticated. Configurable because a deployment that serves the frontend
 * from a different site needs {@code None} — and then needs a real CSRF token, which is exactly
 * why the value is a decision recorded in configuration rather than a constant here.
 *
 * <p><strong>{@code Path=/api/auth}.</strong> Narrower than {@code /}, so the browser does not
 * attach a week-long credential to every resume upload and analysis request that has no use for
 * it. Fewer requests carrying it means fewer logs, proxies and crash reports that could contain
 * it.
 *
 * <p><strong>{@code Secure}.</strong> Off in local development only, because {@code http://}
 * localhost is not a secure context and the browser would silently drop the cookie. Anything
 * deployed sets it.
 *
 * <p>There is deliberately no {@code Domain} attribute. Omitting it makes the cookie host-only,
 * so it is never sent to a sibling subdomain — a shared parent domain is how one compromised
 * subdomain ends up holding another one's sessions.
 */
@Component
public class RefreshCookieWriter {

    /**
     * Scope of the cookie. Must cover the refresh and sign-out endpoints and nothing else; if a
     * future endpoint needs it, widening this string is the deliberate act that grants it.
     */
    static final String COOKIE_PATH = "/api/auth";

    private final String name;
    private final String sameSite;
    private final boolean secure;
    private final Duration lifetime;

    public RefreshCookieWriter(ResumeIqProperties properties) {
        this.name = properties.auth().refreshCookieName();
        this.sameSite = properties.auth().refreshCookieSameSite();
        this.secure = properties.auth().refreshCookieSecure();
        this.lifetime = Duration.ofDays(properties.auth().refreshTokenDays());
    }

    /** The cookie name, so tests and the sign-out path do not restate it. */
    public String cookieName() {
        return name;
    }

    /** Sets a freshly issued token, with a lifetime matching the row behind it. */
    public ResponseCookie issue(IssuedRefreshToken refreshToken) {
        return baseBuilder(refreshToken.token()).maxAge(lifetime).build();
    }

    /**
     * Clears the cookie.
     *
     * <p>An empty value with {@code Max-Age=0}, and every other attribute identical to the one
     * that was set. That last part is the part people get wrong: a browser matches a deletion
     * against name, path and domain, so clearing a {@code /api/auth} cookie with a {@code /}
     * cookie leaves the original in place and the person stays signed in.
     */
    public ResponseCookie clear() {
        return baseBuilder("").maxAge(0).build();
    }

    /**
     * Pulls the token out of the request, if it is there at all.
     *
     * <p>Missing is not an error here — an anonymous visitor's first page load calls refresh to
     * find out whether they have a session, and "no cookie" is the ordinary answer.
     */
    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH);
    }
}
