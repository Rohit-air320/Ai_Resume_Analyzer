package com.resumeiq.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.support.AnalysisIntegrationTest;
import com.resumeiq.support.ApiClient;
import com.resumeiq.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET} and {@code PUT /api/profile}.
 *
 * <p>Two endpoints with no path variable, which is the interesting thing about them: the id is the
 * token, so there is nothing to tamper with and no ownership check to forget. The tests that matter
 * here are therefore not about authorisation but about what the request is unable to say —
 * {@link #ignoresFieldsThatAreNotTheUsersToChange()} sends an email, a role and a password hash and
 * checks that all three are ignored, because "update profile" is exactly the endpoint that quietly
 * grows a privilege escalation when somebody adds a field to a DTO.
 *
 * <p>It reuses {@link AnalysisIntegrationTest} rather than declaring its own properties so it shares a
 * cached Spring context with the analysis tests instead of starting a second application.
 */
@AnalysisIntegrationTest
class ProfileApiTest {

    private static final String EMAIL = "priya@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;

    @BeforeEach
    void clearState() {
        api = new ApiClient(mockMvc, json);
        DatabaseCleaner.clear(jdbc);
    }

    @Test
    @DisplayName("returns the signed-in user's profile and nothing else about them")
    void returnsTheProfile() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Priya Raman"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.memberSince").exists())
                .andExpect(jsonPath("$.lastLoginAt").exists())

                // The two optional fields are omitted until they are set, which is how the UI can tell
                // "not answered" from an answer.
                .andExpect(jsonPath("$.targetRole").doesNotExist())
                .andExpect(jsonPath("$.experienceLevel").doesNotExist())

                // The response is an explicit record, so this is not a test of an annotation somebody
                // could remove — but it is the assertion that would catch a future switch to returning
                // the entity, which is one @JsonIgnore away from putting a hash on the wire.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("updates the three editable fields and keeps them")
    void updatesTheEditableFields() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");

        update(token, "Priya R. Raman", "Backend Engineer", "SENIOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Priya R. Raman"))
                .andExpect(jsonPath("$.targetRole").value("Backend Engineer"))
                .andExpect(jsonPath("$.experienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$.email").value(EMAIL));

        // Read back through a second request, because a PUT that returns the object it was handed
        // looks identical to one that saved it.
        JsonNode reread = api.getJson(token, "/api/profile");
        assertThat(reread.get("fullName").asText()).isEqualTo("Priya R. Raman");
        assertThat(reread.get("targetRole").asText()).isEqualTo("Backend Engineer");
        assertThat(reread.get("experienceLevel").asText()).isEqualTo("SENIOR");
    }

    @Test
    @DisplayName("a field left out of the request is cleared, because PUT replaces")
    void omittingAnOptionalFieldClearsIt() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");
        update(token, "Priya Raman", "Backend Engineer", "SENIOR").andExpect(status().isOk());

        update(token, "Priya Raman", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetRole").doesNotExist())
                .andExpect(jsonPath("$.experienceLevel").doesNotExist());

        // This is the trade a PUT makes and it is deliberate: a record cannot tell "leave it alone"
        // from "I no longer have one", since both arrive as null. Replacement is the honest reading of
        // the two, and the form sends every field anyway. A PATCH that could express the difference
        // would need a wrapper type per field, and the endpoint is three fields wide.
        assertThat(api.getJson(token, "/api/profile").has("targetRole")).isFalse();
    }

    @Test
    @DisplayName("a blank target role is stored as none rather than as an empty string")
    void blankTargetRoleBecomesNone() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");

        update(token, "  Priya Raman  ", "   ", null)
                .andExpect(status().isOk())
                // Trimmed on the way in, so a name pasted with a trailing space does not sort oddly or
                // render with a gap.
                .andExpect(jsonPath("$.fullName").value("Priya Raman"))
                .andExpect(jsonPath("$.targetRole").doesNotExist());

        Integer blanks = jdbc.queryForObject(
                "select count(*) from users where target_role = ''", Integer.class);
        assertThat(blanks).as("'' and null in one column is two ways to say the same thing").isZero();
    }

    @Test
    @DisplayName("the new name survives a fresh sign-in")
    void survivesANewToken() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");
        update(token, "Priya R. Raman", null, null).andExpect(status().isOk());

        // A new token carries a new copy of the name in its claims. Reading the profile from the token
        // rather than from the row would show the old one here — which is why the service loads the
        // user instead of mapping the principal it was given.
        String freshToken = api.signIn(EMAIL);
        assertThat(api.getJson(freshToken, "/api/profile").get("fullName").asText())
                .isEqualTo("Priya R. Raman");
    }

    @Test
    @DisplayName("a blank name is a field error")
    void rejectsABlankName() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");

        update(token, "   ", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(api.getJson(token, "/api/profile").get("fullName").asText())
                .isEqualTo("Priya Raman");
    }

    @Test
    @DisplayName("an over-long name and an over-long target role are field errors, not truncations")
    void rejectsOverLongValues() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");

        update(token, "n".repeat(121), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")));

        // Refused rather than cut. Silently shortening somebody's own name is worse than telling them
        // it is too long: they would find out from a rendered page later, if at all.
        update(token, "Priya Raman", "r".repeat(121), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("targetRole")));
    }

    @Test
    @DisplayName("an experience level that is not one of the five is a bad request")
    void rejectsAnUnknownExperienceLevel() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");

        update(token, "Priya Raman", null, "PRINCIPAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("email, role and password cannot be changed through this endpoint")
    void ignoresFieldsThatAreNotTheUsersToChange() throws Exception {
        String token = api.signUp(EMAIL, "Priya Raman");
        String hashBefore = passwordHash();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Priya Raman");
        body.put("email", "attacker@example.test");
        body.put("role", "ADMIN");
        body.put("password", "something-else-entirely");
        body.put("passwordHash", "$2a$04$notarealhash");
        body.put("id", "11111111-1111-1111-1111-111111111111");

        mockMvc.perform(put("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"));

        // The request record has three components and unknown properties are ignored, so none of the
        // extra keys had anywhere to land. That is the point of a request DTO that is not the entity:
        // the endpoint cannot express a privilege change, so it cannot be talked into performing one.
        assertThat(passwordHash()).isEqualTo(hashBefore);
        Integer admins = jdbc.queryForObject(
                "select count(*) from users where role = 'ADMIN'", Integer.class);
        assertThat(admins).isZero();

        // And the old password still works, which is the version of that assertion a user would care
        // about.
        assertThat(api.signIn(EMAIL)).isNotBlank();
    }

    @Test
    @DisplayName("each account sees its own profile")
    void keepsProfilesSeparate() throws Exception {
        String priya = api.signUp(EMAIL, "Priya Raman");
        String other = api.signUp("someone@example.test", "Someone Else");
        update(priya, "Priya R. Raman", "Backend Engineer", "SENIOR").andExpect(status().isOk());

        // One endpoint, two callers, no id in the URL — so the only thing distinguishing these two
        // responses is the token, which is the whole design.
        assertThat(api.getJson(other, "/api/profile").get("fullName").asText())
                .isEqualTo("Someone Else");
        assertThat(api.getJson(other, "/api/profile").has("targetRole")).isFalse();
        assertThat(api.getJson(priya, "/api/profile").get("fullName").asText())
                .isEqualTo("Priya R. Raman");
    }

    @Test
    @DisplayName("both methods are closed to anonymous callers")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Nobody"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A profile update.
     *
     * <p>A null argument means "not set". Whether Jackson writes it as an explicit {@code null} or
     * omits the key entirely does not matter here — the record receives null either way, which is the
     * replacement semantics {@link #omittingAnOptionalFieldClearsIt()} pins down.
     */
    private ResultActions update(String token, String fullName, String targetRole, String level)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", fullName);
        body.put("targetRole", targetRole);
        body.put("experienceLevel", level);

        return mockMvc.perform(put("/api/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private String passwordHash() {
        return jdbc.queryForObject("select password_hash from users where email = ?",
                String.class, EMAIL);
    }
}
