package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.analysis.SkillImportance;
import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.recommendation.Priority;
import com.resumeiq.support.AnalysisFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The writer that runs when no model does.
 *
 * <p>Two things are being tested here, and the second one is the interesting one. The first is that the
 * offline writer produces advice at all, since it is what a user with no API key gets and what every user
 * gets when a provider call fails. The second is that the advice is honest in the specific ways the spec
 * demands: it invents no link, it suggests no keyword it cannot place, and it never tells anyone to spend
 * a weekend on a technology a posting mentioned once in passing. Those are properties of code here rather
 * than instructions in a prompt, which is the only reason they can be asserted.
 *
 * <p>Every case runs against real computed findings, because the writer's entire input is those findings.
 * Handing it a hand-built {@code AnalysisFacts} would test a shape rather than a behaviour.
 */
class OfflineAdviceSourceTest {

    /** Priya against Northwind: every requirement demonstrated, four preferred skills missing. */
    private static final AnalysisFacts STRONG =
            AnalysisFixtures.facts(AnalysisFixtures.STRONG_RESUME);

    /** Rahul against Northwind: stated requirements missing, and the ones present only asserted. */
    private static final AnalysisFacts THIN = AnalysisFixtures.facts(AnalysisFixtures.THIN_RESUME);

    /**
     * A posting whose only technologies appear in company boilerplate and a benefits list.
     *
     * <p>Both headings are read as passing mentions, which makes every skill here a
     * {@code NICE_TO_HAVE} — the case where recommending a project would be actively bad advice.
     */
    private static final String PASSING_MENTION_POSTING = """
            Operations Engineer

            About us
            We are a team of nine and our platform happens to run on Kubernetes.

            Benefits
            A conference budget, and time to learn Docker if that interests you.
            """;

    private final OfflineAdviceSource writer = new OfflineAdviceSource();

    @Test
    @DisplayName("a user with no API key still gets advice in every category")
    void adviceIsWrittenInEveryCategoryWithNoModelInvolved() {
        AiAdvice advice = writer.adviseOn(STRONG, AnalysisFixtures.POSTING);

        assertThat(advice.overallFeedback()).isNotBlank();
        assertThat(advice.improvements()).isNotEmpty();
        assertThat(advice.skillGaps()).isNotEmpty();
        assertThat(advice.recommendedProjects()).isNotEmpty();
        assertThat(advice.learningRecommendations()).isNotEmpty();
        assertThat(advice.suggestedKeywords()).isNotEmpty();
        assertThat(advice.sectionNotes()).hasSize(ResumeSection.values().length);
        assertThat(advice.isEmpty()).isFalse();
        assertThat(advice.itemCount()).isPositive();
        // No opinion about the scores, because this writer is not a second opinion — it is the same
        // arithmetic that produced them, described in words.
        assertThat(advice.modelScores()).isEmpty();
        assertThat(advice.source()).isEqualTo("offline writer (no model called)");
    }

    @Test
    @DisplayName("the source and the description both say plainly that no model was called")
    void theSourceSaysNoModelWasCalled() {
        // The first question anybody asks when the advice looks thin is "did this actually call a
        // model?". It should be answerable from the stored analysis, not by reading the configuration.
        assertThat(writer.describe()).isEqualTo("offline writer (no model called)");
        assertThat(writer.adviseOn(THIN, AnalysisFixtures.POSTING).source())
                .isEqualTo(writer.describe());
    }

    @Test
    @DisplayName("the writer works from the findings alone and never reads the raw posting text")
    void theRawPostingTextIsNotUsed() {
        // Worth pinning rather than assuming. The findings are the sanitised, structured half of the
        // analysis; the raw text is the half nothing downstream should be re-deriving facts from. If a
        // future edit starts reading it here, the two results stop matching and this test says so.
        assertThat(writer.adviseOn(STRONG, null))
                .isEqualTo(writer.adviseOn(STRONG, AnalysisFixtures.POSTING));
    }

