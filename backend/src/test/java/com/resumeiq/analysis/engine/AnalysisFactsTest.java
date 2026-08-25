package com.resumeiq.analysis.engine;

import com.resumeiq.support.AnalysisFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary between the half of the analysis that is arithmetic and the half that is words.
 *
 * <p>{@code AnalysisFacts} is a small record over five larger ones, so most of what is worth testing here
 * is about the guarantees the rest of the phase leans on. The advice layer is handed this object and
 * nothing else, which is the reason a model cannot introduce a fact: if a claim is not derivable from
 * something in here, the sanitiser has nothing to match it against and drops it. That only holds if the
 * derived views are actually complete and actually partition the way the callers assume.
 */
class AnalysisFactsTest {

    private static final AnalysisFacts FACTS =
            AnalysisFixtures.facts(AnalysisFixtures.STRONG_RESUME);

    @Test
    @DisplayName("the whole pipeline runs from two strings, with no database and no network")
    void theWholePipelineRunsFromTwoStrings() {
        // Not a trivial assertion. Every scoring test, section test and advice test in this phase runs
        // in milliseconds because this is a pure function, and it is a pure function because the skill
        // catalogue is passed in rather than looked up. That decision is what this test protects.
        assertThat(FACTS.roleTitle()).isEqualTo(AnalysisFixtures.ROLE);
        assertThat(FACTS.posting().skills()).isNotEmpty();
        assertThat(FACTS.resume().text()).contains("Priya Raman");
        assertThat(FACTS.skills().demanded()).isNotEmpty();
        assertThat(FACTS.keywords()).isNotEmpty();
        assertThat(FACTS.scores().overall()).isBetween(0, 100);
        assertThat(FACTS.sections()).hasSize(8);
    }

    @Test
    @DisplayName("the same documents always produce the same numbers")
    void theSameDocumentsProduceTheSameNumbers() {
        // A score somebody disputes has to be reproducible on demand. If this ever fails, some part of
        // the engine has picked up an iteration order, a clock or a hash it should not depend on.
        AnalysisFacts again = AnalysisFixtures.facts(AnalysisFixtures.STRONG_RESUME);

        assertThat(again.scores()).isEqualTo(FACTS.scores());
        assertThat(again.sections()).isEqualTo(FACTS.sections());
        assertThat(again.keywords()).isEqualTo(FACTS.keywords());
    }

    @Test
    @DisplayName("the two keyword views partition the whole set with nothing lost or counted twice")
    void theKeywordViewsPartitionTheWholeSet() {
        // The sanitiser decides what keyword advice a user may see by checking membership of
        // absentKeywords(). A term that fell out of both views would be a term the model can never
        // suggest and the user can never be told about, and nothing else in the suite would notice.
        assertThat(FACTS.matchedKeywords()).isNotEmpty();
        assertThat(FACTS.absentKeywords()).isNotEmpty();
        assertThat(FACTS.matchedKeywords().size() + FACTS.absentKeywords().size())
                .isEqualTo(FACTS.keywords().size());
        assertThat(FACTS.matchedKeywords()).allSatisfy(verdict ->
                assertThat(verdict.isMatched()).isTrue());
        assertThat(FACTS.absentKeywords()).allSatisfy(verdict ->
                assertThat(verdict.isMatched()).isFalse());
        assertThat(FACTS.matchedKeywords()).doesNotContainAnyElementsOf(FACTS.absentKeywords());
    }

    @Test
    @DisplayName("matched terms come first, so the working half of the picture leads")
    void matchedTermsComeFirst() {
        // Both the prompt and the UI read this list in order. Interleaving them would put "you already
        // use this" next to "you do not use this" with only a flag to tell them apart.
        int firstAbsent = FACTS.keywords().indexOf(FACTS.absentKeywords().get(0));
        int lastMatched = FACTS.keywords().indexOf(
                FACTS.matchedKeywords().get(FACTS.matchedKeywords().size() - 1));

        assertThat(lastMatched).isLessThan(firstAbsent);
    }

    @Test
    @DisplayName("an empty resume is thin, and a full pair is not")
    void anEmptyResumeIsThin() {
        assertThat(AnalysisFixtures.facts("").isThin()).isTrue();
        assertThat(FACTS.isThin()).isFalse();
    }

    @Test
    @DisplayName("a posting with prose but no technology is still worth advising on")
    void aPostingWithNoTechnologyIsNotThin() {
        AnalysisFacts noSkills = AnalysisFixtures.facts(AnalysisFixtures.STRONG_RESUME,
                AnalysisFixtures.POSTING_WITHOUT_SKILLS, "Operations Coordinator");

        // Thinness is about having nothing to compare, not about having no technology to compare. This
        // posting names no tool, but it has terms and requirements, so the resume can still be measured
        // against it — and telling that user "your documents are too short" would be wrong.
        assertThat(noSkills.posting().skills()).isEmpty();
        assertThat(noSkills.posting().keywords()).isNotEmpty();
        assertThat(noSkills.isThin()).isFalse();
    }

    @Test
    @DisplayName("the weakest sections are every section, worst first")
    void weakestSectionsAreEverySectionWorstFirst() {
        // Every section rather than a top few, because the caller decides how many to act on. A view
        // that silently kept three would make the improvement cap look like a scoring decision.
        assertThat(FACTS.weakestSections())
                .hasSameSizeAs(FACTS.sections())
                .containsExactlyInAnyOrderElementsOf(FACTS.sections())
                .isSortedAccordingTo((left, right) -> Integer.compare(left.score(), right.score()));
    }

    @Test
    @DisplayName("the lists a caller is handed cannot be edited underneath the analysis")
    void theListsAreImmutable() {
        // These records are shared: the same facts object goes to the prompt builder, the sanitiser and
        // the offline writer. One of them mutating a list would change what the others saw, and the
        // resulting bug would look like a scoring bug.
        assertThatThrownBy(() -> FACTS.keywords().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> FACTS.sections().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the resume text is carried through whole, because the provider needs all of it")
    void theResumeTextIsCarriedThroughWhole() {
        // The one piece of raw user content in this record. It is sent to the provider, and it is never
        // logged and never returned by the API — a rule the API tests in Phase 7 hold up their end of.
        // Asserted by its ends rather than by equality with the fixture, because line endings and
        // trailing whitespace are normalised on the way in and that is not a change worth pinning.
        assertThat(FACTS.resume().text())
                .startsWith("Priya Raman")
                .contains("priya.raman@example.test")
                .endsWith("B.E. Computer Engineering, Pune University, 2019");
    }
}
