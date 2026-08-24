package com.resumeiq.recommendation;

import com.resumeiq.analysis.Analysis;
import com.resumeiq.analysis.AnalysisRepository;
import com.resumeiq.jobdescription.JobDescription;
import com.resumeiq.resume.Resume;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Recommendation reads. This table has no {@code user_id} column, so every ownership filter here
 * walks {@code recommendation → analysis → user}; the tests check the stranger's side of each one.
 */
@RepositoryTest
class RecommendationRepositoryTest {

    @Autowired
    private RecommendationRepository recommendations;

    @Autowired
    private AnalysisRepository analyses;

    @Autowired
    private TestEntityManager em;

    private User owner;
    private User stranger;

    @BeforeEach
    void setUp() {
        owner = em.persistFlushFind(TestFixtures.user("rec-owner@example.com"));
        stranger = em.persistFlushFind(TestFixtures.user("rec-stranger@example.com"));
    }

    private Analysis analysisWith(User user, String label, Recommendation... advice) {
        Resume resume = em.persistFlushFind(TestFixtures.resume(user, label + " CV"));
        JobDescription jd = em.persistFlushFind(
                TestFixtures.jobDescription(user, label, "Posting for " + label));

        Analysis analysis = TestFixtures.completedAnalysis(user, resume, jd, 74);
        for (Recommendation recommendation : advice) {
            analysis.addRecommendation(recommendation);
        }
        return analyses.saveAndFlush(analysis);
    }

    @Test
    @DisplayName("one analysis's advice is grouped by type, then by display order")
    void ordersAdviceForTheResultsPage() {
        Analysis analysis = analysisWith(owner, "Mixed",
                TestFixtures.recommendation(RecommendationType.PROJECT, "Build a deploy pipeline", 0),
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Quantify the migration", 1),
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Lead with the outcome", 0),
                TestFixtures.recommendation(RecommendationType.LEARNING, "Learn container basics", 0),
                TestFixtures.recommendation(RecommendationType.KEYWORD, "Name the message broker", 0));
        em.flush();
        em.clear();

        List<Recommendation> ordered =
                recommendations.findByAnalysis_IdOrderByTypeAscDisplayOrderAsc(analysis.getId());

        // Because type is stored as text, "order by type" is alphabetical on the stored name —
        // not the enum's declaration order, which would put KEYWORD last. The UI groups by type
        // and reads the order from the query, so this is the grouping the user actually sees.
        assertThat(ordered).extracting(Recommendation::getTitle).containsExactly(
                "Lead with the outcome",     // IMPROVEMENT, order 0
                "Quantify the migration",    // IMPROVEMENT, order 1
                "Name the message broker",   // KEYWORD
                "Learn container basics",    // LEARNING
                "Build a deploy pipeline");  // PROJECT
    }