    @Test
    @DisplayName("the feedback describes the skill match without naming a score band")
    void theFeedbackDescribesTheMatchWithoutNamingABand() {
        String feedback = writer.adviseOn(STRONG, AnalysisFixtures.POSTING).overallFeedback();

        assertThat(feedback).contains("On skills this is a strong match");
        // The band is a pure function of the score and the frontend owns that function. A second copy
        // of the thresholds in this sentence would be a source of truth that disagrees the day one of
        // them moves, and the disagreement would be visible to the user.
        assertThat(feedback).doesNotContain("Excellent Match", "Strong Match", "Moderate Match",
                "Needs Improvement", "Needs Major Improvement");
    }

    @Test
    @DisplayName("a stated requirement that is missing leads the feedback")
    void aCriticalGapLeadsTheFeedback() {
        String feedback = writer.adviseOn(THIN, AnalysisFixtures.POSTING).overallFeedback();

        assertThat(feedback)
                .startsWith("The biggest thing standing between this resume and this role is")
                .contains("the posting asks for it and the resume does not mention it");
    }

    @Test
    @DisplayName("documents too thin to compare are said to be thin rather than scored confidently")
    void thinDocumentsAreCalledThin() {
        AiAdvice advice = writer.adviseOn(AnalysisFixtures.facts(""), AnalysisFixtures.POSTING);

        assertThat(advice.overallFeedback())
                .contains("not enough in one or both documents to compare them properly")
                .contains("treat them as provisional");
    }

    @Test
    @DisplayName("improvements are the things the engine measured, capped at six")
    void improvementsAreTheMeasuredThingsCappedAtSix() {
        AiAdvice advice = writer.adviseOn(THIN, AnalysisFixtures.POSTING);

        // Six is a reading limit. A list of eleven fixes is a list nobody starts, and the ones that
        // matter are at the top anyway: contact details, then absent sections, then unquantified work.
        assertThat(advice.improvements()).hasSize(6);
        assertThat(advice.improvements()).extracting(AiAdvice.Improvement::title)
                .contains("Add an email address to the top of the resume",
                        "Add a projects section",
                        "Put numbers on your strongest bullets");
        assertThat(advice.improvements()).allSatisfy(item -> {
            assertThat(item.title()).isNotBlank();
            assertThat(item.detail()).isNotBlank();
            assertThat(item.priority()).isNotNull();
        });
    }

    @Test
    @DisplayName("no learning resource link is ever invented")
    void noLinkIsEverFabricated() {
        // This writer has no way to know a URL exists, so it does not offer one. A link that 404s is
        // worse than no link: it makes a reader doubt the advice printed next to it.
        assertThat(writer.adviseOn(STRONG, AnalysisFixtures.POSTING).learningRecommendations())
                .isNotEmpty()
                .allSatisfy(topic -> {
                    assertThat(topic.resourceUrl()).isNull();
                    assertThat(topic.title()).startsWith("Learn ");
                    assertThat(topic.detail()).contains("explain a decision you made using it");
                });
    }

    @Test
    @DisplayName("every computed gap is explained, and nothing that is not a gap is")
    void everyGapIsExplainedAndOnlyGaps() {
        AiAdvice advice = writer.adviseOn(STRONG, AnalysisFixtures.POSTING);

        assertThat(advice.skillGaps()).extracting(AiAdvice.GapNote::slug)
                .containsExactlyElementsOf(STRONG.skills().gaps().stream()
                        .map(verdict -> verdict.slug())
                        .toList());
        assertThat(advice.skillGaps()).allSatisfy(gap -> assertThat(gap.detail()).isNotBlank());
    }

    @Test
    @DisplayName("a preferred skill that is missing is framed as an opportunity, not a failure")
    void aPreferredGapIsFramedAsAnOpportunity() {
        assertThat(STRONG.skills().gaps()).isNotEmpty().allSatisfy(gap ->
                assertThat(gap.importance()).isEqualTo(SkillImportance.IMPORTANT));

        assertThat(writer.adviseOn(STRONG, AnalysisFixtures.POSTING).skillGaps())
                .allSatisfy(gap -> {
                    assertThat(gap.detail()).contains("as a preference rather than a requirement");
                    assertThat(gap.priority()).isEqualTo(Priority.MEDIUM);
                });
    }

