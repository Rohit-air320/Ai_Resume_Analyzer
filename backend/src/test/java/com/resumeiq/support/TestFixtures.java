package com.resumeiq.support;

import com.resumeiq.analysis.Analysis;
import com.resumeiq.analysis.AnalysisSkill;
import com.resumeiq.analysis.AnalysisStatus;
import com.resumeiq.analysis.SkillImportance;
import com.resumeiq.analysis.SkillStatus;
import com.resumeiq.jobdescription.JobDescription;
import com.resumeiq.recommendation.Priority;
import com.resumeiq.recommendation.Recommendation;
import com.resumeiq.recommendation.RecommendationType;
import com.resumeiq.resume.Resume;
import com.resumeiq.resume.ResumeStatus;
import com.resumeiq.user.User;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Valid entities for tests, built one obvious way.
 *
 * <p>Every entity here satisfies its own non-null constraints, so a test that cares about one
 * field can set that field and ignore the other twelve. When a test needs an invalid object it
 * builds it inline — the fixture is the happy path by definition.
 */
public final class TestFixtures {

    /** Shape of a real bcrypt hash. Never a plain-text password, not even in tests. */
    public static final String PASSWORD_HASH =
            "$2a$10$Xn8Q1ZBb2R2sT3uV4wX5yeK6L7mN8oP9qR0sT1uV2wX3yZ4aB5cD6";

    private TestFixtures() {
    }

    public static User user(String email) {
        return User.register(email, PASSWORD_HASH, "Test Person");
    }

    public static Resume resume(User owner, String label) {
        return Resume.builder()
                .user(owner)
                .label(label)
                .originalFilename(label.replace(' ', '_') + ".pdf")
                // Server-generated, unique across all users — never the uploaded name.
                .storageKey(UUID.randomUUID() + ".pdf")
                .contentType("application/pdf")
                .fileSizeBytes(184_320L)
                .pageCount(2)
                .wordCount(612)
                .extractedText("Java, Spring Boot and MySQL across three internships.")
                .status(ResumeStatus.TEXT_EXTRACTED)
                .build();
    }

    public static JobDescription jobDescription(User owner, String title, String rawText) {
        return JobDescription.builder()
                .user(owner)
                .title(title)
                .company("Northwind")
                .rawText(rawText)
                .contentHash(JobDescription.hashOf(rawText))
                .build();
    }

    /** A finished run. Built as {@code QUEUED} and completed through the domain method. */
    public static Analysis completedAnalysis(User owner, Resume resume, JobDescription jd, int overall) {
        Analysis analysis = Analysis.builder()
                .user(owner)
                .resume(resume)
                .jobDescription(jd)
                .status(AnalysisStatus.QUEUED)
                .overallScore(overall)
                .atsScore(overall + 2)
                .jobMatchScore(overall - 3)
                .skillsMatchScore(overall)
                .keywordScore(overall - 1)
                .experienceScore(overall + 1)
                .overallFeedback("Strong on backend, thin on cloud.")
                .aiModel("test-model-1")
                .analyzerVersion("p2")
                .processingMs(1_400)
                .build();
        analysis.markCompleted(Instant.now());
        return analysis;
    }

    public static Analysis failedAnalysis(User owner, Resume resume, JobDescription jd) {
        Analysis analysis = Analysis.builder()
                .user(owner)
                .resume(resume)
                .jobDescription(jd)
                .status(AnalysisStatus.PROCESSING)
                .build();
        analysis.markFailed("The analyzer did not respond. Try again.", Instant.now());
        return analysis;
    }

    public static AnalysisSkill analysisSkill(String rawName, SkillStatus status, SkillImportance importance) {
        return AnalysisSkill.builder()
                .rawName(rawName)
                .status(status)
                .importance(importance)
                .evidence(status == SkillStatus.MISSING
                        ? null
                        : "Named in the payments migration bullet.")
                .build();
    }

    public static Recommendation recommendation(RecommendationType type, String title, int displayOrder) {
        return Recommendation.builder()
                .type(type)
                .title(title)
                .detail("Name the system and the measured outcome in this bullet.")
                .priority(Priority.HIGH)
                .displayOrder(displayOrder)
                .build();
    }

    /**
     * Moves a row's {@code created_at} into the past so ordering assertions are deterministic.
     *
     * <p>{@code createdAt} is written by a lifecycle callback and has no setter, and two rows
     * inserted in the same test can land inside the same clock tick — which would make any
     * "newest first" assertion pass or fail at random. Native SQL because the column is mapped
     * {@code updatable = false}.
     *
     * <p>The table name is a literal from the calling test, never input; and Hibernate does not
     * flush before a native query, so callers flush first and clear afterwards.
     */
    public static void backdate(EntityManager em, String table, Long id, Instant when) {
        em.createNativeQuery("update " + table + " set created_at = ?1 where id = ?2")
                .setParameter(1, Timestamp.from(when))
                .setParameter(2, id)
                .executeUpdate();
    }
}
