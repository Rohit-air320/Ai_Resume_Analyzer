package com.resumeiq.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.support.AuthIntegrationTest;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole authentication flow over HTTP: sign up, sign in, refresh, sign out.
 *
 * <p>Everything here goes through the real filter chain, the real database and real BCrypt,
 * because the interesting behaviour is not inside any one class. A cookie with the right
 * attributes, a token the next request is allowed to use, a replayed refresh token that takes the
 * whole session down with it — each of those is produced by several components agreeing, and a
 * test that mocked any of them would stop describing what a browser will actually experience.
 */
@AuthIntegrationTest
class AuthFlowTest {

    private static final String EMAIL = "casey@example.test";
    private static final String FULL_NAME = "Casey Rivers";

    /**
     * Both values say "example" on purpose. A test password that looks like a real one is a
     * password somebody eventually copies, and the secret scanner in {@code tools/} is right to
     * treat anything else in a source file as a finding.
     */
    private static final String PASSWORD = "example-passphrase-9";

    private static final String WRONG_PASSWORD = "example-passphrase-8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private LoginAttemptService loginAttempts;

    @Autowired
    private RefreshCookieWriter refreshCookies;

    @Autowired
    private ResumeIqProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Every test here commits, and the throttle is a field on a singleton. Both have to be put
     * back by hand — a MockMvc call is not wrapped in a rollback, and one test's failed sign-ins
     * would otherwise lock out the next test's account.
     */
    @BeforeEach
    void clearState() {
        refreshTokens.deleteAll();
        users.deleteAll();
        loginAttempts.reset();
    }

