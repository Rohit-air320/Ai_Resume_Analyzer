package com.resumeiq.analysis;

import com.resumeiq.jobdescription.JobDescription;
import com.resumeiq.resume.Resume;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The skill-gap aggregation — the query the whole skill-gap page is built on.
 *
 * <p>Two behaviours are load bearing and neither is obvious from reading the JPQL: mentions that
 * resolved to the taxonomy are counted under one canonical name however they were spelled, and
 * mentions that did not resolve still count instead of disappearing.
 */
@RepositoryTest
class AnalysisSkillRepositoryTest {

    @Autowired
    private AnalysisSkillRepository analysisSkills;

    @Autowired
    private AnalysisRepository analyses;

    @Autowired
    private SkillRepository skills;

    @Autowired
    private TestEntityManager em;

    private User owner;
    private User stranger;
    private Skill docker;

    @BeforeEach
    void setUp() {
        owner = em.persistFlushFind(TestFixtures.user("gap-owner@example.com"));
        stranger = em.persistFlushFind(TestFixtures.user("gap-stranger@example.com"));
        docker = skills.findBySlug("docker").orElseGet(() -> skills.saveAndFlush(Skill.builder()
                .slug("docker")
                .displayName("Docker")
                .category(SkillCategory.DEVOPS)
                .build()));
    }

    /** An analysis with the given gap rows, where a resolved row is written as {@code raw=slug}. */
    private Analysis analysisFor(User user, String label, AnalysisSkill... skillRows) {
        Resume resume = em.persistFlushFind(TestFixtures.resume(user, label + " CV"));
        JobDescription jd = em.persistFlushFind(
                TestFixtures.jobDescription(user, label, "Posting for " + label));

        Analysis analysis = TestFixtures.completedAnalysis(user, resume, jd, 72);
        for (AnalysisSkill row : skillRows) {
            analysis.addSkill(row);
        }
        return analyses.saveAndFlush(analysis);
    }

    private AnalysisSkill resolved(String rawName, SkillStatus status) {
        AnalysisSkill row = TestFixtures.analysisSkill(rawName, status, SkillImportance.CRITICAL);
        row.setSkill(docker);
        return row;
    }

    @Test
    @DisplayName("gaps are counted per canonical skill, most frequent first")
    void countsGapsAcrossAnalyses() {
        // The same skill, written two different ways, resolved to one taxonomy row.
        analysisFor(owner, "First",
                resolved("docker", SkillStatus.MISSING),
                TestFixtures.analysisSkill("Kafka", SkillStatus.MISSING, SkillImportance.IMPORTANT),
                TestFixtures.analysisSkill("Java", SkillStatus.STRONG, SkillImportance.CRITICAL));
        analysisFor(owner, "Second",
                resolved("Docker-Engine", SkillStatus.MISSING),
                TestFixtures.analysisSkill("Terraform", SkillStatus.MISSING, SkillImportance.NICE_TO_HAVE));
        // Another account's gaps must not leak into these counts.
        analysisFor(stranger, "Stranger", resolved("docker", SkillStatus.MISSING));
        em.flush();
        em.clear();

        List<SkillGapCount> gaps = analysisSkills.countGapsForUser(
                owner.getId(), SkillStatus.MISSING, PageRequest.of(0, 10));

        assertThat(gaps)
                .extracting(SkillGapCount::getLabel, SkillGapCount::getOccurrences)
                .containsExactly(
                        tuple("Docker", 2L),      // "docker" and "Docker-Engine" are one skill
                        tuple("Kafka", 1L),       // never resolved, still counted
                        tuple("Terraform", 1L));  // ties fall back to alphabetical order
    }

    @Test
    @DisplayName("the gap list is limited by the pageable")
    void respectsThePageSize() {
        analysisFor(owner, "First",
                resolved("docker", SkillStatus.MISSING),
                TestFixtures.analysisSkill("Kafka", SkillStatus.MISSING, SkillImportance.IMPORTANT));
        analysisFor(owner, "Second", resolved("Docker-Engine", SkillStatus.MISSING));
        em.flush();
        em.clear();

        // The skill-gap page shows a top-N list; without the limit this query grows with history.
        assertThat(analysisSkills.countGapsForUser(owner.getId(), SkillStatus.MISSING, PageRequest.of(0, 1)))
                .singleElement()
                .satisfies(gap -> assertThat(gap.getLabel()).isEqualTo("Docker"));
    }

    @Test
    @DisplayName("partial matches are counted separately from missing ones")
    void separatesPartialFromMissing() {
        analysisFor(owner, "First",
                TestFixtures.analysisSkill("Kubernetes", SkillStatus.PARTIAL, SkillImportance.IMPORTANT),
                TestFixtures.analysisSkill("Kafka", SkillStatus.MISSING, SkillImportance.IMPORTANT));
        em.flush();
        em.clear();

        assertThat(analysisSkills.countGapsForUser(owner.getId(), SkillStatus.PARTIAL, PageRequest.of(0, 10)))
                .extracting(SkillGapCount::getLabel)
                .containsExactly("Kubernetes");
        assertThat(analysisSkills.countGapsForUser(owner.getId(), SkillStatus.STRONG, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("skill rows are readable by analysis and by status")
    void readsRowsForOneAnalysis() {
        Analysis analysis = analysisFor(owner, "First",
                resolved("docker", SkillStatus.MISSING),
                TestFixtures.analysisSkill("Java", SkillStatus.STRONG, SkillImportance.CRITICAL));
        em.flush();
        em.clear();

        assertThat(analysisSkills.findByAnalysisId(analysis.getId())).hasSize(2);
        assertThat(analysisSkills.findByAnalysisIdAndStatus(analysis.getId(), SkillStatus.MISSING))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.label()).isEqualTo("Docker");
                    assertThat(row.getRawName()).isEqualTo("docker");
                    assertThat(row.isGap()).isTrue();
                    // A missing skill has no evidence in the resume, by definition.
                    assertThat(row.getEvidence()).isNull();
                });
    }

    @Test
    @DisplayName("a gap row keeps the raw mention when nothing matched")
    void keepsUnresolvedMentions() {
        Analysis analysis = analysisFor(owner, "First",
                TestFixtures.analysisSkill("Bespoke Ledger Framework", SkillStatus.MISSING,
                        SkillImportance.CRITICAL));
        em.flush();
        em.clear();

        assertThat(analysisSkills.findByAnalysisId(analysis.getId()))
                .singleElement()
                .satisfies(row -> {
                    // Dropping these would hide the one requirement most likely to be unusual —
                    // and unusual requirements are the ones worth telling the user about.
                    assertThat(row.getSkill()).isNull();
                    assertThat(row.label()).isEqualTo("Bespoke Ledger Framework");
                });
    }
}
