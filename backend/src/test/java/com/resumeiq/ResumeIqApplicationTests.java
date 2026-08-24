package com.resumeiq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Guards the wiring itself: if a bean definition, a required property, or a profile is
 * broken, this fails before any feature test runs. Cheap, and it catches the majority of
 * configuration mistakes.
 *
 * <p>Seeding is switched off here for one specific reason. The dev profile keeps H2 alive for the
 * whole JVM ({@code DB_CLOSE_DELAY=-1}), so every test context in the build shares one database.
 * This test's {@code ApplicationRunner} runs outside any test transaction, so its writes commit
 * and stay committed — the skill catalogue would appear, already populated, inside repository
 * slices that never asked for it. Those tests are written not to care, but a shared database that
 * quietly changes depending on test order is how a suite starts failing only on CI.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "resumeiq.seed.skills=false")
class ResumeIqApplicationTests {

    @Test
    void applicationContextLoads() {
        // Success is the context starting with the dev profile and validated properties.
    }
}
