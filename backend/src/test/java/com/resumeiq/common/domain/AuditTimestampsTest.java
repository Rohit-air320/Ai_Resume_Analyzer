package com.resumeiq.common.domain;

import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestFixtures;
import com.resumeiq.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit timestamps every entity inherits, and the one property that is easy to lose: an
 * entity in memory must hold the same instant as the row it was written from.
 *
 * <p>This is here because the alternative is a bug that only appears on some machines. A
 * timestamp column keeps microseconds; {@code Instant.now()} on Windows ticks in hundreds of
 * nanoseconds. Save an entity with the finer value and the object and its row differ from the
 * moment of the insert — so a create response and a later read disagree about {@code createdAt},
 * and a test comparing the two passes on Linux and fails on Windows. It did, at the Phase 5
 * gate. {@link Timestamps} truncates at the source; these tests are what keep it doing so.
 *
 * <p>{@link User} stands in for every entity: the callbacks live on {@link BaseEntity}, so what
 * holds for one holds for all of them.
 */
@RepositoryTest
class AuditTimestampsTest {

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("a saved entity holds exactly the instant the database holds")
    void survivesARoundTripUnchanged() {
        User saved = em.persistFlushFind(TestFixtures.user("audit@example.com"));
        Instant createdInMemory = saved.getCreatedAt();
        Instant updatedInMemory = saved.getUpdatedAt();

        // clear(), so the reload is a real select rather than the same instance from the
        // persistence context — which would compare the value to itself and prove nothing.
        em.flush();
        em.clear();
        User reloaded = em.find(User.class, saved.getId());

        assertThat(reloaded.getCreatedAt())
                .as("the row the insert produced must equal the object it came from")
                .isEqualTo(createdInMemory);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedInMemory);
    }

    @Test
    @DisplayName("insert timestamps carry no precision the column would drop")
    void isSetAtStoredPrecision() {
        User saved = em.persistFlushFind(TestFixtures.user("precision@example.com"));

        assertThat(saved.getCreatedAt())
                .isEqualTo(saved.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
        assertThat(saved.getUpdatedAt())
                .isEqualTo(saved.getUpdatedAt().truncatedTo(ChronoUnit.MICROS));
        // An insert sets both from one instant, so a fresh row can be recognised by the two
        // being identical — which is also what makes updatedAt meaningful later.
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("an update moves updatedAt, still at stored precision, and leaves createdAt alone")
    void updateKeepsCreatedAtAndTruncatesUpdatedAt() {
        User saved = em.persistFlushFind(TestFixtures.user("touched@example.com"));
        Instant createdAt = saved.getCreatedAt();

        saved.setLastLoginAt(Timestamps.now());
        em.flush();
        em.clear();
        User reloaded = em.find(User.class, saved.getId());

        assertThat(reloaded.getCreatedAt())
                .as("created_at is mapped updatable = false, and a moving one would reorder lists")
                .isEqualTo(createdAt);
        assertThat(reloaded.getUpdatedAt())
                .isEqualTo(reloaded.getUpdatedAt().truncatedTo(ChronoUnit.MICROS));
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(reloaded.getLastLoginAt())
                .isEqualTo(reloaded.getLastLoginAt().truncatedTo(ChronoUnit.MICROS));
    }
}
