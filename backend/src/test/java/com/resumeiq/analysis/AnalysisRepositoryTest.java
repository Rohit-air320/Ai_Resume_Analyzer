package com.resumeiq.analysis;

import com.resumeiq.jobdescription.JobDescription;
import com.resumeiq.recommendation.RecommendationType;
import com.resumeiq.resume.Resume;
import com.resumeiq.resume.ResumeRepository;
import com.resumeiq.skill.Skill;
import com.resumeiq.skill.SkillCategory;
import com.resumeiq.skill.SkillRepository;
import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestFixtures;
import com.resumeiq.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The analysis aggregate: how it is written, how the results page reads it back, how the
 * dashboard aggregates it, and what a delete takes with it.
 *
 * <p>Every query here is filtered by owner, and every test checks the negative case as well as
 * the positive one — an authorisation rule that is only tested from the owner's side is not
 * tested at all.
 */
@RepositoryTest
class AnalysisRepositoryTest {

    @Autowired
    private AnalysisRepository analyses;

    @Autowired
    private ResumeRepository resumes;

    @Autowired
    private SkillRepository skills;

    @Autowired
    private TestEntityManager em;

    private User owner;
    private User stranger;
    private Resume resume;
    private JobDescription jobDescription;

    @BeforeEach
    void setUp() {
        owner = em.persistFlushFind(TestFixtures.user("analysis-owner@example.com"));
        stranger = em.persistFlushFind(TestFixtures.user("analysis-stranger@example.com"));
        resume = em.persistFlushFind(TestFixtures.resume(owner, "Owner CV"));
        jobDescription = em.persistFlushFind(
                TestFixtures.jobDescription(owner, "Backend Engineer", "Java, Spring Boot, Docker."));
    }

    private Skill dockerSkill() {
        return skills.findBySlug("docker").orElseGet(() -> skills.saveAndFlush(Skill.builder()
                .slug("docker")
                .displayName("Docker")
                .category(SkillCategory.DEVOPS)
                .build()));
    }

