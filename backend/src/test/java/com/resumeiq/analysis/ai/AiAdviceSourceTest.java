package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.config.ResumeIqProperties.Ai;
import com.resumeiq.support.AnalysisFixtures;
import com.resumeiq.support.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule this class exists to enforce: a provider problem never costs the user their result.
 *
 * <p>Everything measurable about an analysis — the six scores, the gaps, the keywords, the section
 * findings — is computed before this class runs. So a timeout, a rate limit, a truncated response, or an
 * outright bug in a provider should cost the user the model's prose and nothing else. Every failure case
 * below therefore asserts two things: that the advice came back complete, and that the source string says
 * plainly what happened, because "why does my advice look different today" has to be answerable.
 *
 * <p>No network, and no Spring. The provider is a stub that returns scripted answers, which is what makes
 * the retry and fallback paths testable at all: they are the paths a real provider only takes when the
 * third party is having a bad day.
 */
class AiAdviceSourceTest {

    private static final String PROVIDER_NAME = "stub-provider";

    /** The model name the stub reports, which becomes the advice's source when a call succeeds. */
    private static final String MODEL = "test-model";

    private static final AnalysisFacts FACTS =
            AnalysisFixtures.facts(AnalysisFixtures.STRONG_RESUME);

    @Test
    @DisplayName("the findings these tests script responses against hold what the tests assume")
    void theFixtureHoldsWhatTheOtherTestsAssume() {
        // Every scripted response below names a gap so it can survive the sanitiser. Stated once here
        // so a catalogue change reads as a catalogue change rather than as six broken fallback tests.
        assertThat(FACTS.skills().gaps()).isNotEmpty();
        assertThat(aGap()).isNotBlank();
    }

