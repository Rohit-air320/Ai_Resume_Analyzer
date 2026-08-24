package com.resumeiq.resume;

import com.resumeiq.support.TestFixtures;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test in this suite that deliberately runs <em>outside</em> a transaction.
 *
 * <p>Every other repository test is a {@code @DataJpaTest}, which wraps each test in a transaction
 * and rolls it back. That is fast and clean, and it hides one specific bug completely. Spring Data
 * generates its repository implementation as {@code @Transactional(readOnly = true)}, which leaves
 * Hibernate in {@code FlushMode.MANUAL}; a derived {@code deleteBy…} without its own writable
 * transaction loads the rows, removes them from the session, never flushes, and still returns a
 * count. Inside a test-managed transaction the removal is visible through the same session, so the
 * assertion passes while production would delete nothing.
 *
 * <p>So this class has no {@code @Transactional} — deliberately, and it must stay that way. Each
 * repository call commits on its own, and the read afterwards happens in a new session, which is
 * the only arrangement that can tell "deleted" apart from "removed from a session that was thrown
 * away".
 *
 * <p>It gets its own in-memory database rather than sharing the dev one, because its writes commit
 * and a shared database that changes depending on test order is how a suite starts failing only
 * on CI.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "resumeiq.seed.skills=false",
        "spring.datasource.url=jdbc:h2:mem:resumeiq-commit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class ResumeDeleteCommitsTest {

    @Autowired
    private ResumeRepository resumes;

    @Autowired
    private UserRepository users;

    @Test
    @DisplayName("a derived delete actually commits, and only for the owner")
    void deletesTheRowForRealAndOnlyForItsOwner() {
        User owner = users.save(TestFixtures.user("commit-owner@example.com"));
        User stranger = users.save(TestFixtures.user("commit-stranger@example.com"));
        Resume resume = resumes.save(TestFixtures.resume(owner, "Committed"));

        // The ownership filter is in the query, so a stranger's delete matches nothing.
        assertThat(resumes.deleteByPublicIdAndUserId(resume.getPublicId(), stranger.getId()))
                .isZero();
        assertThat(resumes.findByPublicIdAndUserId(resume.getPublicId(), owner.getId()))
                .isPresent();

        assertThat(resumes.deleteByPublicIdAndUserId(resume.getPublicId(), owner.getId()))
                .isEqualTo(1);

        // Read back in a fresh session. This is the assertion that fails if the repository
        // method loses its @Transactional: the count above would still say one row deleted.
        assertThat(resumes.findByPublicIdAndUserId(resume.getPublicId(), owner.getId()))
                .isEmpty();
        assertThat(resumes.countByUserId(owner.getId())).isZero();

        users.deleteAll();
    }
}
