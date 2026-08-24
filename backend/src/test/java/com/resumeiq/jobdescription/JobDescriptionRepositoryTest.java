package com.resumeiq.jobdescription;

import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestFixtures;
import com.resumeiq.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Job description storage, and the de-duplication rule: one posting per user, not one row per
 * paste — but two users pasting the same posting must still get their own rows.
 */
@RepositoryTest
class JobDescriptionRepositoryTest {

    private static final String POSTING = "Backend Engineer. Java, Spring Boot, MySQL, Docker.";

    @Autowired
    private JobDescriptionRepository jobDescriptions;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("the same posting pasted twice by one user is rejected by the database")
    void oneUserCannotStoreTheSamePostingTwice() {
        User owner = em.persistFlushFind(TestFixtures.user("jd-owner@example.com"));
        jobDescriptions.saveAndFlush(TestFixtures.jobDescription(owner, "Backend Engineer", POSTING));
        em.clear();

        // The service looks the hash up first — this constraint is the backstop for two requests
        // arriving at once.
        assertThatThrownBy(() -> jobDescriptions.saveAndFlush(
                TestFixtures.jobDescription(owner, "Backend Engineer (again)", POSTING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("two users can each hold the same posting")
    void theConstraintIsScopedPerUser() {
        User first = em.persistFlushFind(TestFixtures.user("jd-first@example.com"));
        User second = em.persistFlushFind(TestFixtures.user("jd-second@example.com"));

        jobDescriptions.saveAndFlush(TestFixtures.jobDescription(first, "Backend Engineer", POSTING));
        jobDescriptions.saveAndFlush(TestFixtures.jobDescription(second, "Backend Engineer", POSTING));
        em.clear();

        // A globally unique hash would be cheaper on storage and would let either user observe
        // that the other had applied to the same job.
        assertThat(jobDescriptions.countByUserId(first.getId())).isEqualTo(1);
        assertThat(jobDescriptions.countByUserId(second.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("an existing posting is found by its hash, scoped to its owner")
    void findsByHashForReuse() {
        User owner = em.persistFlushFind(TestFixtures.user("jd-hash@example.com"));
        User stranger = em.persistFlushFind(TestFixtures.user("jd-hash-other@example.com"));
        JobDescription saved = jobDescriptions.saveAndFlush(
                TestFixtures.jobDescription(owner, "Backend Engineer", POSTING));
        em.clear();

        String hash = JobDescription.hashOf(POSTING);
        assertThat(jobDescriptions.findByUserIdAndContentHash(owner.getId(), hash))
                .get()
                .extracting(JobDescription::getPublicId)
                .isEqualTo(saved.getPublicId());
        assertThat(jobDescriptions.findByUserIdAndContentHash(stranger.getId(), hash)).isEmpty();
    }

    @Test
    @DisplayName("the list projection returns titles without the posting text")
    void summariesOmitTheRawText() {
        User owner = em.persistFlushFind(TestFixtures.user("jd-summary@example.com"));
        jobDescriptions.saveAndFlush(TestFixtures.jobDescription(owner, "Backend Engineer", POSTING));
        em.clear();

        assertThat(jobDescriptions.findSummariesByUserIdOrderByCreatedAtDesc(owner.getId()))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getTitle()).isEqualTo("Backend Engineer");
                    assertThat(summary.getCompany()).isEqualTo("Northwind");
                    assertThat(summary.getCreatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("delete is scoped to the owner")
    void deleteIsScopedToTheOwner() {
        User owner = em.persistFlushFind(TestFixtures.user("jd-delete@example.com"));
        User stranger = em.persistFlushFind(TestFixtures.user("jd-delete-other@example.com"));
        JobDescription saved = jobDescriptions.saveAndFlush(
                TestFixtures.jobDescription(owner, "Backend Engineer", POSTING));
        em.clear();

        assertThat(jobDescriptions.deleteByPublicIdAndUserId(saved.getPublicId(), stranger.getId()))
                .isZero();
        assertThat(jobDescriptions.deleteByPublicIdAndUserId(saved.getPublicId(), owner.getId()))
                .isEqualTo(1);
    }
}
