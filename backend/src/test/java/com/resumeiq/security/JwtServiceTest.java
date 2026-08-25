package com.resumeiq.security;

import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.security.JwtService.VerifiedAccessToken;
import com.resumeiq.support.TestProperties;
import com.resumeiq.user.Role;
import com.resumeiq.user.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Access tokens, from both sides.
 *
 * <p>A signing test that only proves a token round-trips is not worth writing: any implementation
 * that returns its input would pass. What matters is what the verifier <em>refuses</em>, so most
 * of this class is about rejection — an expired token, an edited payload, a token signed by
 * somebody else, a token issued by another system.
 *
 * <p>No Spring context. {@code JwtService} takes its two collaborators as constructor arguments,
 * which is what makes that possible, and is the reason it was written that way.
 */
class JwtServiceTest {

    /** Long enough for HS256, and obviously not a real key. */
    private static final String CONFIGURED_KEY = "verify-sources-test-key-".repeat(3);

    private static final String OTHER_KEY = "a-different-key-of-adequate-length-".repeat(2);

    @Test
    @DisplayName("A freshly issued token verifies back to the account that owns it")
    void roundTripsSubjectAndRole() {
        JwtService service = serviceWith(CONFIGURED_KEY, "dev");
        User user = account();

        VerifiedAccessToken verified = service.verify(service.issueAccessToken(user));

        assertThat(verified.subject()).isEqualTo(user.getPublicId());
        assertThat(verified.role()).isEqualTo(Role.USER);
        assertThat(verified.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("The subject is the public id, never the database key")
    void carriesOnlyThePublicIdentifier() {
        JwtService service = serviceWith(CONFIGURED_KEY, "dev");
        User user = account();

        String token = service.issueAccessToken(user);

        // A JWT payload is base64, not encryption: anyone holding the token can read it. So the
        // test asserts on the token text itself, which is the form an attacker would inspect.
        assertThat(token).contains(".").doesNotContain(user.getEmail());
        assertThat(service.verify(token).subject()).isEqualTo(user.getPublicId());
    }

    @Test
    @DisplayName("An expired token is rejected")
    void rejectsExpiredToken() {
        JwtService service = serviceWith(CONFIGURED_KEY, "dev");
        // Issued an hour ago with a fifteen minute life: a genuinely expired token, not a mock.
        String stale = service.issueAccessToken(
                account(), Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatExceptionOfType(ExpiredJwtException.class)
                .isThrownBy(() -> service.verify(stale));
    }

    @Test
    @DisplayName("A token signed with another key is rejected")
    void rejectsForeignSignature() {
        String tokenFromElsewhere = serviceWith(OTHER_KEY, "dev").issueAccessToken(account());
        JwtService service = serviceWith(CONFIGURED_KEY, "dev");

        assertThatThrownBy(() -> service.verify(tokenFromElsewhere))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("An edited payload is rejected")
    void rejectsTamperedPayload() {
        JwtService service = serviceWith(CONFIGURED_KEY, "dev");
        String token = service.issueAccessToken(account());

        String[] parts = token.split("\\.");
        // Flip one character of the payload. The signature no longer matches it, which is the
        // entire security property of a signed token.
        String editedPayload = parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("A") ? "B" : "A");
        String tampered = parts[0] + "." + editedPayload + "." + parts[2];

        assertThatThrownBy(() -> service.verify(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Something that is not a token at all is rejected")
    void rejectsGarbage() {
        JwtService service = serviceWith(CONFIGURED_KEY, "dev");

        assertThatThrownBy(() -> service.verify("not-a-token")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Development generates a key when none is configured")
    void generatesKeyInDevelopment() {
        JwtService service = serviceWith("", "dev");
        User user = account();

        assertThat(service.verify(service.issueAccessToken(user)).subject())
                .isEqualTo(user.getPublicId());
    }

    @Test
    @DisplayName("Two runs without a configured key cannot verify each other's tokens")
    void generatedKeysAreNotShared() {
        String fromFirstRun = serviceWith("", "dev").issueAccessToken(account());

        // The point of the warning the service logs: restarting invalidates every token, which
        // is the correct and visible consequence of having no configured key.
        assertThatThrownBy(() -> serviceWith("", "dev").verify(fromFirstRun))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Any profile other than dev refuses to start without a configured key")
    void refusesToStartWithoutKeyOutsideDevelopment() {
        assertThatThrownBy(() -> serviceWith("", "mysql"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("A key too short for HS256 is treated as no key at all")
    void rejectsShortKey() {
        // Twenty characters: enough to look configured, not enough to sign with. Accepting it
        // would mean a deployment believing it had set a secret while jjwt refused to use it.
        assertThatThrownBy(() -> serviceWith("short-key-4-testing", "mysql"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static User account() {
        return User.register("Casey@Example.com", "irrelevant-for-signing", "Casey Rivers");
    }

    private static JwtService serviceWith(String signingKey, String profile) {
        // A real Environment rather than a mock: the service asks it one question — whether the
        // dev profile is active — and a StandardEnvironment answers it exactly as Boot's would.
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        return new JwtService(propertiesWith(signingKey), environment);
    }

    private static ResumeIqProperties propertiesWith(String signingKey) {
        return TestProperties.withAuth(TestProperties.auth(signingKey));
    }
}
