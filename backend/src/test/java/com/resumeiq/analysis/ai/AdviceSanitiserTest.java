package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.analysis.engine.SkillVerdict;
import com.resumeiq.recommendation.Priority;
import com.resumeiq.support.AnalysisFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter that stands between a model's advice and the user.
 *
 * <p>This is the class the spec's honesty requirements actually rest on. A prompt telling a model not to
 * invent a skill makes it rarer; a filter that drops any skill slug absent from the computed gap list
 * means an invented skill cannot reach the user at all. Only the second of those is testable, and these
 * are the tests.
 *
 * <p>Every test filters against real computed findings rather than a hand-built set, because the whole
 * point of the filter is that its authority comes from the deterministic half of the analysis. The
 * advice, by contrast, is always hand-built: these are the responses a model gives when it is wrong, and
 * writing them out is the only way to be sure of what happens to each one.
 */
class AdviceSanitiserTest {

    private static final String SOURCE = "test-model";

    /**
     * Priya's resume against the Northwind posting.
     *
     * <p>Five demands answered with demonstrated work, four left unanswered. That gives the filters
     * something real on both sides: slugs that are genuinely gaps, slugs that are genuinely demanded and
     * not gaps, terms the resume already uses, and terms it does not.
     */
    private static final AnalysisFacts FACTS =
            AnalysisFixtures.facts(AnalysisFixtures.STRONG_RESUME);

    @Test
    @DisplayName("the findings these tests filter against hold what the other tests assume")
    void theFixtureHoldsWhatTheOtherTestsAssume() {
        // Stated once here rather than implied fifteen times below. If the parser or the catalogue
        // changes and Docker stops being a gap, this test names the reason the rest went red instead of
        // leaving fifteen assertion failures to work backwards from.
        assertThat(FACTS.skills().gaps()).extracting(SkillVerdict::slug)
                .contains("docker")
                .doesNotContain("java");
        assertThat(FACTS.skills().demanded()).extracting(SkillVerdict::slug).contains("java");
        assertThat(FACTS.absentKeywords()).isNotEmpty();
        assertThat(FACTS.matchedKeywords()).isNotEmpty();
        assertThat(FACTS.sections()).hasSize(ResumeSection.values().length);
    }

    @Test
    @DisplayName("a gap the posting never asked for is dropped, and so is one the resume answers")
    void onlyComputedGapsSurvive() {
        // The single most important filter in the class. "You are missing Rust" about a posting that
        // never mentioned Rust is not a bad suggestion, it is a false one, and a user who believes it
        // spends a weekend on the wrong thing.
        AiAdvice clean = AdviceSanitiser.clean(withGaps(List.of(
                new AiAdvice.GapNote(aGap(), "Named in the posting and absent here.", Priority.HIGH),
                new AiAdvice.GapNote(aGap().toUpperCase(Locale.ROOT), "Said twice.", Priority.LOW),
                new AiAdvice.GapNote("rust", "The posting never asked for this.", Priority.HIGH),
                new AiAdvice.GapNote("java", "Evidenced in two roles and a project.", Priority.HIGH))),
                FACTS);

        assertThat(clean.skillGaps()).extracting(AiAdvice.GapNote::slug).containsExactly(aGap());
    }

    @Test
    @DisplayName("a project survives, but a skill it falsely claims to demonstrate does not")
    void aProjectSurvivesItsFalseClaims() {
        // A project is an idea and does not need validating — nobody is harmed by being told to build
        // something. What it says it demonstrates is a claim about this posting, so that part does.
        AiAdvice clean = AdviceSanitiser.clean(withProjects(List.of(
                new AiAdvice.ProjectIdea("Containerise the reconciler",
                        "Package the Ledger service and publish an image.",
                        List.of("java", "rust", "python", "docker")))), FACTS);

        assertThat(clean.recommendedProjects()).singleElement().satisfies(idea -> {
            assertThat(idea.title()).isEqualTo("Containerise the reconciler");
            // "rust" is not in the catalogue and "python" is on the resume but not in this posting, so
            // neither is a claim this posting can support. Docker leads because closing the gap is the
            // reason to build the thing; Java follows because it is demanded and true.
            assertThat(idea.skillSlugs()).containsExactly("docker", "java");
        });
    }

    @Test
    @DisplayName("a suggested term the posting never used is dropped")
    void anInventedTermIsDropped() {
        AiAdvice clean = AdviceSanitiser.clean(withKeywords(List.of(
                new AiAdvice.KeywordPlacement("blockchain", "the summary line"),
                new AiAdvice.KeywordPlacement(anAbsentTerm(), "the Ledger Reconciler line"))), FACTS);

        assertThat(clean.suggestedKeywords()).extracting(AiAdvice.KeywordPlacement::term)
                .containsExactly(anAbsentTerm());
    }