    @Test
    @DisplayName("signing up returns an access token in the body and a refresh token in a cookie")
    void signingUpStartsASession() throws Exception {
        MvcResult result = signUp(EMAIL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds")
                        .value(properties.auth().accessTokenMinutes() * 60))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.fullName").value(FULL_NAME))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.id").isString())
                // No refreshToken key anywhere in the payload, under any name.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String refreshToken = refreshCookieValue(result);

        // The real assertion behind the one above: whatever the field were called, the token's
        // value must not appear in the response body at all.
        assertThat(body).doesNotContain(refreshToken);
        assertThat(refreshTokens.count()).isOne();
    }

    @Test
    @DisplayName("the refresh cookie is httpOnly, same-site, and scoped to the auth endpoints")
    void refreshCookieIsHardened() throws Exception {
        MvcResult result = signUp(EMAIL).andExpect(status().isCreated()).andReturn();

        Cookie cookie = result.getResponse().getCookie(refreshCookies.cookieName());
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge())
                .isEqualTo(properties.auth().refreshTokenDays() * 86_400);

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("HttpOnly")
                .contains("SameSite=" + properties.auth().refreshCookieSameSite())
                // Host-only. A Domain attribute would send a week-long credential to every
                // sibling subdomain, which is how one compromised host takes another's sessions.
                .doesNotContain("Domain");
    }

    @Test
    @DisplayName("the stored account holds a bcrypt hash, never the password")
    void storesOnlyAHash() throws Exception {
        signUp(EMAIL).andExpect(status().isCreated());

        User stored = users.findByEmailNormalized(EMAIL).orElseThrow();

        assertThat(stored.getPasswordHash())
                .isNotEqualTo(PASSWORD)
                .startsWith("$2")
                .hasSizeGreaterThan(50);
    }

    @Test
    @DisplayName("an email already registered is refused however it is capitalised")
    void refusesADuplicateEmail() throws Exception {
        signUp(EMAIL).andExpect(status().isCreated());

        // Different capitalisation, same person. Emails are case-insensitive in practice, so an
        // account that can be created twice by holding shift is an account with two passwords.
        signUp("CASEY@Example.TEST")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));

        assertThat(users.count()).isOne();
    }

    @Test
    @DisplayName("signing up validates the body and names the offending fields")
    void validatesTheSignUpBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "password", "short",
                                "fullName", "C"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(org.hamcrest.Matchers.hasItems("email", "password", "fullName")));

        assertThat(users.count()).isZero();
    }

    @Test
    @DisplayName("a token from sign-in opens a closed endpoint")
    void signInReturnsAUsableToken() throws Exception {
        signUp(EMAIL).andExpect(status().isCreated());

        MvcResult signedIn = signIn(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(signedIn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value(FULL_NAME))
                // The profile is the whole account, but never the column that proves a password.
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("signing in records when it happened")
    void recordsTheSignIn() throws Exception {
        signUp(EMAIL).andExpect(status().isCreated());
        assertThat(users.findByEmailNormalized(EMAIL).orElseThrow().getLastLoginAt()).isNull();

        signIn(EMAIL, PASSWORD).andExpect(status().isOk());

        assertThat(users.findByEmailNormalized(EMAIL).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("an unknown email and a wrong password produce the same answer")
    void doesNotRevealWhichAccountsExist() throws Exception {
        signUp(EMAIL).andExpect(status().isCreated());

        JsonNode wrongPassword = errorBody(signIn(EMAIL, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized()));
        JsonNode unknownEmail = errorBody(signIn("nobody@example.test", PASSWORD)
                .andExpect(status().isUnauthorized()));

        // Same status, same code, same words. This is the property that stops the sign-in form
        // being used to find out who has an account here; the equal timing that backs it up is
        // AuthService's constant-time comparison, which no assertion can prove reliably.
        assertThat(wrongPassword.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownEmail.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownEmail.get("message")).isEqualTo(wrongPassword.get("message"));
    }

    @Test
    @DisplayName("too many failures are throttled, and the throttle outranks a correct password")
    void throttlesRepeatedFailures() throws Exception {
        signUp(EMAIL).andExpect(status().isCreated());
        int allowance = properties.auth().maxLoginAttempts();

        for (int attempt = 0; attempt < allowance; attempt++) {
            signIn(EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        signIn(EMAIL, WRONG_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // The right password is refused too. That is not a bug: the throttle is consulted before
        // the password is compared, which is what keeps an attacker from spending the server's
        // BCrypt budget and what stops a locked key from having its lockout extended.
        signIn(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("refreshing rotates the cookie and keeps the session usable")
    void refreshRotatesTheCookie() throws Exception {
        MvcResult signedUp = signUp(EMAIL).andExpect(status().isCreated()).andReturn();
        String first = refreshCookieValue(signedUp);

        MvcResult refreshed = refreshWith(first).andExpect(status().isOk()).andReturn();
        String second = refreshCookieValue(refreshed);

        assertThat(second).isNotEqualTo(first);
        // Two rows in one family: the spent one, kept so a replay can be recognised, and its
        // successor. Sign-in count is unchanged — refreshing is not a new session.
        assertThat(refreshTokens.count()).isEqualTo(2);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("presenting a spent refresh token ends the entire session")
    void reusedRefreshTokenEndsTheFamily() throws Exception {
        MvcResult signedUp = signUp(EMAIL).andExpect(status().isCreated()).andReturn();
        String stolen = refreshCookieValue(signedUp);
        String current = refreshCookieValue(refreshWith(stolen).andExpect(status().isOk()).andReturn());

        // The thief's copy, presented after the owner's browser already rotated it.
        refreshWith(stolen)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        // And the owner's live token is gone too. That is the point: the server cannot tell which
        // of the two holders is the real one, so it ends the family and both must sign in again.
        // A false alarm costs one sign-in; the alternative costs the account.
        refreshWith(current)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        assertThat(refreshTokens.countByUserIdAndRevokedAtIsNull(
                users.findByEmailNormalized(EMAIL).orElseThrow().getId())).isZero();
    }

    @Test
    @DisplayName("refreshing without a cookie is a finished session, not an error")
    void refreshWithoutACookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("an invented refresh token is refused without revoking anything")
    void refreshWithAnUnknownToken() throws Exception {
        MvcResult signedUp = signUp(EMAIL).andExpect(status().isCreated()).andReturn();
        String genuine = refreshCookieValue(signedUp);

        refreshWith("a-token-that-was-never-issued")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        // A guess must not be able to end somebody else's session — there is no family to revoke,
        // so the real one is untouched.
        refreshWith(genuine).andExpect(status().isOk());
    }

    @Test
    @DisplayName("signing out clears the cookie and the session behind it")
    void signOutEndsTheSession() throws Exception {
        MvcResult signedUp = signUp(EMAIL).andExpect(status().isCreated()).andReturn();
        String refreshToken = refreshCookieValue(signedUp);

        MvcResult signedOut = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(refreshCookies.cookieName(), refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cleared = signedOut.getResponse().getCookie(refreshCookies.cookieName());
        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        // Same path as the cookie that was set. A deletion is matched on name and path, so
        // clearing a /api/auth cookie from / leaves the original in the browser.
        assertThat(cleared.getPath()).isEqualTo("/api/auth");

        refreshWith(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("signing out with nothing to sign out of still succeeds")
    void signOutWithoutASession() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("a sixth session evicts the oldest one")
    void capsActiveSessions() throws Exception {
        // Sign-up is session one; five sign-ins follow. The cap is five, so creating the sixth
        // has to remove something, and the contract is that it removes the least recently used.
        String oldest = refreshCookieValue(signUp(EMAIL).andExpect(status().isCreated()).andReturn());
        backdateSession(oldest, Instant.parse("2026-01-01T00:00:00Z"));

        String newest = null;
        for (int session = 1; session <= 5; session++) {
            newest = refreshCookieValue(signIn(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn());
            backdateSession(newest, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(session));
        }

        refreshWith(oldest)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        refreshWith(newest).andExpect(status().isOk());
    }

    private ResultActions signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "fullName", FULL_NAME))));
    }

    private ResultActions signIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions refreshWith(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(refreshCookies.cookieName(), refreshToken)));
    }

    private String accessToken(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String refreshCookieValue(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(refreshCookies.cookieName());
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }

    private JsonNode errorBody(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /**
     * Moves one session's {@code created_at} into the past.
     *
     * <p>The session cap evicts oldest first, and six sessions created inside the same test can
     * share a clock tick — which would make "the oldest one" whichever row the database happened
     * to return. Addressed by hash rather than by id because the hash is derivable from the
     * cookie the test is already holding, and finding the row by it proves the cookie really is
     * the token behind that row.
     */
    private void backdateSession(String refreshToken, Instant when) {
        int updated = jdbc.update("update refresh_tokens set created_at = ? where token_hash = ?",
                Timestamp.from(when), RefreshTokenService.hash(refreshToken));
        assertThat(updated).as("backdated session rows").isOne();
    }
}