    @Test
    @DisplayName("a complete response is the advice the user gets, credited to the model")
    void aGoodResponseIsUsedAsWritten() {
        StubProvider provider = new StubProvider(replies(completeResponse()));

        AiAdvice advice = sourceFor(provider, 1).adviseOn(FACTS, AnalysisFixtures.POSTING);

        assertThat(advice.improvements()).singleElement().satisfies(item ->
                assertThat(item.title()).isEqualTo("Quantify the migration"));
        assertThat(advice.skillGaps()).singleElement().satisfies(gap ->
                assertThat(gap.slug()).isEqualTo(aGap()));
        // No top-up suffix: the response was complete, so nothing was written from the findings.
        assertThat(advice.source()).isEqualTo(MODEL);
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("the provider is handed the rules, the findings, and nothing the ceiling forbids")
    void theProviderIsHandedTheConfiguredPrompt() {
        StubProvider provider = new StubProvider(replies(completeResponse()));
        Ai settings = TestProperties.ai(PROVIDER_NAME, "a-key");

        new AiAdviceSource(provider, new OfflineAdviceSource(), settings)
                .adviseOn(FACTS, AnalysisFixtures.POSTING);

        // Whole-object equality against the builder, which pins two things at once: the configured
        // ceiling is the one that reaches the builder, and this class does not edit the prompt on the
        // way past. A source that quietly appended anything of its own would show up here.
        assertThat(provider.lastPrompt).isEqualTo(
                AnalysisPrompts.build(FACTS, AnalysisFixtures.POSTING,
                        settings.maxPromptCharacters()));
        assertThat(provider.lastPrompt.system()).isEqualTo(AnalysisPrompts.SYSTEM);
        assertThat(provider.lastPrompt.characterCount())
                .isLessThanOrEqualTo(settings.maxPromptCharacters());
    }

    @Test
    @DisplayName("a provider that is down costs the prose and nothing else")
    void aTransportFailureFallsBackToTheOfflineWriter() {
        StubProvider provider = new StubProvider(List.of(unavailable()));

        AiAdvice advice = sourceFor(provider, 0).adviseOn(FACTS, AnalysisFixtures.POSTING);

        assertThat(advice.source())
                .isEqualTo("offline writer (no model called), after stub-provider was unavailable");
        assertThatAdviceIsComplete(advice);
        // Zero retries means one attempt. Worth asserting: a retry budget that is ignored is a bill.
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("a transient failure is retried, and a later good answer is the one used")
    void aTransientFailureIsRetried() {
        StubProvider provider = new StubProvider(
                List.of(unavailable(), unavailable(), reply(completeResponse())));

        AiAdvice advice = sourceFor(provider, 2).adviseOn(FACTS, AnalysisFixtures.POSTING);

        // The case the retry budget is for: rate limits and cold starts recover within seconds, and
        // falling back on the first one would serve offline advice to a user whose model was fine.
        assertThat(advice.source()).isEqualTo(MODEL);
        assertThat(provider.calls).isEqualTo(3);
    }

    @Test
    @DisplayName("retrying stops at the configured budget rather than at the provider's patience")
    void retriesAreBounded() {
        StubProvider provider = new StubProvider(List.of(unavailable()));

        AiAdvice advice = sourceFor(provider, 2).adviseOn(FACTS, AnalysisFixtures.POSTING);

        assertThat(provider.calls).isEqualTo(3);
        assertThat(advice.source()).endsWith(", after stub-provider was unavailable");
        assertThatAdviceIsComplete(advice);
    }

    @Test
    @DisplayName("a response about skills the posting never mentioned is a failed attempt")
    void aResponseThatSurvivesNothingIsAFailure() {
        // The sanitiser drops every one of these, leaving empty advice. Serving that would be worse
        // than a provider outage: the user sees an analysis with no words in it and no reason given.
        StubProvider provider = new StubProvider(replies("""
                {"skillGaps": [{"skill": "rust", "detail": "The posting never asked for this."},
                   {"skill": "cobol", "detail": "Nor this."}]}
                """));

        AiAdvice advice = sourceFor(provider, 0).adviseOn(FACTS, AnalysisFixtures.POSTING);

        assertThat(advice.source()).endsWith(", after stub-provider was unavailable");
        assertThatAdviceIsComplete(advice);
        assertThat(advice.skillGaps()).extracting(AiAdvice.GapNote::slug)
                .doesNotContain("rust", "cobol");
    }

    @Test
    @DisplayName("a bug in a provider is still not a reason to lose the analysis")
    void anUnexpectedExceptionAlsoFallsBack() {
        // Providers are asked for two exception types. A third one means a bug in an HTTP client, a
        // JSON library, or this package — and the user's scores were computed before any of that ran.
        StubProvider provider = new StubProvider(List.of(broken()));

        AiAdvice advice = sourceFor(provider, 0).adviseOn(FACTS, AnalysisFixtures.POSTING);

        assertThat(advice.source()).endsWith(", after stub-provider was unavailable");
        assertThatAdviceIsComplete(advice);
    }

    @Test
    @DisplayName("a partial response keeps what the model wrote and fills the rest from the findings")
    void aPartialResponseIsToppedUp() {
        // The common partial failure: a model that writes good improvements and stops. Discarding it
        // for the sake of one code path would throw away the part the user came for.
        StubProvider provider = new StubProvider(replies("""
                {"overallFeedback": "A strong backend resume that undersells its data work.",
                 "improvements": [{"title": "Quantify the migration", "detail": "Say how many rows.",
                   "priority": "HIGH", "section": "EXPERIENCE"}]}
                """));

        AiAdvice advice = sourceFor(provider, 0).adviseOn(FACTS, AnalysisFixtures.POSTING);

        assertThat(advice.overallFeedback())
                .isEqualTo("A strong backend resume that undersells its data work.");
        assertThat(advice.improvements()).singleElement().satisfies(item ->
                assertThat(item.title()).isEqualTo("Quantify the migration"));
        // The lists the model left out are the offline writer's, verbatim.
        AiAdvice computed = new OfflineAdviceSource().adviseOn(FACTS, AnalysisFixtures.POSTING);
        assertThat(advice.skillGaps()).isEqualTo(computed.skillGaps());
        assertThat(advice.sectionNotes()).isEqualTo(computed.sectionNotes());
        // Said out loud, because "some of this was written by a model and some was not" is the kind of
        // thing that gets forgotten by the time somebody is reading a stored analysis.
        assertThat(advice.source()).isEqualTo(MODEL + " (some sections written from the computed "
                + "findings)");
    }

    @Test
    @DisplayName("a model that disagrees about the scores changes nothing but its own recorded opinion")
    void theModelsScoresNeverBecomeTheProductsScores() {
        AiAdvice wild = adviceFrom(responseWithScores(
                "\"overallScore\": 100, \"atsScore\": 3, \"jobMatchScore\": 99, "));
        AiAdvice quiet = adviceFrom(completeResponse());

        // Kept, because a model that has been handed the arithmetic and still lands forty points away
        // is worth a log line. Used for nothing, because the arithmetic is what the product reports.
        assertThat(wild.modelScores()).containsEntry("overallScore", 100).containsEntry("atsScore", 3);
        assertThat(wild.overallFeedback()).isEqualTo(quiet.overallFeedback());
        assertThat(wild.improvements()).isEqualTo(quiet.improvements());
        assertThat(wild.skillGaps()).isEqualTo(quiet.skillGaps());
        assertThat(wild.source()).isEqualTo(quiet.source());
        assertThat(quiet.modelScores()).isEmpty();
    }

    @Test
    @DisplayName("the source names the provider, so a stored analysis says where its words came from")
    void describeNamesTheProvider() {
        assertThat(sourceFor(new StubProvider(replies(completeResponse())), 0).describe())
                .isEqualTo(PROVIDER_NAME);
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** Advice from a single scripted response, with retries off. */
    private static AiAdvice adviceFrom(String responseText) {
        return sourceFor(new StubProvider(replies(responseText)), 0)
                .adviseOn(FACTS, AnalysisFixtures.POSTING);
    }

    private static AiAdviceSource sourceFor(AiProvider provider, int maxRetries) {
        return new AiAdviceSource(provider, new OfflineAdviceSource(),
                TestProperties.ai(PROVIDER_NAME, "a-key", maxRetries));
    }

    /**
     * A response complete enough that nothing is topped up from the findings.
     *
     * <p>Complete means the four things {@code topUp} looks for: feedback, an improvement, a gap the
     * sanitiser will accept, and a section note.
     */
    private static String completeResponse() {
        return responseWithScores("");
    }

    /**
     * The same response with a scores block spliced into the front.
     *
     * @param scores JSON key-value pairs including their trailing comma, or empty for no scores at all
     */
    private static String responseWithScores(String scores) {
        return """
                {%s"overallFeedback": "A strong backend resume that undersells its data work.",
                 "improvements": [{"title": "Quantify the migration", "detail": "Say how many rows.",
                   "priority": "HIGH", "section": "EXPERIENCE"}],
                 "skillGaps": [{"skill": "%s", "detail": "Named in the posting, absent here.",
                   "priority": "MEDIUM"}],
                 "sectionScores": [{"section": "SUMMARY", "note": "Names the role, not the evidence."}]}
                """.formatted(scores, aGap());
    }

    /** The first computed gap. Which one it is does not matter; that it is real is the point. */
    private static String aGap() {
        return FACTS.skills().gaps().get(0).slug();
    }

    private static List<Supplier<AiCompletion>> replies(String responseText) {
        return List.of(reply(responseText));
    }

    private static Supplier<AiCompletion> reply(String responseText) {
        return () -> new AiCompletion(responseText, MODEL);
    }

    private static Supplier<AiCompletion> unavailable() {
        return () -> {
            throw new AiUnavailableException("The provider returned 429.");
        };
    }

    /** A provider failing in a way its contract does not allow for — a bug rather than an outage. */
    private static Supplier<AiCompletion> broken() {
        return () -> {
            throw new IllegalStateException("a connection pool that was closed underneath us");
        };
    }

    /**
     * Asserts the user got everything, whichever writer produced it.
     *
     * <p>This is the whole point of the fallback and the reason it is asserted in every failure case
     * rather than once: "it did not throw" is not the requirement. The requirement is that the analysis
     * page has advice in every panel.
     */
    private static void assertThatAdviceIsComplete(AiAdvice advice) {
        assertThat(advice.overallFeedback()).isNotBlank();
        assertThat(advice.improvements()).isNotEmpty();
        assertThat(advice.skillGaps()).isNotEmpty();
        assertThat(advice.recommendedProjects()).isNotEmpty();
        assertThat(advice.learningRecommendations()).isNotEmpty();
        assertThat(advice.sectionNotes()).isNotEmpty();
    }

    /**
     * A provider that answers from a script.
     *
     * <p>The last entry repeats, so "always fails" is one entry rather than one per attempt, and a
     * test that changes its retry budget does not also have to change the length of its script.
     */
    private static final class StubProvider implements AiProvider {

        private final List<Supplier<AiCompletion>> answers;

        private int calls;
        private AiPrompt lastPrompt;

        private StubProvider(List<Supplier<AiCompletion>> answers) {
            this.answers = List.copyOf(answers);
        }

        @Override
        public AiCompletion complete(AiPrompt prompt) {
            calls++;
            lastPrompt = prompt;
            // Checked here rather than in every test: a provider handed no prompt is a bug this stub
            // should fail on rather than hide behind a scripted answer.
            if (prompt == null || prompt.user().isBlank()) {
                throw new AssertionError("The provider was called with no prompt.");
            }
            return answers.get(Math.min(calls - 1, answers.size() - 1)).get();
        }

        @Override
        public String name() {
            return PROVIDER_NAME;
        }
    }
}