    @Test
    @DisplayName("a term the resume already uses is not a suggestion")
    void aTermAlreadyInTheResumeIsDropped() {
        // Not dishonest, just useless: the advice would read "add MySQL" to a resume that names MySQL
        // three times, and one suggestion like that is enough for a reader to stop trusting the list.
        assertThat(AdviceSanitiser.clean(withKeywords(List.of(
                new AiAdvice.KeywordPlacement(aMatchedTerm(), "the experience section"))), FACTS)
                .suggestedKeywords())
                .isEmpty();
    }

    @Test
    @DisplayName("a term with no placement is dropped rather than repaired")
    void aTermWithNoPlacementIsDropped() {
        // The rule the spec is most emphatic about. A term with no placement is exactly the
        // keyword-stuffing instruction it forbids: a word to paste in, with no honest answer to
        // "where". Repairing it into a term with an empty placement would launder a forbidden
        // suggestion into an allowed shape, so it goes.
        assertThat(AdviceSanitiser.clean(withKeywords(List.of(
                new AiAdvice.KeywordPlacement(anAbsentTerm(), ""),
                new AiAdvice.KeywordPlacement(anAbsentTerm(), "   "),
                new AiAdvice.KeywordPlacement(anAbsentTerm(), null))), FACTS)
                .suggestedKeywords())
                .isEmpty();
    }

    @Test
    @DisplayName("a whole list of terms with nowhere to put them yields no keyword advice at all")
    void keywordStuffingCannotSurviveTheSanitiser() {
        // Every real absent term, each one a bare word with no place for it. This is what a model
        // producing a keyword-stuffing list looks like, and the honest output is nothing.
        List<AiAdvice.KeywordPlacement> stuffing = FACTS.absentKeywords().stream()
                .map(verdict -> new AiAdvice.KeywordPlacement(verdict.term(), ""))
                .toList();

        assertThat(AdviceSanitiser.clean(withKeywords(stuffing), FACTS).suggestedKeywords())
                .isEmpty();
    }

    @Test
    @DisplayName("the same term twice in different case is one suggestion")
    void duplicateTermsCollapse() {
        AiAdvice clean = AdviceSanitiser.clean(withKeywords(List.of(
                new AiAdvice.KeywordPlacement(anAbsentTerm(), "the Ledger Reconciler line"),
                new AiAdvice.KeywordPlacement(anAbsentTerm().toUpperCase(Locale.ROOT),
                        "the summary"))), FACTS);

        assertThat(clean.suggestedKeywords()).singleElement()
                .satisfies(keyword -> assertThat(keyword.placement())
                        .isEqualTo("the Ledger Reconciler line"));
    }

    @Test
    @DisplayName("an implausible link is removed and the topic is kept")
    void aFabricatedLinkIsRemovedAndTheTopicSurvives() {
        // A model asked for a link will produce one whether or not it knows of one, and a URL that
        // 404s is worse than no URL: it makes the user doubt the advice next to it. Checking the shape
        // is as far as this can honestly go — verifying the page would mean an outbound request per
        // suggestion, from inside an analysis, to an address a model chose.
        AiAdvice clean = AdviceSanitiser.clean(withLearning(List.of(
                new AiAdvice.LearningTopic("Container fundamentals", "Images, layers, networking.",
                        "https://docs.example.test/containers", Priority.HIGH),
                new AiAdvice.LearningTopic("Orchestration", "Scheduling and rollouts.",
                        "http://docs.example.test/k8s", Priority.MEDIUM),
                new AiAdvice.LearningTopic("Ingress", "Routing into a cluster.",
                        "https://docs.example.test/a page", Priority.LOW),
                new AiAdvice.LearningTopic("Volumes", "Persistent storage.", "see the docs",
                        Priority.LOW),
                new AiAdvice.LearningTopic("Metrics", "What to measure first.", null,
                        Priority.LOW))), FACTS);

        assertThat(clean.learningRecommendations()).extracting(AiAdvice.LearningTopic::title)
                .containsExactly("Container fundamentals", "Orchestration", "Ingress", "Volumes",
                        "Metrics");
        assertThat(clean.learningRecommendations()).extracting(AiAdvice.LearningTopic::resourceUrl)
                .containsExactly("https://docs.example.test/containers", null, null, null, null);
    }

