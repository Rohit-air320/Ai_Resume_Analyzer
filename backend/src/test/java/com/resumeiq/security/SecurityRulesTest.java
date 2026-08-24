package com.resumeiq.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.auth.RefreshTokenRepository;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.support.AuthIntegrationTest;
import com.resumeiq.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the filter chain lets through, and what it does not.
 *
 * <p>{@code SecurityConfig} reads as a list of intentions. This class is where those intentions
 * become facts, and it exists because the two most expensive mistakes in a chain like this are
 * both invisible in a code review: a rule written in the wrong order, and a route that nobody
 * remembered to close. The second one is why {@link #anUnknownApiPathIsClosedToo} matters more
 * than it looks — every endpoint added in a later phase inherits {@code anyRequest().authenticated()},
 * and this test is what proves that default is really in force.
 *
 * <p>{@code HealthControllerTest} covers the health endpoint's payload with a slice test that
 * disables filters. This class covers the opposite half: whether a request can get there at all.
 */
@AuthIntegrationTest
class SecurityRulesTest {

    private static final String EMAIL = "dana@example.test";
    /** Says "example" because the secret scanner is right to flag any test password that does not. */
    private static final String PASSWORD = "example-passphrase-7";

    /** The three messages the entry point can produce, asserted as user-facing copy. */
    private static final String ANONYMOUS_MESSAGE = "Please sign in to continue.";
    private static final String UNUSABLE_TOKEN_MESSAGE =
            "We could not verify your sign-in. Please sign in again.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private ResumeIqProperties properties;

    @BeforeEach
    void clearState() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    @DisplayName("the health endpoint answers without a token")
    void healthIsOpen() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("registering and signing in are reachable before anybody has an identity")
    void theWaysInAreOpen() throws Exception {
        // 400, not 401. A validation failure is proof the request reached the controller: had the
        // chain refused it, the entry point would have answered first and the body would say
        // UNAUTHORIZED instead. That is the assertion — the status code is only how it shows up.
        postEmptyBody("/api/auth/register")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        postEmptyBody("/api/auth/login")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("refresh and sign-out are reachable with no cookie and no token")
    void theWaysBackAreOpen() throws Exception {
        // Also a 401, but a different code from a different place: SESSION_EXPIRED comes from the
        // controller, so this proves the route is public even though the answer is a refusal.
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a closed endpoint refuses an anonymous request in the project's error envelope")
    void closedEndpointRefusesAnonymousRequests() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(ANONYMOUS_MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.timestamp").exists())
                // Spring Security's own 401 carries this header, and in a browser it opens the
                // native basic-auth dialog — a modal the frontend cannot dismiss. Its absence is
                // the reason RestAuthenticationEntryPoint exists.
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .as("a 401 body must carry nothing about how the server is built")
                .doesNotContain("Exception")
                .doesNotContain("com.resumeiq")
                .doesNotContain("trace");
    }

    @Test
    @DisplayName("a path with no handler is refused before anybody learns it has no handler")
    void anUnknownApiPathIsClosedToo() throws Exception {
        // /api/analyses arrives in a later phase. Today nothing maps it, and an anonymous caller
        // must still get 401 rather than 404: the chain authorises before dispatch, so a new
        // controller is private the moment it is written and a 404 never doubles as a way to
        // enumerate which endpoints exist.
        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("a token that is not a token says so differently")
    void garbageTokenIsRefused() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-json-web-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                // Different wording from the anonymous case on purpose: "you were never signed
                // in" and "what you sent is not usable" are different situations for the person
                // reading the screen, even though the code the client switches on is the same.
                .andExpect(jsonPath("$.message").value(UNUSABLE_TOKEN_MESSAGE));

        assertThat(UNUSABLE_TOKEN_MESSAGE).isNotEqualTo(ANONYMOUS_MESSAGE);
    }

    @Test
    @DisplayName("an Authorization header in another scheme is ignored, not misread")
    void anotherSchemeIsIgnored() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Basic ZGFuYQ=="))
                .andExpect(status().isUnauthorized())
                // The anonymous message, not the unusable-token one: the filter never treated the
                // credential as a bearer token, so there was nothing to fail at verifying. Basic
                // auth is disabled on this chain and this is what that looks like from outside.
                .andExpect(jsonPath("$.message").value(ANONYMOUS_MESSAGE));
    }

    @Test
    @DisplayName("an empty bearer value is ignored rather than parsed")
    void anEmptyBearerValueIsIgnored() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer   "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ANONYMOUS_MESSAGE));
    }

    @Test
    @DisplayName("a valid token for a deleted account stops working immediately")
    void tokenForADeletedAccountStopsWorking() throws Exception {
        String token = accessToken(signUp().andExpect(status().isCreated()).andReturn());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        refreshTokens.deleteAll();
        users.deleteAll();

        // The signature is still perfectly valid and the token has minutes left to run. It fails
        // anyway, because the filter loads the account on every request instead of trusting the
        // claims. That per-request read is what makes a deletion take effect at once rather than
        // whenever the last issued token happens to expire.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(UNUSABLE_TOKEN_MESSAGE));
    }

    @Test
    @DisplayName("no server-side session is created, for an authenticated request or a refused one")
    void nothingIsStoredOnTheServer() throws Exception {
        MvcResult signedUp = signUp().andExpect(status().isCreated()).andReturn();
        MvcResult authenticated = mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(signedUp)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult refused = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Nothing to replicate between instances, nothing to invalidate, nothing to grow in
        // memory under load. This is the observable half of SessionCreationPolicy.STATELESS.
        assertThat(authenticated.getRequest().getSession(false)).as("session after success").isNull();
        assertThat(refused.getRequest().getSession(false)).as("session after refusal").isNull();
        assertThat(authenticated.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.contains("JSESSIONID"));
    }

    @Test
    @DisplayName("a preflight from a configured origin is answered without credentials")
    void preflightIsAllowed() throws Exception {
        String origin = properties.cors().allowedOrigins().get(0);

        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                // Required for the refresh cookie to be sent at all by a cross-origin frontend.
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("a preflight from an origin nobody configured is turned away")
    void preflightFromAnUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, "https://not-our-frontend.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                // No echo of the origin. A wildcard here would be illegal anyway once credentials
                // are allowed, but the mistake worth guarding is the one where somebody reflects
                // whatever Origin arrived — which is a wildcard with extra steps.
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("the API documentation is readable without signing in")
    void documentationIsOpen() throws Exception {
        // Asserted as "not 401" rather than "200" deliberately. The rule under test belongs to
        // this chain: the docs paths are permitAll. Whether springdoc renders a full document
        // under MockMvc is springdoc's concern, and pinning 200 here would make this test fail
        // for a reason that has nothing to do with security.
        assertThat(statusOf("/v3/api-docs")).as("/v3/api-docs").isNotEqualTo(401);
        assertThat(statusOf("/swagger-ui/index.html")).as("swagger ui").isNotEqualTo(401);
    }

    @Test
    @DisplayName("the error dispatch is reachable, so a 500 does not come back as a 401")
    void errorDispatchIsOpen() throws Exception {
        // If /error required authentication, every unhandled exception on a public endpoint would
        // be re-dispatched into the entry point and reported as "please sign in" — the single
        // most misleading answer an API can give while it is actually broken.
        assertThat(statusOf("/error")).as("/error").isNotEqualTo(401);
    }

    private ResultActions signUp() throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", EMAIL,
                        "password", PASSWORD,
                        "fullName", "Dana Okafor"))));
    }

    private ResultActions postEmptyBody(String path) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private int statusOf(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
    }
}
