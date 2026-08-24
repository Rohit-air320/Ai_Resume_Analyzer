package com.resumeiq.jobdescription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The de-duplication fingerprint. No database involved — this is a pure function, and the
 * behaviour that matters is which pastes it treats as the same posting.
 */
class JobDescriptionHashTest {

    private static final String POSTING = """
            Backend Engineer
            We need Java, Spring Boot and MySQL.
            """;

    @Test
    @DisplayName("the same text hashes to the same 64-character value")
    void isStableAndFixedWidth() {
        String hash = JobDescription.hashOf(POSTING);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(JobDescription.hashOf(POSTING)).isEqualTo(hash);
    }

    @Test
    @DisplayName("re-pasting the same posting matches despite whitespace and case changes")
    void ignoresWhitespaceAndCase() {
        // This is the actual user behaviour being served: paste, tweak the resume, paste again.
        // The browser hands back different line breaks and indentation each time.
        String reformatted = "  BACKEND ENGINEER\r\n\r\n  We need Java,   Spring Boot and MySQL.  ";

        assertThat(JobDescription.hashOf(reformatted)).isEqualTo(JobDescription.hashOf(POSTING));
    }

    @Test
    @DisplayName("a different posting hashes differently")
    void distinguishesRealChanges() {
        assertThat(JobDescription.hashOf(POSTING + "Docker and AWS are required."))
                .isNotEqualTo(JobDescription.hashOf(POSTING));
    }

    @Test
    @DisplayName("null and blank text hash to the same empty-input value instead of failing")
    void handlesMissingText() {
        assertThat(JobDescription.hashOf(null))
                .isEqualTo(JobDescription.hashOf("   \n  "))
                .hasSize(64);
    }
}