    @Test
    @DisplayName("a blank title is dropped and case-only duplicates collapse to the first")
    void blankAndDuplicateTitlesAreDropped() {
        AiAdvice clean = AdviceSanitiser.clean(withImprovements(List.of(
                new AiAdvice.Improvement("   ", "Nothing to show for it.", Priority.HIGH, null),
                new AiAdvice.Improvement("Quantify the migration", "First.", Priority.HIGH,
                        ResumeSection.EXPERIENCE),
                new AiAdvice.Improvement("QUANTIFY THE MIGRATION", "Second.", Priority.LOW, null))),
                FACTS);

        assertThat(clean.improvements()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Quantify the migration");
            assertThat(item.detail()).isEqualTo("First.");
        });
    }

    @Test
    @DisplayName("one note per section, first one wins, and a note about nothing is dropped")
    void sectionNotesAreOnePerSection() {
        AiAdvice clean = AdviceSanitiser.clean(withNotes(List.of(
                new AiAdvice.SectionNote(ResumeSection.SUMMARY, "Names the role, not the evidence."),
                new AiAdvice.SectionNote(ResumeSection.SUMMARY, "A second opinion on the same one."),
                new AiAdvice.SectionNote(null, "No section at all."),
                new AiAdvice.SectionNote(ResumeSection.SKILLS, "   "),
                new AiAdvice.SectionNote(ResumeSection.EXPERIENCE, "Quantified and specific."))),
                FACTS);

        assertThat(clean.sectionNotes()).extracting(AiAdvice.SectionNote::section)
                .containsExactly(ResumeSection.SUMMARY, ResumeSection.EXPERIENCE);
        assertThat(clean.sectionNotes().get(0).note())
                .isEqualTo("Names the role, not the evidence.");
    }

    @Test
    @DisplayName("each list is cut to a length somebody will actually work through")
    void perListCapsAreApplied() {
        AiAdvice clean = AdviceSanitiser.clean(new AiAdvice("Fine.",
                improvements(12), List.of(), projects(8), learning(8), List.of(), List.of(),
                Map.of(), SOURCE), FACTS);

        // A reading limit rather than a storage one. Twenty improvements is a list nobody works
        // through, and a model given room for twenty will pad to fill it; cutting at the cap keeps the
        // ones it led with, which is what it was asked to put first.
        assertThat(clean.improvements()).extracting(AiAdvice.Improvement::title)
                .hasSize(8)
                .containsSequence("Improvement 0", "Improvement 1")
                .doesNotContain("Improvement 8");
        assertThat(clean.recommendedProjects()).hasSize(6);
        assertThat(clean.learningRecommendations()).hasSize(6);
    }

    @Test
    @DisplayName("every string is cut to the width of the column it will be stored in")
    void everyStringIsCutToItsColumn() {
        // Truncating here rather than at the insert means a verbose model cannot cause a persistence
        // failure. The alternative is a constraint violation at the end of a successful analysis: a 500
        // for the user, and a lost result for a reason that has nothing to do with them.
        String tooLongTitle = "t".repeat(300);
        String tooLongDetail = "d".repeat(2_500);
        AiAdvice clean = AdviceSanitiser.clean(new AiAdvice("f".repeat(2_500),
                List.of(new AiAdvice.Improvement(tooLongTitle, tooLongDetail, Priority.HIGH,
                        ResumeSection.SUMMARY)),
                List.of(new AiAdvice.GapNote(aGap(), tooLongDetail, Priority.HIGH)),
                List.of(new AiAdvice.ProjectIdea(tooLongTitle, tooLongDetail, List.of())),
                List.of(new AiAdvice.LearningTopic(tooLongTitle, tooLongDetail, null, Priority.LOW)),
                List.of(new AiAdvice.KeywordPlacement(anAbsentTerm(), "p".repeat(400))),
                List.of(new AiAdvice.SectionNote(ResumeSection.SKILLS, "n".repeat(600))),
                Map.of(), SOURCE), FACTS);

        assertThat(clean.overallFeedback()).hasSize(2_000);
        assertThat(clean.improvements()).singleElement().satisfies(item -> {
            assertThat(item.title()).hasSize(160);
            assertThat(item.detail()).hasSize(2_000);
        });
        assertThat(clean.skillGaps().get(0).detail()).hasSize(2_000);
        assertThat(clean.recommendedProjects().get(0).title()).hasSize(160);
        assertThat(clean.learningRecommendations().get(0).detail()).hasSize(2_000);
        assertThat(clean.suggestedKeywords().get(0).placement()).hasSize(300);
        assertThat(clean.sectionNotes().get(0).note()).hasSize(400);
    }

    @Test
    @DisplayName("a null detail becomes an empty string, because the column is not nullable")
    void nullDetailsBecomeEmptyStrings() {
        AiAdvice clean = AdviceSanitiser.clean(new AiAdvice(null,
                List.of(new AiAdvice.Improvement("Quantify the migration", null, Priority.HIGH, null)),
                List.of(new AiAdvice.GapNote(aGap(), null, Priority.HIGH)),
                List.of(new AiAdvice.ProjectIdea("Containerise the reconciler", null, List.of())),
                List.of(new AiAdvice.LearningTopic("Container fundamentals", null, null,
                        Priority.LOW)),
                List.of(), List.of(), Map.of(), SOURCE), FACTS);

        assertThat(clean.overallFeedback()).isEmpty();
        assertThat(clean.improvements().get(0).detail()).isEmpty();
        assertThat(clean.skillGaps().get(0).detail()).isEmpty();
        assertThat(clean.recommendedProjects().get(0).detail()).isEmpty();
        assertThat(clean.learningRecommendations().get(0).detail()).isEmpty();
    }

    @Test
    @DisplayName("the model's scores and the source pass through, because neither is a claim to check")
    void scoresAndSourcePassThroughUnchanged() {
        // These are not the scores. The engine's numbers are, and they have already been computed by
        // the time this runs. Keeping the model's opinion is what lets the cross-check log a
        // disagreement without ever acting on one.
        AiAdvice clean = AdviceSanitiser.clean(new AiAdvice("Fine.", List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of("overallScore", 71, "atsScore", 84),
                SOURCE), FACTS);

        assertThat(clean.modelScores()).containsEntry("overallScore", 71).hasSize(2);
        assertThat(clean.source()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("cleaning empty advice is empty advice, not a failure")
    void emptyAdviceSurvivesCleaning() {
        AiAdvice clean = AdviceSanitiser.clean(AiAdvice.empty(SOURCE), FACTS);

        assertThat(clean.isEmpty()).isTrue();
        assertThat(clean.overallFeedback()).isEmpty();
        assertThat(clean.itemCount()).isZero();
        assertThat(clean.source()).isEqualTo(SOURCE);
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** The first computed gap. Which one it is does not matter; that it is real is the point. */
    private static String aGap() {
        return FACTS.skills().gaps().get(0).slug();
    }

    /** A term the posting leans on and this resume does not use. */
    private static String anAbsentTerm() {
        return FACTS.absentKeywords().get(0).term();
    }

    /** A term the resume already uses, so suggesting it would be advice to do what is done. */
    private static String aMatchedTerm() {
        return FACTS.matchedKeywords().get(0).term();
    }

    private static List<AiAdvice.Improvement> improvements(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new AiAdvice.Improvement("Improvement " + index,
                        "Detail " + index, Priority.MEDIUM, null))
                .toList();
    }

    private static List<AiAdvice.ProjectIdea> projects(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new AiAdvice.ProjectIdea("Project " + index, "Detail " + index,
                        List.of()))
                .toList();
    }

    private static List<AiAdvice.LearningTopic> learning(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new AiAdvice.LearningTopic("Topic " + index, "Detail " + index,
                        null, Priority.LOW))
                .toList();
    }

    private static AiAdvice withImprovements(List<AiAdvice.Improvement> items) {
        return new AiAdvice("Fine.", items, List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), SOURCE);
    }

    private static AiAdvice withGaps(List<AiAdvice.GapNote> items) {
        return new AiAdvice("Fine.", List.of(), items, List.of(), List.of(), List.of(), List.of(),
                Map.of(), SOURCE);
    }

    private static AiAdvice withProjects(List<AiAdvice.ProjectIdea> items) {
        return new AiAdvice("Fine.", List.of(), List.of(), items, List.of(), List.of(), List.of(),
                Map.of(), SOURCE);
    }

    private static AiAdvice withLearning(List<AiAdvice.LearningTopic> items) {
        return new AiAdvice("Fine.", List.of(), List.of(), List.of(), items, List.of(), List.of(),
                Map.of(), SOURCE);
    }

    private static AiAdvice withKeywords(List<AiAdvice.KeywordPlacement> items) {
        return new AiAdvice("Fine.", List.of(), List.of(), List.of(), List.of(), items, List.of(),
                Map.of(), SOURCE);
    }

    private static AiAdvice withNotes(List<AiAdvice.SectionNote> items) {
        return new AiAdvice("Fine.", List.of(), List.of(), List.of(), List.of(), List.of(), items,
                Map.of(), SOURCE);
    }
}
