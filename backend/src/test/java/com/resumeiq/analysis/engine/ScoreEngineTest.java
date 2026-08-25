package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.KeywordKind;
import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.analysis.SkillImportance;
import com.resumeiq.analysis.SkillStatus;
import com.resumeiq.jobdescription.parse.ExperienceDemand;
import com.resumeiq.jobdescription.parse.JobPostingParser;
import com.resumeiq.jobdescription.parse.PostingInsight;
import com.resumeiq.skill.SkillCategory;
import com.resumeiq.skill.SkillIndex;
import com.resumeiq.user.ExperienceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The six scores.
 *
 * <p>Every input here is hand-built rather than parsed from a document, which is the only way to test
 * a scoring function honestly: a test that runs a resume fixture through the whole pipeline and asserts
 * "the score is 71" tells you that the number changed, not which weight changed it. These tests pin the
 * arithmetic one branch at a time.
 *
 * <h2>Why the ATS budget test matters most</h2>
 *
 * <p>{@link #atsIsAHundredPointBudget()} catches the mistake this design invites. The ATS score is a
 * sum of six independent components, so adding a seventh — or nudging one weight up to make a
 * particular case score better — produces a total above 100 that then gets clamped. That looks fine on
 * the case being tuned and quietly compresses every other resume into the top of the scale.
 */
class ScoreEngineTest {

    /** The six components the ATS score is made of, in the order their notes are written. */
    private static final List<String> ATS_NOTES = List.of("Contact details", "Recognisable sections",
            "Bullet structure", "Quantified impact", "Length", "Clean layout");

    /** No skills demanded: the unmeasurable branch, for tests that are not about skills. */
    private static final SkillComparison.Comparison NO_SKILLS =
            new SkillComparison.Comparison(List.of(), List.of());

    @Test
    @DisplayName("the ATS budget is exactly 100 points and a flawless document earns all of them")
    void atsIsAHundredPointBudget() {
        ScoreCard card = ScoreEngine.score(posting(demand(4, ExperienceLevel.MID, "4+ years")),
                flawless(), NO_SKILLS, List.of());

        assertThat(card.ats()).isEqualTo(ScoreCard.MAX);

        // A real 100 rather than a clamped overflow. If a component is added or a weight raised, the
        // budget stops summing to 100 and this fails — where the assertion above would not.
        int budget = ATS_NOTES.stream().mapToInt(label -> noteFor(card, label).outOf()).sum();
        assertThat(budget).isEqualTo(100);
    }

    @Test
    @DisplayName("an empty resume scores the floor, and the floor is not zero")
    void anEmptyResumeScoresTheFloor() {
        ScoreCard card = ScoreEngine.score(posting(ExperienceDemand.unknown()),
                ResumeInsight.empty(), NO_SKILLS, List.of());

        // No contact (0 of 15), no sections (0 of 32), no bullets (0 of 12), nothing quantified
        // (0 of 16), no words so the length band bottoms out (2 of 15), and a clean layout, which an
        // empty document trivially has (10 of 10).
        assertThat(card.ats()).isEqualTo(12);
        assertThat(card.overall()).isGreaterThan(0);
    }

    @Test
    @DisplayName("every score component leaves a note, in the order the notes are read")
    void everyComponentLeavesANote() {
        ScoreCard card = ScoreEngine.score(posting(demand(4, ExperienceLevel.MID, "4+ years")),
                flawless(), comparison(SkillStatus.STRONG), List.of(matched("java", 3)));

        assertThat(card.notes()).extracting(ScoreNote::label).containsExactly(
                "Contact details", "Recognisable sections", "Bullet structure", "Quantified impact",
                "Length", "Clean layout", "Skills match", "Keyword coverage", "Experience",
                "Job match", "Overall");
        assertThat(card.notes()).allSatisfy(note -> assertThat(note.comment()).isNotBlank());
    }

    @Test
    @DisplayName("layout artefacts cost the layout budget and nothing else")
    void layoutArtefactsCostOnlyTheLayoutBudget() {
        int clean = atsFor(shape(620, 40, 10, 6, false));
        int wrecked = atsFor(shape(620, 40, 10, 6, true));

        assertThat(clean - wrecked).isEqualTo(10);
    }

    @Test
    @DisplayName("length is a band, and both ends of it are a finding")
    void lengthIsScoredAsABand() {
        assertThat(atsFor(shape(620, 40, 10, 6, false))).isEqualTo(100);   // the readable range
        assertThat(atsFor(shape(1_000, 60, 10, 6, false))).isEqualTo(96);  // wide but tolerable
        assertThat(atsFor(shape(200, 20, 10, 6, false))).isEqualTo(91);    // thin
        assertThat(atsFor(shape(2_400, 90, 10, 6, false))).isEqualTo(87);  // nobody reads this far
    }

    @Test
    @DisplayName("bullets and numbers are scored in steps, so partial progress shows")
    void bulletsAndNumbersAreScoredInSteps() {
        // Steps rather than one threshold, on purpose: somebody who rewrites three of eight bullets
        // should see the score move, or they conclude the advice does not work and stop taking it.
        assertThat(atsFor(shape(620, 40, 8, 6, false))).isEqualTo(100);
        assertThat(atsFor(shape(620, 40, 5, 6, false))).isEqualTo(97);
        assertThat(atsFor(shape(620, 40, 2, 6, false))).isEqualTo(93);
        assertThat(atsFor(shape(620, 40, 0, 6, false))).isEqualTo(88);

        assertThat(atsFor(shape(620, 40, 10, 4, false))).isEqualTo(100);
        assertThat(atsFor(shape(620, 40, 10, 2, false))).isEqualTo(96);
        assertThat(atsFor(shape(620, 40, 10, 1, false))).isEqualTo(91);
        assertThat(atsFor(shape(620, 40, 10, 0, false))).isEqualTo(84);
    }

    @Test
    @DisplayName("skill coverage is weighted by importance and by how well the skill is evidenced")
    void skillCoverageIsWeightedTwice() {
        assertThat(skillsFor(comparison(SkillStatus.STRONG))).isEqualTo(100);
        // A complete skills list with nothing demonstrated lands in the seventies rather than at 50.
        assertThat(skillsFor(comparison(SkillStatus.PARTIAL))).isEqualTo(70);
        assertThat(skillsFor(comparison(SkillStatus.MISSING))).isZero();

        // A demonstrated requirement plus a missing passing mention: 3 of 4 available points.
        SkillComparison.Comparison mixed = new SkillComparison.Comparison(List.of(
                verdict("java", SkillImportance.CRITICAL, SkillStatus.STRONG),
                verdict("docker", SkillImportance.NICE_TO_HAVE, SkillStatus.MISSING)), List.of());
        assertThat(skillsFor(mixed)).isEqualTo(75);

        // The reverse pairing, which is the case an unweighted count would score identically.
        SkillComparison.Comparison inverted = new SkillComparison.Comparison(List.of(
                verdict("java", SkillImportance.CRITICAL, SkillStatus.MISSING),
                verdict("docker", SkillImportance.NICE_TO_HAVE, SkillStatus.STRONG)), List.of());
        assertThat(skillsFor(inverted)).isEqualTo(25);
    }

    @Test
    @DisplayName("a posting naming no known skill is neutral, not zero")
    void unmeasurableSkillsAreNeutral() {
        ScoreCard card = ScoreEngine.score(posting(ExperienceDemand.unknown()), flawless(),
                NO_SKILLS, List.of());

        assertThat(card.skillsMatch()).isEqualTo(55);
        assertThat(noteFor(card, "Skills match").isScored()).isFalse();
        assertThat(noteFor(card, "Skills match").comment())
                .contains("could not be measured")
                .contains("not a finding about the resume");
    }

    @Test
    @DisplayName("keyword coverage is weighted by where the posting used the term")
    void keywordCoverageIsWeighted() {
        // One heavily weighted term matched and one light term missed scores far above half.
        assertThat(keywordFor(List.of(matched("java", 5), absent("kubernetes", 1)))).isEqualTo(83);

        assertThat(keywordFor(List.of(matched("java", 3), matched("mysql", 3)))).isEqualTo(100);
        assertThat(keywordFor(List.of(absent("java", 3), absent("mysql", 3)))).isZero();
    }

    @Test
    @DisplayName("a term the posting ranked at zero still counts for something")
    void aZeroWeightedTermStillCounts() {
        // Otherwise a zero-weighted term is invisible: matched or missed, the score is identical, and
        // the user is shown a suggestion that provably cannot change their number.
        assertThat(keywordFor(List.of(matched("java", 0), absent("docker", 0)))).isEqualTo(50);
    }

    @Test
    @DisplayName("a posting with no ranked terms is neutral, not zero")
    void unmeasurableKeywordsAreNeutral() {
        ScoreCard card = ScoreEngine.score(posting(ExperienceDemand.unknown()), flawless(),
                comparison(SkillStatus.STRONG), List.of());

        assertThat(card.keyword()).isEqualTo(60);
        assertThat(noteFor(card, "Keyword coverage").comment()).contains("Neutral score");
    }

    @Test
    @DisplayName("an unstated years requirement is neutral, and the note says an absence is not a zero")
    void anUnstatedRequirementIsNeutral() {
        ScoreCard card = ScoreEngine.score(posting(ExperienceDemand.unknown()), flawless(),
                NO_SKILLS, List.of());

        assertThat(card.experience()).isEqualTo(78);
        assertThat(noteFor(card, "Experience").comment())
                .contains("an unstated requirement is not the same as no requirement");
    }

    @Test
    @DisplayName("a posting that says Senior but names no number is neutral rather than guessed at")
    void seniorityWithoutANumberIsNeutral() {
        // The branch that would have thrown. isStated() is true — a level was read — while minYears
        // is null, because the level came from the title alone. Unboxing that null was a latent
        // NullPointerException, and inventing "Senior means five years" would have been worse than
        // the crash: a threshold nobody stated, then scored against as though somebody had.
        ExperienceDemand titleOnly =
                new ExperienceDemand(null, null, ExperienceLevel.SENIOR, "Senior");
        assertThat(titleOnly.isStated()).isTrue();

        ScoreCard card = ScoreEngine.score(posting(titleOnly), flawless(), NO_SKILLS, List.of());

        assertThat(card.experience()).isEqualTo(70);
        assertThat(noteFor(card, "Experience").comment())
                .contains("Senior")
                .contains("never states a number of years");
    }

    @Test
    @DisplayName("a stated year count always carries a level, so the neutral branch cannot swallow it")
    void aStatedYearCountAlwaysCarriesALevel() {
        // The invariant that makes the engine's branch order safe. ExperienceDemand.detect derives a
        // level from the year count, so a non-null minYears implies isStated(). If that ever stops
        // being true, a posting stating "5+ years" scores the unstated-neutral 78 and the comparison
        // silently never runs — a wrong answer with no error anywhere.
        PostingInsight stated = JobPostingParser.parseWith("Requirements\n5+ years with Java.\n",
                "Engineer", SkillIndex.empty(), 10);

        assertThat(stated.experience().minYears()).isEqualTo(5);
        assertThat(stated.experience().isStated()).isTrue();
    }

    @Test
    @DisplayName("an undatable resume against a stated requirement is neutral, with the fix in the note")
    void anUndatableResumeIsNeutral() {
        ScoreCard card = ScoreEngine.score(posting(demand(4, ExperienceLevel.MID, "4+ years")),
                withYears(Optional.empty()), NO_SKILLS, List.of());

        assertThat(card.experience()).isEqualTo(62);
        assertThat(noteFor(card, "Experience").comment()).contains("adding dates to each role");
    }

    @Test
    @DisplayName("meeting or exceeding the stated years is full marks, and being over is never penalised")
    void meetingTheRequirementIsFullMarks() {
        ExperienceDemand wantsFour = demand(4, ExperienceLevel.MID, "4+ years");

        assertThat(experienceFor(wantsFour, 4)).isEqualTo(100);
        assertThat(experienceFor(wantsFour, 9)).isEqualTo(100);
        assertThat(experienceFor(wantsFour, 25)).isEqualTo(100);
    }

    @Test
    @DisplayName("falling short costs progressively, and never falls below the floor")
    void fallingShortCostsProgressively() {
        ExperienceDemand wantsEight = demand(8, ExperienceLevel.SENIOR, "8+ years");

        assertThat(experienceFor(wantsEight, 7)).isEqualTo(84);
        assertThat(experienceFor(wantsEight, 6)).isEqualTo(70);
        assertThat(experienceFor(wantsEight, 5)).isEqualTo(58);
        assertThat(experienceFor(wantsEight, 4)).isEqualTo(46);
        // The floor. Somebody two years into their career reading a posting that wants eight is not a
        // 5% match to that job, and saying so is both wrong and discouraging to no purpose.
        assertThat(experienceFor(wantsEight, 1)).isEqualTo(30);
        assertThat(experienceFor(demand(30, ExperienceLevel.LEAD, "30 years"), 1)).isEqualTo(30);
    }

    @Test
    @DisplayName("job match is the documented weighted sum of its three components")
    void jobMatchIsTheWeightedSumOfItsComponents() {
        // The weights are asserted here rather than isolated one at a time, because no input drives
        // the experience component to zero — its floor is 30 and its unmeasurable branches are in the
        // sixties and seventies. A test claiming to isolate a weight by zeroing the other two would
        // be quietly asserting the wrong arithmetic.
        for (ScoreCard card : spread()) {
            assertThat(card.jobMatch())
                    .as("job match on %s", card)
                    .isEqualTo(Math.round(0.55 * card.skillsMatch() + 0.25 * card.keyword()
                            + 0.20 * card.experience()));
        }
    }

    @Test
    @DisplayName("a point of skill coverage is worth more than a point of keyword coverage")
    void skillsOutweighKeywords() {
        ExperienceDemand unstated = ExperienceDemand.unknown();
        int neither = ScoreEngine.score(posting(unstated), flawless(),
                comparison(SkillStatus.MISSING), List.of(absent("java", 3))).jobMatch();
        int skillsOnly = ScoreEngine.score(posting(unstated), flawless(),
                comparison(SkillStatus.STRONG), List.of(absent("java", 3))).jobMatch();
        int keywordsOnly = ScoreEngine.score(posting(unstated), flawless(),
                comparison(SkillStatus.MISSING), List.of(matched("java", 3))).jobMatch();

        // The same hundred-point swing, applied to each component in turn.
        assertThat(skillsOnly - neither).isEqualTo(55);
        assertThat(keywordsOnly - neither).isEqualTo(25);
    }

    @Test
    @DisplayName("the overall score weights fit above readability")
    void overallWeightsFitAboveReadability() {
        ScoreCard readableMisfit = ScoreEngine.score(posting(ExperienceDemand.unknown()), flawless(),
                comparison(SkillStatus.MISSING), List.of(absent("kubernetes", 3)));
        ScoreCard unreadableFit = ScoreEngine.score(posting(ExperienceDemand.unknown()),
                ResumeInsight.empty(), comparison(SkillStatus.STRONG), List.of(matched("java", 3)));

        // A perfect fit in an unreadable document beats a flawless document that fits nothing,
        // because fit is the half a person cannot repair by reformatting.
        assertThat(unreadableFit.jobMatch()).isGreaterThan(readableMisfit.jobMatch());
        assertThat(unreadableFit.overall()).isGreaterThan(readableMisfit.overall());

        for (ScoreCard card : spread()) {
            assertThat(card.overall())
                    .as("overall on %s", card)
                    .isEqualTo(Math.round(0.60 * card.jobMatch() + 0.40 * card.ats()));
        }
    }

    @Test
    @DisplayName("every score stays inside 0 to 100")
    void everyScoreIsAPercentage() {
        for (ScoreCard card : spread()) {
            for (String name : ScoreCard.scoreNames()) {
                assertThat(card.byName(name))
                        .as("%s on %s", name, card)
                        .isBetween(ScoreCard.MIN, ScoreCard.MAX);
            }
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    /**
     * A spread of score cards from the extremes to the middle.
     *
     * <p>Shared by the arithmetic tests so each of them checks the formula against several shapes
     * rather than one convenient case. The two ends are the ones that expose a clamp masking a bug.
     */
    private static List<ScoreCard> spread() {
        return List.of(
                ScoreEngine.score(posting(ExperienceDemand.unknown()), ResumeInsight.empty(),
                        comparison(SkillStatus.MISSING), List.of(absent("java", 9))),
                ScoreEngine.score(posting(demand(1, ExperienceLevel.ENTRY, "1 year")), flawless(),
                        comparison(SkillStatus.STRONG), List.of(matched("java", 9))),
                ScoreEngine.score(posting(demand(8, ExperienceLevel.SENIOR, "8+ years")),
                        withShape(shape(430, 30, 5, 2, false)), comparison(SkillStatus.PARTIAL),
                        List.of(matched("java", 4), absent("docker", 2))),
                ScoreEngine.score(posting(new ExperienceDemand(null, null, ExperienceLevel.SENIOR,
                        "Senior")), withYears(Optional.empty()), NO_SKILLS, List.of()));
    }

    /** The ATS score for a shape, with everything else held at its best. */
    private static int atsFor(ResumeShape shape) {
        return ScoreEngine.score(posting(ExperienceDemand.unknown()), withShape(shape), NO_SKILLS,
                List.of()).ats();
    }

    private static int skillsFor(SkillComparison.Comparison comparison) {
        return ScoreEngine.score(posting(ExperienceDemand.unknown()), flawless(), comparison,
                List.of()).skillsMatch();
    }

    private static int keywordFor(List<KeywordVerdict> keywords) {
        return ScoreEngine.score(posting(ExperienceDemand.unknown()), flawless(), NO_SKILLS,
                keywords).keyword();
    }

    private static int experienceFor(ExperienceDemand demand, int resumeYears) {
        return ScoreEngine.score(posting(demand), withYears(Optional.of(resumeYears)), NO_SKILLS,
                List.of()).experience();
    }

    private static PostingInsight posting(ExperienceDemand demand) {
        return new PostingInsight(List.of(), List.of(), demand, 120, Set.of(), true);
    }

    private static ExperienceDemand demand(int minYears, ExperienceLevel level, String evidence) {
        return new ExperienceDemand(minYears, null, level, evidence);
    }

    /** A document that earns every ATS point: all sections, contact details, bullets and numbers. */
    private static ResumeInsight flawless() {
        return new ResumeInsight(List.of(), EnumSet.of(ResumeSection.CONTACT, ResumeSection.SUMMARY,
                ResumeSection.SKILLS, ResumeSection.EXPERIENCE, ResumeSection.PROJECTS,
                ResumeSection.EDUCATION, ResumeSection.CERTIFICATIONS),
                Optional.of(6), shape(620, 40, 10, 6, false), "resume text");
    }

    private static ResumeInsight withShape(ResumeShape shape) {
        ResumeInsight base = flawless();
        return new ResumeInsight(base.skills(), base.sectionsFound(), base.years(), shape,
                base.text());
    }

    private static ResumeInsight withYears(Optional<Integer> years) {
        ResumeInsight base = flawless();
        return new ResumeInsight(base.skills(), base.sectionsFound(), years, base.shape(),
                base.text());
    }

    private static ResumeShape shape(int words, int lines, int bullets, int quantified,
                                     boolean artefacts) {
        return new ResumeShape(words, lines, bullets, quantified, true, true, true, artefacts);
    }

    /** Three demands at three importances, all at the same status, so weighting is isolated. */
    private static SkillComparison.Comparison comparison(SkillStatus status) {
        return new SkillComparison.Comparison(List.of(
                verdict("java", SkillImportance.CRITICAL, status),
                verdict("mysql", SkillImportance.IMPORTANT, status),
                verdict("docker", SkillImportance.NICE_TO_HAVE, status)), List.of());
    }

    private static SkillVerdict verdict(String slug, SkillImportance importance, SkillStatus status) {
        return new SkillVerdict(slug, slug, SkillCategory.LANGUAGE, importance, status,
                status == SkillStatus.MISSING ? 0 : 2, "", "Requirements");
    }

    private static KeywordVerdict matched(String term, int weight) {
        return new KeywordVerdict(term, 2, weight, KeywordKind.MATCHED);
    }

    private static KeywordVerdict absent(String term, int weight) {
        return new KeywordVerdict(term, 0, weight, KeywordKind.ABSENT);
    }

    private static ScoreNote noteFor(ScoreCard card, String label) {
        return card.notes().stream()
                .filter(note -> note.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No note labelled " + label));
    }
}
