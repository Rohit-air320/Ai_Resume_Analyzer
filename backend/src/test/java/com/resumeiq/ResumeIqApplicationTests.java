package com.resumeiq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the wiring itself: if a bean definition, a required property, or a profile is
 * broken, this fails before any feature test runs. Cheap, and it catches the majority of
 * configuration mistakes.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ResumeIqApplicationTests {

    @Test
    void applicationContextLoads() {
        // Success is the context starting with the dev profile and validated properties.
    }
}
