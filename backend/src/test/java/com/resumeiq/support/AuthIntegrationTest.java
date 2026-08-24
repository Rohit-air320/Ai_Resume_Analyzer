package com.resumeiq.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A full-context HTTP test: real filter chain, real database, real BCrypt.
 *
 * <p>Authentication is one of the few things a slice test cannot honestly cover. The behaviour
 * that matters — a cookie set with the right attributes, a token accepted on the next request, a
 * closed endpoint answering 401 in the project's error envelope — is produced by the filter
 * chain, the controller and the database acting together. Mock any one of them out and the test
 * stops describing what a browser will see.
 *
 * <p>Three property overrides, each for a specific reason:
 *
 * <p><strong>Its own database.</strong> The dev profile keeps H2 alive for the whole JVM
 * ({@code DB_CLOSE_DELAY=-1}), so every test context in the build otherwise shares one schema.
 * These tests sign people up through HTTP, which commits — there is no test transaction wrapping
 * a MockMvc call to roll back. Those rows would then be visible inside repository slices that
 * never asked for them. A separate URL keeps the mess local.
 *
 * <p><strong>Seeding off.</strong> Same reason, from the other direction: nothing here needs the
 * skill catalogue, and a startup writer that commits into a shared database makes test order
 * matter.
 *
 * <p><strong>BCrypt at cost 4.</strong> Production uses 12, which is a quarter of a second per
 * hash — correct there, and about a minute across a suite that signs in this often. The cost is
 * configuration precisely so this line can exist.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:resumeiq-auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "resumeiq.seed.skills=false",
        "resumeiq.auth.bcrypt-strength=4",
})
public @interface AuthIntegrationTest {
}
