package com.resumeiq.resume;

import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestFixtures;
import com.resumeiq.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resume reads, with the two rules from the spec that this repository is responsible for:
 * a user only ever reaches their own resumes, and no list query touches the extracted text.
 */
@RepositoryTest
class ResumeRepositoryTest {

    @Autowired
    private ResumeRepository resumes;

    @Autowired
    private TestEntityManager em;

    private User persistUser(String email) {
        return em.persistFlushFind(TestFixtures.user(email));
    }

    @Test
    @DisplayName("the list projection cannot expose the resume text")
    void summaryProjectionHasNoAccessForTheText() {
        List<String> accessors = Arrays.stream(ResumeSummary.class.getDeclaredMethods())
                .map(Method::getName)
                .sorted()
                .toList();

        // A guard against the easiest possible regression: adding getExtractedText() to this
        // interface would put resume contents into every list response, silently. The storage
        // key is named here too — it is an internal path component, not something a list needs.
        assertThat(accessors).doesNotContain("getExtractedText", "getStorageKey", "getUser");

        // Pinned exactly rather than pattern-matched. An earlier version of this test asserted
        // that no accessor name contained "text", which fails on getExtractionError: lowercased,
        // "getextractionerror" contains "text" inside "ge-text-raction". Naming the ten allowed
        // accessors makes widening this projection a deliberate act with a failing test attached.
        assertThat(accessors).containsExactly(
                "getContentType",
                "getCreatedAt",
                "getExtractionError",
                "getFileSizeBytes",
                "getLabel",
                "getOriginalFilename",
                "getPageCount",
                "getPublicId",
                "getStatus",
                "getWordCount");
    }

    @Test
    @DisplayName("summaries come back newest first with the display fields filled in")
    void listsSummariesNewestFirst() {
        User owner = persistUser("summaries@example.com");
        Resume older = em.persistAndFlush(TestFixtures.resume(owner, "Older CV"));
        Resume newer = em.persistAndFlush(TestFixtures.resume(owner, "Newer CV"));

        em.flush();
        TestFixtures.backdate(em.getEntityManager(), "resumes", older.getId(),
                Instant.now().minus(2, ChronoUnit.DAYS));
        em.clear();

        List<ResumeSummary> summaries = resumes.findSummariesByUserIdOrderByCreatedAtDesc(owner.getId());

        assertThat(summaries).extracting(ResumeSummary::getLabel)
                .containsExactly("Newer CV", "Older CV");

        ResumeSummary newest = summaries.get(0);
        assertThat(newest.getPublicId()).isEqualTo(newer.getPublicId());
        assertThat(newest.getStatus()).isEqualTo(ResumeStatus.TEXT_EXTRACTED);
        assertThat(newest.getWordCount()).isEqualTo(612);
        assertThat(newest.getFileSizeBytes()).isEqualTo(184_320L);
        assertThat(newest.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("another user's resume is not found, rather than found and refused")
    void lookupIsScopedToTheOwner() {
        User owner = persistUser("owner@example.com");
        User stranger = persistUser("stranger@example.com");
        Resume resume = em.persistFlushFind(TestFixtures.resume(owner, "Private CV"));
        em.clear();

        assertThat(resumes.findByPublicIdAndUserId(resume.getPublicId(), owner.getId())).isPresent();

        // Empty, not a populated result the service then has to reject: the caller cannot even
        // learn that this public id exists.
        assertThat(resumes.findByPublicIdAndUserId(resume.getPublicId(), stranger.getId())).isEmpty();
        assertThat(resumes.findByPublicIdAndUserId(UUID.randomUUID(), owner.getId())).isEmpty();
        assertThat(resumes.countByUserId(stranger.getId())).isZero();
        assertThat(resumes.countByUserId(owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("delete only removes the caller's own resume")
    void deleteIsScopedToTheOwner() {
        User owner = persistUser("delete-owner@example.com");
        User stranger = persistUser("delete-stranger@example.com");
        Resume resume = em.persistFlushFind(TestFixtures.resume(owner, "Deletable CV"));
        UUID publicId = resume.getPublicId();
        em.clear();

        assertThat(resumes.deleteByPublicIdAndUserId(publicId, stranger.getId())).isZero();
        em.clear();
        assertThat(resumes.findByPublicIdAndUserId(publicId, owner.getId())).isPresent();

        assertThat(resumes.deleteByPublicIdAndUserId(publicId, owner.getId())).isEqualTo(1);
        em.flush();
        em.clear();
        assertThat(resumes.findByPublicIdAndUserId(publicId, owner.getId())).isEmpty();
    }

    @Test
    @DisplayName("a storage key is unique across all users")
    void storageKeysAreCheckedGlobally() {
        User owner = persistUser("storage@example.com");
        Resume resume = em.persistFlushFind(TestFixtures.resume(owner, "Stored CV"));
        em.clear();

        // Checked without a user id on purpose: a collision between two users' files is exactly
        // the case that matters, and scoping this query by owner would miss it.
        assertThat(resumes.existsByStorageKey(resume.getStorageKey())).isTrue();
        assertThat(resumes.existsByStorageKey("never-written.pdf")).isFalse();
    }

    @Test
    @DisplayName("a resume without extracted text is not analysable")
    void analysabilityFollowsExtraction() {
        User owner = persistUser("analysable@example.com");
        Resume ready = TestFixtures.resume(owner, "Ready CV");
        Resume pending = TestFixtures.resume(owner, "Pending CV");
        pending.setStatus(ResumeStatus.UPLOADED);
        pending.setExtractedText(null);

        assertThat(ready.isAnalysable()).isTrue();
        assertThat(pending.isAnalysable()).isFalse();

        ready.setExtractedText("   ");
        assertThat(ready.isAnalysable()).isFalse();
    }
}
