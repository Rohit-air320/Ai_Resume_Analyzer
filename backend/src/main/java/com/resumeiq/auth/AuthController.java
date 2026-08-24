package com.resumeiq.auth;

import com.resumeiq.auth.AuthService.AuthenticatedSession;
import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.security.CurrentUser;
import com.resumeiq.user.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authentication endpoints.
 *
 * <p>This controller does one thing the service cannot: it decides which half of a session goes
 * in the body and which goes in a header. The access token is JSON, so the frontend can hold it
 * in memory. The refresh token is a {@code Set-Cookie} the browser stores and JavaScript cannot
 * read. Keeping that split in one small class means there is exactly one place to check that the
 * refresh token never leaks into a response body.
 *
 * <p>Every endpoint here is reachable without a token except {@code /me}, which is what makes it
 * the natural boundary: the four public ones are how identity is obtained, and {@code /me} is the
 * first thing that requires it.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, sign-in, silent refresh and sign-out")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieWriter refreshCookies;

    public AuthController(AuthService authService, RefreshCookieWriter refreshCookies) {
        this.authService = authService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Create an account",
            description = "Registers a new account and returns a session. "
                    + "Responds 409 if the email is already registered.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respondWithSession(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Sign in",
            description = "Exchanges an email and password for an access token and a refresh "
                    + "cookie. Responds 401 for any wrong combination and 429 once an email or "
                    + "address has failed too many times.")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {

        // getRemoteAddr, not the X-Forwarded-For header. Behind a proxy that header is the truth
        // and Boot's forward-headers-strategy makes getRemoteAddr report it; read directly and an
        // attacker could set a fresh value per request and walk straight around the throttle.
        AuthenticatedSession session = authService.login(request, httpRequest.getRemoteAddr());
        return respondWithSession(session, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renew a session",
            description = "Reads the refresh cookie, rotates it, and returns a new access token. "
                    + "Responds 401 with SESSION_EXPIRED when there is nothing to renew.")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest httpRequest) {
        // orElse(null) rather than a branch: a missing cookie and an unrecognised token are the
        // same answer to the caller, and the service already knows how to say it.
        String presented = refreshCookies.readToken(httpRequest).orElse(null);
        return respondWithSession(authService.refresh(presented), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Sign out",
            description = "Revokes the session behind the refresh cookie and clears it. "
                    + "Always responds 204, whether or not a session was found.")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        refreshCookies.readToken(httpRequest).ifPresent(authService::logout);
        // The cookie is cleared either way. A client that asked to sign out must end up signed
        // out locally even if the server had no row to revoke.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "Describe the signed-in account",
            description = "Returns the current account. Used on page load to confirm a token is "
                    + "still good and to hydrate the UI.")
    public UserProfileResponse me(@CurrentUser AuthenticatedUser caller) {
        return authService.profileOf(caller);
    }

    /**
     * Puts the body in the response and the refresh token in a cookie.
     *
     * <p>{@code POST} is not required by the HTTP specification for any of these, but it is
     * correct for all of them: each one changes server state — a row, a login timestamp, a
     * rotated token — and a {@code GET} that mutates is a {@code GET} a browser or a proxy will
     * eventually replay on its own.
     */
    private ResponseEntity<AuthResponse> respondWithSession(
            AuthenticatedSession session, HttpStatus status) {

        ResponseCookie cookie = refreshCookies.issue(session.refreshToken());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(session.body());
    }
}