    private Analysis fullAnalysis(int overallScore) {
        Analysis analysis = TestFixtures.completedAnalysis(owner, resume, jobDescription, overallScore);
        analysis.addSkill(TestFixtures.analysisSkill("Java", SkillStatus.STRONG, SkillImportance.CRITICAL));
        analysis.addSkill(TestFixtures.analysisSkill("Spring Boot", SkillStatus.PARTIAL, SkillImportance.IMPORTANT));

        AnalysisSkill missing = TestFixtures.analysisSkill("Docker", SkillStatus.MISSING, SkillImportance.CRITICAL);
        missing.setSkill(dockerSkill());
        analysis.addSkill(missing);

        analysis.addRecommendation(
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Quantify the migration", 0));
        analysis.addRecommendation(
                TestFixtures.recommendation(RecommendationType.LEARNING, "Learn container basics", 1));

        analysis.addKeyword(new AnalysisKeyword(KeywordKind.MATCHED, "Spring Boot", null));
        analysis.addKeyword(new AnalysisKeyword(KeywordKind.SUGGESTED, "containerised deployment",
                "In the payments bullet, where you already describe the release process."));
        analysis.addSectionAssessment(new SectionAssessment(ResumeSection.EXPERIENCE, 68,
                "Three of five roles describe duties rather than outcomes."));

        return analysis;
    }

    private long nativeCount(String table, String column, Object value) {
        Object result = em.getEntityManager()
                .createNativeQuery("select count(*) from " + table + " where " + column + " = ?1")
                .setParameter(1, value)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    @Test
    @DisplayName("the aggregate is written and read back in one query")
    void savesAndFetchesTheWholeAggregate() {
        Analysis saved = analyses.saveAndFlush(fullAnalysis(78));
        UUID publicId = saved.getPublicId();
        em.flush();
        em.clear();

        Analysis found = analyses.findDetailByPublicIdAndUserId(publicId, owner.getId()).orElseThrow();
        em.getEntityManager().detach(found);

        // Detached: if the entity graph had not fetched these, every assertion below would throw
        // LazyInitializationException — which is exactly what the results page would do in
        // production with open-in-view disabled.
        assertThat(found.getSkills()).hasSize(3);
        assertThat(found.getRecommendations()).hasSize(2);
        assertThat(found.getSkills())
                .filteredOn(skill -> skill.getSkill() != null)
                .singleElement()
                .satisfies(skill -> assertThat(skill.label()).isEqualTo("Docker"));
        assertThat(found.getSkills())
                .filteredOn(skill -> skill.getSkill() == null)
                .extracting(AnalysisSkill::label)
                .containsExactlyInAnyOrder("Java", "Spring Boot");
    }

    @Test
    @DisplayName("element collections load inside the transaction")
    void keywordsAndSectionScoresAreReadable() {
        Analysis saved = analyses.saveAndFlush(fullAnalysis(78));
        em.flush();
        em.clear();

        Analysis found = analyses.findByPublicIdAndUserId(saved.getPublicId(), owner.getId()).orElseThrow();

        assertThat(found.getKeywords()).hasSize(2);
        assertThat(found.getKeywords())
                .filteredOn(keyword -> keyword.getKind() == KeywordKind.SUGGESTED)
                .singleElement()
                // The advice never travels without the place it belongs — no bare keyword lists.
                .satisfies(keyword -> assertThat(keyword.getPlacement()).isNotBlank());
        assertThat(found.getSectionAssessments())
                .singleElement()
                .satisfies(section -> {
                    assertThat(section.getSection()).isEqualTo(ResumeSection.EXPERIENCE);
                    assertThat(section.getScore()).isEqualTo(68);
                });
    }

    @Test
    @DisplayName("the collections cannot be modified through their getters")
    void collectionsAreExposedAsViews() {
        Analysis analysis = fullAnalysis(78);

        // Adding through the getter would set one side of the relationship only, and the child
        // would insert with a null analysis_id.
        assertThat(analysis.getSkills()).isUnmodifiable();
        assertThat(analysis.getRecommendations()).isUnmodifiable();
        assertThat(analysis.getKeywords()).isUnmodifiable();
        assertThat(analysis.getSectionAssessments()).isUnmodifiable();
    }

    @Test
    @DisplayName("another user cannot reach an analysis by its public id")
    void lookupsAreScopedToTheOwner() {
        Analysis saved = analyses.saveAndFlush(fullAnalysis(78));
        em.flush();
        em.clear();

        assertThat(analyses.findByPublicIdAndUserId(saved.getPublicId(), stranger.getId())).isEmpty();
        assertThat(analyses.findDetailByPublicIdAndUserId(saved.getPublicId(), stranger.getId())).isEmpty();
        assertThat(analyses.findSummariesForUser(stranger.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(analyses.countByUserId(stranger.getId())).isZero();
        assertThat(analyses.deleteByPublicIdAndUserId(saved.getPublicId(), stranger.getId())).isZero();
    }

    @Test
    @DisplayName("enums are stored as names, not ordinals")
    void enumsAreStoredAsText() {
        Analysis saved = analyses.saveAndFlush(fullAnalysis(78));
        em.flush();

        Object status = em.getEntityManager()
                .createNativeQuery("select status from analyses where id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        // With the JPA default of ORDINAL, inserting a new constant in the middle of the enum
        // would silently reinterpret every existing row.
        assertThat(status).hasToString("COMPLETED");
    }

    @Test
    @DisplayName("deleting an analysis removes its children and leaves its inputs alone")
    void deleteCascadesToChildrenOnly() {
        Analysis saved = analyses.saveAndFlush(fullAnalysis(78));
        Long analysisId = saved.getId();
        em.flush();
        em.clear();

        assertThat(analyses.deleteByPublicIdAndUserId(saved.getPublicId(), owner.getId())).isEqualTo(1);
        em.flush();
        em.clear();

        assertThat(nativeCount("analysis_skills", "analysis_id", analysisId)).isZero();
        assertThat(nativeCount("recommendations", "analysis_id", analysisId)).isZero();
        assertThat(nativeCount("analysis_keywords", "analysis_id", analysisId)).isZero();
        assertThat(nativeCount("analysis_section_scores", "analysis_id", analysisId)).isZero();

        // The resume, the posting and the taxonomy are inputs, not parts of the result.
        assertThat(resumes.findByPublicIdAndUserId(resume.getPublicId(), owner.getId())).isPresent();
        assertThat(skills.findBySlug("docker")).isPresent();
    }

    @Test
    @DisplayName("a resume's analyses are removed before the resume itself")
    void analysesAreClearedWhenAResumeIsDeleted() {
        analyses.saveAndFlush(fullAnalysis(78));
        analyses.saveAndFlush(fullAnalysis(81));
        em.flush();
        em.clear();

        assertThat(analyses.existsByResumeId(resume.getId())).isTrue();
        assertThat(analyses.deleteByResumeId(resume.getId())).isEqualTo(2);
        em.flush();
        em.clear();

        // Only now is the foreign key clear, which is why the service deletes in this order.
        assertThat(analyses.existsByResumeId(resume.getId())).isFalse();
        assertThat(resumes.deleteByPublicIdAndUserId(resume.getPublicId(), owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("summaries carry the job title and resume label, newest first")
    void listsSummariesForTheHistoryPage() {
        Analysis older = analyses.saveAndFlush(fullAnalysis(64));
        Analysis newer = analyses.saveAndFlush(fullAnalysis(88));
        em.flush();
        TestFixtures.backdate(em.getEntityManager(), "analyses", older.getId(),
                Instant.now().minus(3, ChronoUnit.DAYS));
        em.clear();

        List<AnalysisSummary> summaries = analyses.findSummariesForUser(owner.getId(), PageRequest.of(0, 10));

        assertThat(summaries).extracting(AnalysisSummary::getOverallScore).containsExactly(88, 64);
        AnalysisSummary newest = summaries.get(0);
        assertThat(newest.getPublicId()).isEqualTo(newer.getPublicId());
        assertThat(newest.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(newest.getCompany()).isEqualTo("Northwind");
        assertThat(newest.getResumeLabel()).isEqualTo("Owner CV");
        assertThat(newest.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(newest.getCompletedAt()).isNotNull();

        // The page size is honoured, so the history page cannot be made to load a whole account.
        assertThat(analyses.findSummariesForUser(owner.getId(), PageRequest.of(0, 1))).hasSize(1);
    }

    @Test
    @DisplayName("the score chart skips runs that never finished")
    void scoreHistoryCoversCompletedRunsOnly() {
        Analysis first = analyses.saveAndFlush(TestFixtures.completedAnalysis(owner, resume, jobDescription, 70));
        Analysis second = analyses.saveAndFlush(TestFixtures.completedAnalysis(owner, resume, jobDescription, 82));
        Analysis failed = analyses.saveAndFlush(TestFixtures.failedAnalysis(owner, resume, jobDescription));
        em.flush();
        TestFixtures.backdate(em.getEntityManager(), "analyses", first.getId(),
                Instant.now().minus(3, ChronoUnit.DAYS));
        TestFixtures.backdate(em.getEntityManager(), "analyses", failed.getId(),
                Instant.now().minus(2, ChronoUnit.DAYS));
        em.clear();

        List<ScorePoint> history = analyses.findScoreHistoryForUser(owner.getId());

        // A failed run has null scores; including it would draw a hole in the trend line.
        assertThat(history).extracting(ScorePoint::getOverallScore).containsExactly(70, 82);
        assertThat(history.get(0).getRecordedAt()).isBefore(history.get(1).getRecordedAt());
        assertThat(history.get(1).getAtsScore()).isEqualTo(84);
        assertThat(analyses.countByUserIdAndStatus(owner.getId(), AnalysisStatus.FAILED)).isEqualTo(1);
        assertThat(second.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("dashboard totals average the completed runs")
    void totalsAggregateCompletedRuns() {
        analyses.saveAndFlush(TestFixtures.completedAnalysis(owner, resume, jobDescription, 70));
        analyses.saveAndFlush(TestFixtures.completedAnalysis(owner, resume, jobDescription, 82));
        analyses.saveAndFlush(TestFixtures.failedAnalysis(owner, resume, jobDescription));
        em.flush();
        em.clear();

        DashboardTotals totals = analyses.findTotalsForUser(owner.getId());

        assertThat(totals.getAnalysisCount()).isEqualTo(2);
        assertThat(totals.getAverageScore()).isEqualTo(76.0);
        assertThat(totals.getBestScore()).isEqualTo(82);
    }

    @Test
    @DisplayName("a new account gets empty totals rather than zeroes")
    void totalsAreNullForAnAccountWithNoRuns() {
        DashboardTotals totals = analyses.findTotalsForUser(stranger.getId());

        // avg() over no rows is null, not 0. The dashboard has to render "no analyses yet"
        // instead of an average score of zero, which would read as a verdict.
        assertThat(totals.getAnalysisCount()).isZero();
        assertThat(totals.getAverageScore()).isNull();
        assertThat(totals.getBestScore()).isNull();
    }
}
