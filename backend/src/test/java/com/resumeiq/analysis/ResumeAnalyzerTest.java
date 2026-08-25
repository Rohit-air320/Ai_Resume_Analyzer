package com.resumeiq.analysis;

import com.resumeiq.analysis.ai.AdviceSource;
import com.resumeiq.analysis.ai.AiAdvice;
import com.resumeiq.analysis.ai.OfflineAdviceSource;
import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.analysis.engine.SkillVerdict;
import com.resumeiq.recommendation.Priority;
import com.resumeiq.support.AnalysisFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole analysis, end to end, from two string literals.
 *
 * <p>These are the tests that would fail if the phase's central claim stopped being true. Everything else
 * in the phase tests one component; this tests the arrangement — that the numbers come out of the engine,
 * that the words come out of a writer, and that the second cannot influence the first. The dishonest
 * writer below is the proof: it is handed the real findings and returns advice that contradicts every one
 * of them, and the analysis comes out clean anyway.
 *
 * <p>No database, no Spring context, no network, because {@link ResumeAnalyzer#analyseWith} takes its
 * catalogue, its keyword limit and its writer as arguments. The Spring-managed {@code analyse} method
 * exists only to supply those three, and is exercised by Phase 7's API tests.
 */
class ResumeAnalyzerTest {

    private static final AnalysisInput STRONG_MATCH = new AnalysisInput(
            AnalysisFixtures.STRONG_RESUME, AnalysisFixtures.POSTING, AnalysisFixtures.ROLE);

    @Test
    @DisplayName("an analysis produces every output the spec lists")
    void anAnalysisProducesEverySpecifiedOutput() {
        AnalysisOutcome outcome = analyse(STRONG_MATCH, new OfflineAdviceSource());
        AnalysisFacts facts = outcome.facts();

        // Walked through in the order the spec lists them, because "produces all twelve outputs" is the
        // requirement and a test that checks nine of them looks identical from the outside.
        assertThat(facts.scores().ats()).isBetween(0, 100);
        assertThat(facts.scores().jobMatch()).isBetween(0, 100);
        assertThat(facts.skills().strong()).isNotEmpty();
        assertThat(facts.skills().gaps()).isNotEmpty();
        assertThat(facts.matchedKeywords()).isNotEmpty();
        assertThat(outcome.advice().suggestedKeywords()).isNotEmpty();
        assertThat(outcome.advice().improvements()).isNotEmpty();
        assertThat(outcome.advice().skillGaps()).isNotEmpty();
        assertThat(outcome.advice().recommendedProjects()).isNotEmpty();
        assertThat(outcome.advice().learningRecommendations()).isNotEmpty();
        assertThat(outcome.advice().sectionNotes()).hasSize(ResumeSection.values().length);
        assertThat(outcome.advice().overallFeedback()).isNotBlank();
    }

    @Test
    @DisplayName("a writer that contradicts every finding changes nothing about the analysis")
    void aDishonestWriterCannotChangeTheAnalysis() {
        AnalysisOutcome honest = analyse(STRONG_MATCH, new OfflineAdviceSource());
        AnalysisOutcome lied = analyse(STRONG_MATCH, new DishonestSource());

        // The scores are identical because the writer runs after them and is never consulted about them.
        assertThat(lied.facts().scores()).isEqualTo(honest.facts().scores());
        assertThat(lied.facts().skills().gaps()).isEqualTo(honest.facts().skills().gaps());
        // And none of the invented advice survived: not the skill the posting never named, not the
        // keyword with nowhere to go, not the project claiming to evidence either of them.
        assertThat(lied.advice().skillGaps()).isEmpty();
        assertThat(lied.advice().suggestedKeywords()).isEmpty();
        assertThat(lied.advice().recommendedProjects()).singleElement()
                .satisfies(idea -> assertThat(idea.skillSlugs()).isEmpty());
        // Its opinion about the scores is kept, and kept exactly where opinions belong.
        assertThat(lied.advice().modelScores()).containsEntry("overallScore", 100);
        assertThat(lied.facts().scores().overall()).isNotEqualTo(100);
        // The honest limit of the mechanism, stated rather than implied: an improvement is free prose,
        // and no filter can tell that "Add Rust to your skills list" is a false claim rather than a
        // suggestion. Structured claims — skills, keywords, project slugs, scores — are validated by
        // construction. Prose is governed by the prompt's rules and by nothing else.
        assertThat(lied.advice().improvements()).singleElement().satisfies(item ->
                assertThat(item.title()).isEqualTo("Add Rust to your skills list"));
    }

    @Test
    @DisplayName("a weak resume scores below a strong one against the same posting")
    void aWeakResumeScoresLowerThanAStrongOne() {
        // The one comparison a user would notice immediately if it broke, and the reason the fixtures
        // are shared: two resumes, one posting, and a difference that has to run the right way.
        int strong = analyse(STRONG_MATCH, new OfflineAdviceSource()).facts().scores().overall();
        int thin = analyse(new AnalysisInput(AnalysisFixtures.THIN_RESUME, AnalysisFixtures.POSTING,
                AnalysisFixtures.ROLE), new OfflineAdviceSource()).facts().scores().overall();

        assertThat(thin).isLessThan(strong);
    }

    @Test
    @DisplayName("the outcome says which writer produced the words, and whether a model was involved")
    void theOutcomeSaysWhereItsWordsCameFrom() {
        AnalysisOutcome computed = analyse(STRONG_MATCH, new OfflineAdviceSource());
        AnalysisOutcome written = analyse(STRONG_MATCH, new DishonestSource());

        assertThat(computed.adviceSource()).isEqualTo(OfflineAdviceSource.DESCRIPTION);
        assertThat(computed.isModelWritten()).isFalse();
        assertThat(written.adviceSource()).isEqualTo(DishonestSource.NAME);
        assertThat(written.isModelWritten()).isTrue();
    }

    @Test
    @DisplayName("an empty resume is analysed rather than refused")
    void anEmptyResumeIsStillAnalysed() {
        // Refusing would leave the user with nothing to act on, which is the opposite of the point. The
        // scores are honest about it — low — and the advice says the documents are too thin to compare.
        AnalysisOutcome outcome = analyse(
                new AnalysisInput("", AnalysisFixtures.POSTING, AnalysisFixtures.ROLE),
                new OfflineAdviceSource());

        assertThat(outcome.facts().isThin()).isTrue();
        assertThat(outcome.facts().scores().overall()).isBetween(0, 100);
        assertThat(outcome.advice().overallFeedback())
                .contains("not enough in one or both documents to compare them properly");
    }

    @Test
    @DisplayName("null inputs are analysed as empty documents rather than throwing four layers down")
    void nullInputsAreTreatedAsEmptyDocuments() {
        AnalysisOutcome outcome = analyse(new AnalysisInput(null, null, null),
                new OfflineAdviceSource());

        assertThat(outcome.facts().isThin()).isTrue();
        assertThat(outcome.facts().sections()).hasSize(ResumeSection.values().length);
        assertThat(outcome.advice().overallFeedback()).isNotBlank();
    }

    @Test
    @DisplayName("a skill is identified by the same slug on both sides of the comparison")
    void bothSidesAgreeOnSlugs() {
        AnalysisFacts facts = analyse(STRONG_MATCH, new OfflineAdviceSource()).facts();

        // The observable consequence of loading the catalogue once and passing it to both the posting
        // parser and the resume reader. Two loads would work today and would produce a quietly wrong
        // gap list the first time a skill was added between them: demanded under one slug, demonstrated
        // under another, and therefore reported as missing.
        assertThat(facts.skills().strong()).extracting(SkillVerdict::slug).contains("java", "mysql");
        assertThat(facts.resume().find("java")).isPresent();
        assertThat(facts.skills().gaps()).isNotEmpty().extracting(SkillVerdict::slug)
                .allSatisfy(slug -> assertThat(facts.resume().find(slug)).isEmpty());
    }

    private static AnalysisOutcome analyse(AnalysisInput input, AdviceSource source) {
        return ResumeAnalyzer.analyseWith(input, AnalysisFixtures.CATALOGUE,
                AnalysisFixtures.MAX_KEYWORDS, source);
    }

    /**
     * A writer that does everything the spec forbids.
     *
     * <p>Not a strawman. Every one of these is a real observed model failure: naming a skill from a
     * different posting, suggesting a term with no place to put it, claiming a project evidences
     * something it does not, and restating the scores as whatever sounds encouraging. The point of the
     * test that uses it is that none of it needs to be caught by a prompt.
     */
    private static final class DishonestSource implements AdviceSource {

        private static final String NAME = "a model having a bad day";

        @Override
        public AiAdvice adviseOn(AnalysisFacts facts, String postingText) {
            return new AiAdvice(
                    "An outstanding resume with no weaknesses worth mentioning.",
                    List.of(new AiAdvice.Improvement("Add Rust to your skills list",
                            "Employers love it.", Priority.HIGH, ResumeSection.SKILLS)),
                    List.of(new AiAdvice.GapNote("rust", "This posting requires Rust.",
                            Priority.HIGH)),
                    List.of(new AiAdvice.ProjectIdea("A Rust web server",
                            "Would demonstrate Rust and COBOL.", List.of("rust", "cobol"))),
                    List.of(),
                    List.of(new AiAdvice.KeywordPlacement("rust", "")),
                    List.of(),
                    Map.of("overallScore", 100),
                    NAME);
        }

        @Override
        public String describe() {
            return NAME;
        }
    }
}