    @Test
    @DisplayName("the public-id read is scoped to the owner")
    void readsByPublicIdForTheOwnerOnly() {
        Analysis analysis = analysisWith(owner, "Scoped",
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Quantify the migration", 0));
        em.flush();
        em.clear();

        assertThat(recommendations
                .findByAnalysis_PublicIdAndAnalysis_User_IdOrderByTypeAscDisplayOrderAsc(
                        analysis.getPublicId(), owner.getId()))
                .singleElement()
                .satisfies(recommendation -> {
                    assertThat(recommendation.getTitle()).isEqualTo("Quantify the migration");
                    assertThat(recommendation.getPriority()).isEqualTo(Priority.HIGH);
                    assertThat(recommendation.getDetail()).isNotBlank();
                });

        // The controller never checks ownership itself: an empty list is the check.
        assertThat(recommendations
                .findByAnalysis_PublicIdAndAnalysis_User_IdOrderByTypeAscDisplayOrderAsc(
                        analysis.getPublicId(), stranger.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("the recommendations page reads newest first and honours the page size")
    void readsAcrossHistoryNewestFirst() {
        Analysis older = analysisWith(owner, "Older",
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Old advice", 0));
        analysisWith(owner, "Newer",
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "New advice", 0));
        analysisWith(stranger, "Theirs",
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Their advice", 0));
        em.flush();
        older.getRecommendations().forEach(recommendation -> TestFixtures.backdate(
                em.getEntityManager(), "recommendations", recommendation.getId(),
                Instant.now().minus(5, ChronoUnit.DAYS)));
        em.clear();

        assertThat(recommendations.findByAnalysis_User_IdOrderByCreatedAtDesc(
                owner.getId(), PageRequest.of(0, 10)))
                .extracting(Recommendation::getTitle)
                .containsExactly("New advice", "Old advice");

        // Advice accumulates with every run; without the limit this panel would eventually load
        // a user's whole history to render five cards.
        assertThat(recommendations.findByAnalysis_User_IdOrderByCreatedAtDesc(
                owner.getId(), PageRequest.of(0, 1)))
                .extracting(Recommendation::getTitle)
                .containsExactly("New advice");
    }

    @Test
    @DisplayName("advice can be narrowed to one type, per user")
    void filtersByType() {
        analysisWith(owner, "Mixed",
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Quantify the migration", 0),
                TestFixtures.recommendation(RecommendationType.LEARNING, "Learn container basics", 0),
                TestFixtures.recommendation(RecommendationType.PROJECT, "Build a deploy pipeline", 0));
        analysisWith(stranger, "Theirs",
                TestFixtures.recommendation(RecommendationType.LEARNING, "Their learning plan", 0));
        em.flush();
        em.clear();

        assertThat(recommendations.findByAnalysis_User_IdAndTypeOrderByCreatedAtDesc(
                owner.getId(), RecommendationType.LEARNING, PageRequest.of(0, 10)))
                .extracting(Recommendation::getTitle)
                .containsExactly("Learn container basics");
        assertThat(recommendations.countByAnalysis_User_IdAndType(
                owner.getId(), RecommendationType.LEARNING)).isEqualTo(1);
        assertThat(recommendations.countByAnalysis_User_IdAndType(
                owner.getId(), RecommendationType.KEYWORD)).isZero();
        assertThat(recommendations.countByAnalysis_User_IdAndType(
                stranger.getId(), RecommendationType.LEARNING)).isEqualTo(1);
    }

    @Test
    @DisplayName("a learning recommendation can carry a link, and other types need not")
    void keepsAnOptionalResourceLink() {
        Recommendation learning =
                TestFixtures.recommendation(RecommendationType.LEARNING, "Learn container basics", 0);
        learning.setResourceUrl("https://docs.docker.com/get-started/");
        Analysis analysis = analysisWith(owner, "Linked", learning,
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Quantify the migration", 0));
        em.flush();
        em.clear();

        assertThat(recommendations.findByAnalysis_IdOrderByTypeAscDisplayOrderAsc(analysis.getId()))
                .extracting(Recommendation::getType, Recommendation::getResourceUrl)
                .containsExactly(
                        tuple(RecommendationType.IMPROVEMENT, null),
                        tuple(RecommendationType.LEARNING, "https://docs.docker.com/get-started/"));
    }

    @Test
    @DisplayName("advice is removed with its analysis and nothing else")
    void deletesByAnalysis() {
        Analysis analysis = analysisWith(owner, "Doomed",
                TestFixtures.recommendation(RecommendationType.IMPROVEMENT, "Quantify the migration", 0),
                TestFixtures.recommendation(RecommendationType.LEARNING, "Learn container basics", 0));
        Analysis survivor = analysisWith(owner, "Kept",
                TestFixtures.recommendation(RecommendationType.PROJECT, "Build a deploy pipeline", 0));
        em.flush();
        em.clear();

        assertThat(recommendations.deleteByAnalysis_Id(analysis.getId())).isEqualTo(2);
        em.flush();
        em.clear();

        assertThat(recommendations.findByAnalysis_IdOrderByTypeAscDisplayOrderAsc(analysis.getId())).isEmpty();
        assertThat(recommendations.findByAnalysis_IdOrderByTypeAscDisplayOrderAsc(survivor.getId())).hasSize(1);
    }
}