    @Test
    @DisplayName("a project is suggested per gap, naming only that gap")
    void eachProjectClosesOneNamedGap() {
        AiAdvice advice = writer.adviseOn(STRONG, AnalysisFixtures.POSTING);

        assertThat(advice.recommendedProjects()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(advice.recommendedProjects()).allSatisfy(idea -> {
            assertThat(idea.title()).startsWith("A project that evidences ");
            assertThat(idea.skillSlugs()).hasSize(1);
            assertThat(idea.detail()).isNotBlank();
        });
    }

    @Test
    @DisplayName("a skill mentioned in passing is explained but never something to go and build for")
    void aPassingMentionIsNotWorthAProject() {
        AnalysisFacts passing = AnalysisFixtures.facts(AnalysisFixtures.THIN_RESUME,
                PASSING_MENTION_POSTING, "Operations Engineer");
        // Stated as a precondition so a parser change reads as a parser change rather than as a
        // failure of this rule.
        assertThat(passing.skills().gaps()).isNotEmpty().allSatisfy(gap ->
                assertThat(gap.importance()).isEqualTo(SkillImportance.NICE_TO_HAVE));

        AiAdvice advice = writer.adviseOn(passing, PASSING_MENTION_POSTING);

        // Telling somebody to containerise a project because a benefits paragraph mentioned Docker is
        // advice that costs them a weekend and gains them nothing. The gap is still reported — it is
        // true — but with no project and no learning plan attached to it.
        assertThat(advice.skillGaps()).isNotEmpty().allSatisfy(gap -> {
            assertThat(gap.detail()).contains("in passing")
                    .contains("not worth reshaping your resume for");
            assertThat(gap.priority()).isEqualTo(Priority.LOW);
        });
        assertThat(advice.recommendedProjects()).isEmpty();
        assertThat(advice.learningRecommendations()).isEmpty();
    }

    @Test
    @DisplayName("with no experience and no projects there is no keyword advice at all")
    void keywordAdviceNeedsSomewhereHonestToPutIt() {
        AnalysisFacts skillsOnly = AnalysisFixtures.facts(AnalysisFixtures.SKILLS_ONLY_RESUME);

        // The alternative would be to suggest terms with a placement of "somewhere in your resume",
        // which is a list of words to paste in — the exact thing the spec forbids. The right advice for
        // this resume is the structural improvement, and that is what the improvements list carries.
        assertThat(writer.adviseOn(skillsOnly, AnalysisFixtures.POSTING).suggestedKeywords())
                .isEmpty();
        assertThat(writer.adviseOn(STRONG, AnalysisFixtures.POSTING).suggestedKeywords())
                .isNotEmpty();
    }

    @Test
    @DisplayName("every keyword suggestion names a real section and an honest condition for using it")
    void everyKeywordSuggestionCarriesAPlacement() {
        AiAdvice advice = writer.adviseOn(STRONG, AnalysisFixtures.POSTING);

        assertThat(advice.suggestedKeywords()).hasSizeLessThanOrEqualTo(6).allSatisfy(keyword -> {
            assertThat(keyword.term()).isNotBlank();
            // "If it does not, leave the term out" is the sentence that makes this advice rather than
            // stuffing. Without it the suggestion is an instruction to claim something.
            assertThat(keyword.placement())
                    .contains("If your experience section already describes work of this kind")
                    .contains("If it does not, leave the term out");
        });
    }

    @Test
    @DisplayName("the section notes are the engine's own, not a second set written from them")
    void theSectionNotesAreTheEnginesOwn() {
        AiAdvice advice = writer.adviseOn(STRONG, AnalysisFixtures.POSTING);

        assertThat(advice.sectionNotes()).extracting(AiAdvice.SectionNote::section)
                .containsExactly(ResumeSection.values());
        assertThat(advice.sectionNotes()).extracting(AiAdvice.SectionNote::note)
                .containsExactlyElementsOf(STRONG.sections().stream()
                        .map(review -> review.note())
                        .toList());
    }
}
