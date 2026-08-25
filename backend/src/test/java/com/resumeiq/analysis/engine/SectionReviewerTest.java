package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.analysis.SkillImportance;
import com.resumeiq.analysis.SkillStatus;
import com.resumeiq.skill.SkillCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section-by-section review.
 *
 * <p>The tests worth reading here are the two about honesty rather than arithmetic:
 * {@link #absentSectionsAreScoredNotOmitted()}, because a chart that drops an axis is the one place the
 * most valuable advice would never appear, and {@link #evidenceIsCountedFromTheResumeNotThePosting()},
 * because the bug it guards ran fine and produced plausible numbers for a different question.
 */
class SectionReviewerTest {

    @Test
    @DisplayName("all eight sections are reviewed, in enum order, every time")
    void everySectionIsReviewedInEnumOrder() {
        List<SectionReview> reviews = SectionReviewer.review(thin(), comparison());

        assertThat(reviews).extracting(SectionReview::section)
                .containsExactly(ResumeSection.values());
        assertThat(reviews).allSatisfy(review -> {
            assertThat(review.note()).isNotBlank();
            assertThat(review.note().length()).isLessThanOrEqualTo(SectionReview.MAX_NOTE);
            assertThat(review.score()).isBetween(ScoreCard.MIN, ScoreCard.MAX);
        });
    }

    @Test
    @DisplayName("an absent section is scored and explained, never dropped from the list")
    void absentSectionsAreScoredNotOmitted() {
        // A resume with only a skills list. The chart still has eight axes, and the four that read low
        // are the advice: on an early-career resume "add a projects section" is usually the single most
        // valuable suggestion, and dropping the axis is the one way to guarantee it is never made.
        List<SectionReview> reviews = SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SKILLS), shape(180, 12, 0, 0, false), List.of()),
                comparison());

        assertThat(reviews).hasSize(ResumeSection.values().length);
        assertThat(review(reviews, ResumeSection.PROJECTS).present()).isFalse();
        assertThat(review(reviews, ResumeSection.PROJECTS).score()).isEqualTo(20);
        assertThat(review(reviews, ResumeSection.PROJECTS).note())
                .contains("fastest route to closing a skill gap honestly");
        assertThat(review(reviews, ResumeSection.EXPERIENCE).present()).isFalse();
        assertThat(review(reviews, ResumeSection.SUMMARY).present()).isFalse();
        assertThat(review(reviews, ResumeSection.EDUCATION).score()).isEqualTo(20);
    }

    @Test
    @DisplayName("present and weak is distinguished from absent, because the fixes differ")
    void presentButWeakIsNotTheSameAsAbsent() {
        SectionReview absent = review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SKILLS), shape(400, 30, 6, 3, false), List.of()),
                comparison()), ResumeSection.PROJECTS);
        SectionReview weak = review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SKILLS, ResumeSection.PROJECTS),
                        shape(400, 30, 6, 3, false), List.of()),
                comparison()), ResumeSection.PROJECTS);

        assertThat(absent.present()).isFalse();
        assertThat(weak.present()).isTrue();
        assertThat(weak.score()).isGreaterThan(absent.score());
        // "Add a projects section" and "put one of the missing skills in the project you have" are
        // different instructions, and a single low number cannot tell the user which one applies.
        assertThat(absent.note()).isNotEqualTo(weak.note());
    }

    @Test
    @DisplayName("the contact review weights email above the other two signals")
    void contactWeightsEmailAboveTheRest() {
        assertThat(contactScore(true, true, true)).isEqualTo(100);
        assertThat(contactScore(true, true, false)).isEqualTo(80);
        assertThat(contactScore(false, true, true)).isEqualTo(55);
        assertThat(contactScore(true, false, false)).isEqualTo(60);
        assertThat(contactScore(false, false, true)).isEqualTo(35);
    }

    @Test
    @DisplayName("no contact details at all is the lowest score any section can be given")
    void noContactDetailsIsTheFloor() {
        // Note the shape: no email, no phone, no link. The shared shape() helper sets all three, which
        // is what every other test here wants, so this one builds its own.
        ResumeShape unreachable = new ResumeShape(400, 30, 6, 3, false, false, false, false);
        SectionReview contact = review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SKILLS), unreachable, List.of()),
                comparison()), ResumeSection.CONTACT);

        assertThat(contact.present()).isFalse();
        assertThat(contact.score()).isEqualTo(10);
        assertThat(contact.note()).contains("first thing to fix");
    }

    @Test
    @DisplayName("a summary naming technologies beats one built from adjectives")
    void aSummaryOfEvidenceBeatsASummaryOfAdjectives() {
        assertThat(summaryScore(0)).isEqualTo(62);
        assertThat(summaryScore(1)).isEqualTo(82);
        assertThat(summaryScore(3)).isEqualTo(95);
        assertThat(summaryScore(0)).isLessThan(summaryScore(1));
    }

    @Test
    @DisplayName("the skills review answers how well the list covers this posting")
    void theSkillsReviewIsAboutThisPosting() {
        // Two of the three demands named, whether demonstrated or merely listed.
        SectionReview review = review(SectionReviewer.review(full(), comparison()),
                ResumeSection.SKILLS);

        assertThat(review.score()).isEqualTo(67);
        assertThat(review.note()).contains("2 of the 3 skills this posting asks for");
    }

    @Test
    @DisplayName("with no skill demanded the skills review says so instead of scoring zero")
    void anUnmeasurableSkillsSectionSaysSo() {
        SectionReview review = review(SectionReviewer.review(full(),
                new SkillComparison.Comparison(List.of(), List.of())), ResumeSection.SKILLS);

        assertThat(review.score()).isEqualTo(70);
        assertThat(review.note()).contains("could not be measured");
    }

    @Test
    @DisplayName("skill evidence is counted from the resume's sections, not the posting's headings")
    void evidenceIsCountedFromTheResumeNotThePosting() {
        // The bug this guards: reading SkillVerdict.foundUnder(), which names the heading the *posting*
        // used. Every verdict below was found under the posting's "Requirements" heading and none under
        // anything called "Projects", so a reviewer reading the posting's side would count zero — while
        // the resume demonstrably uses Java in a project. The code would have run and the number would
        // have looked reasonable.
        ResumeInsight resume = resume(
                EnumSet.of(ResumeSection.SKILLS, ResumeSection.PROJECTS, ResumeSection.EXPERIENCE),
                shape(500, 30, 6, 3, false),
                List.of(skill("java", Set.of(ResumeSection.SKILLS, ResumeSection.PROJECTS)),
                        skill("mysql", Set.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE))));

        List<SectionReview> reviews = SectionReviewer.review(resume, comparison());

        assertThat(review(reviews, ResumeSection.PROJECTS).note())
                .contains("1 of the posting's skills are demonstrated in a project");
        assertThat(review(reviews, ResumeSection.EXPERIENCE).note())
                .contains("1 of the posting's skills appear under experience");
    }

    @Test
    @DisplayName("a skill listed only in the skills section is not counted as demonstrated anywhere")
    void aListedSkillIsNotEvidence() {
        ResumeInsight listOnly = resume(
                EnumSet.of(ResumeSection.SKILLS, ResumeSection.PROJECTS, ResumeSection.EXPERIENCE),
                shape(500, 30, 6, 3, false),
                List.of(skill("java", Set.of(ResumeSection.SKILLS)),
                        skill("mysql", Set.of(ResumeSection.SKILLS))));

        List<SectionReview> reviews = SectionReviewer.review(listOnly, comparison());

        assertThat(review(reviews, ResumeSection.PROJECTS).score()).isEqualTo(68);
        assertThat(review(reviews, ResumeSection.PROJECTS).note())
                .contains("none of the posting's skills appear in it");
    }

    @Test
    @DisplayName("the experience note says which measurements were document-wide")
    void theExperienceNoteIsHonestAboutWhatItMeasured() {
        // Bullets and numbers are counted across the whole document, because the extractor does not
        // give per-section line counts. Presenting a document-wide count as a fact about one section
        // would be the kind of small dishonesty that makes a reader stop trusting the rest.
        SectionReview review = review(SectionReviewer.review(full(), comparison()),
                ResumeSection.EXPERIENCE);

        assertThat(review.note()).startsWith("Across the resume there are");
        assertThat(review.score()).isEqualTo(100);
    }

    @Test
    @DisplayName("experience is built up from presence, structure, numbers and evidence")
    void experienceIsBuiltUpFromWhatCanBeCounted() {
        // 55 for presence, 15 for bullets, up to 20 for numbers, 10 for a demand demonstrated here.
        assertThat(experienceScore(shape(500, 30, 0, 0, false), false)).isEqualTo(55);
        assertThat(experienceScore(shape(500, 30, 6, 0, false), false)).isEqualTo(70);
        assertThat(experienceScore(shape(500, 30, 6, 1, false), false)).isEqualTo(77);
        assertThat(experienceScore(shape(500, 30, 6, 3, false), false)).isEqualTo(90);
        assertThat(experienceScore(shape(500, 30, 6, 9, false), false)).isEqualTo(90);
        assertThat(experienceScore(shape(500, 30, 6, 3, false), true)).isEqualTo(100);
    }

    @Test
    @DisplayName("an absent certifications section is neutral and the note does not tell anyone to get one")
    void certificationsAreNeutralWhenAbsent() {
        SectionReview absent = review(SectionReviewer.review(full(), comparison()),
                ResumeSection.CERTIFICATIONS);

        // The only section whose absence is not a fault. Plenty of strong resumes have none, and advice
        // that reads as "go and buy a certificate" is advice that costs the reader money for nothing.
        assertThat(absent.present()).isFalse();
        assertThat(absent.score()).isEqualTo(70);
        assertThat(absent.note())
                .contains("entirely normal and not a fault")
                .contains("no reason to acquire one");

        ResumeInsight certified = resume(
                EnumSet.of(ResumeSection.SKILLS, ResumeSection.CERTIFICATIONS),
                shape(500, 30, 6, 3, false), List.of());
        assertThat(review(SectionReviewer.review(certified, comparison()),
                ResumeSection.CERTIFICATIONS).score()).isEqualTo(92);
    }

    @Test
    @DisplayName("formatting is always present, because it is a property of the document")
    void formattingIsAlwaysPresent() {
        for (ResumeInsight candidate : List.of(ResumeInsight.empty(), thin(), full())) {
            assertThat(review(SectionReviewer.review(candidate, comparison()),
                    ResumeSection.FORMATTING).present())
                    .as("formatting presence on %s", candidate.shape())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("formatting deducts once per problem and lists every one it found")
    void formattingDeductsOncePerProblem() {
        assertThat(formattingScore(shape(500, 30, 6, 3, false))).isEqualTo(100);
        assertThat(formattingScore(shape(500, 30, 0, 3, false))).isEqualTo(75);   // paragraphs
        assertThat(formattingScore(shape(500, 30, 6, 3, true))).isEqualTo(60);    // artefacts
        assertThat(formattingScore(shape(1_600, 90, 6, 3, false))).isEqualTo(80); // too long
        assertThat(formattingScore(shape(120, 8, 6, 3, false))).isEqualTo(80);    // too short
        // Every problem at once, floored at zero rather than going negative.
        assertThat(formattingScore(shape(1_600, 90, 0, 0, true))).isEqualTo(15);

        SectionReview wrecked = review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SKILLS), shape(1_600, 90, 0, 0, true), List.of()),
                comparison()), ResumeSection.FORMATTING);
        assertThat(wrecked.note())
                .contains("tables, columns")
                .contains("paragraphs rather than bullets")
                .contains("1600 words");
    }

    @Test
    @DisplayName("an empty resume is reviewed rather than crashing")
    void anEmptyResumeIsReviewed() {
        List<SectionReview> reviews = SectionReviewer.review(ResumeInsight.empty(),
                new SkillComparison.Comparison(List.of(), List.of()));

        assertThat(reviews).hasSize(ResumeSection.values().length);
        // A zero-word document is not deducted for being short: there is no document to be short.
        assertThat(review(reviews, ResumeSection.FORMATTING).score()).isEqualTo(75);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static int contactScore(boolean email, boolean phone, boolean link) {
        ResumeShape shape = new ResumeShape(500, 30, 6, 3, email, phone, link, false);
        return review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SKILLS), shape, List.of()), comparison()),
                ResumeSection.CONTACT).score();
    }

    private static int summaryScore(int technologiesNamed) {
        List<ResumeSkill> named = List.of("java", "mysql", "docker").subList(0, technologiesNamed)
                .stream()
                .map(slug -> skill(slug, Set.of(ResumeSection.SUMMARY)))
                .toList();
        return review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SUMMARY), shape(500, 30, 6, 3, false), named),
                comparison()), ResumeSection.SUMMARY).score();
    }

    private static int experienceScore(ResumeShape shape, boolean demandDemonstratedHere) {
        List<ResumeSkill> skills = demandDemonstratedHere
                ? List.of(skill("java", Set.of(ResumeSection.EXPERIENCE)))
                : List.of();
        return review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.EXPERIENCE), shape, skills), comparison()),
                ResumeSection.EXPERIENCE).score();
    }

    private static int formattingScore(ResumeShape shape) {
        return review(SectionReviewer.review(
                resume(EnumSet.of(ResumeSection.SKILLS), shape, List.of()), comparison()),
                ResumeSection.FORMATTING).score();
    }

    private static SectionReview review(List<SectionReview> reviews, ResumeSection section) {
        return reviews.stream()
                .filter(candidate -> candidate.section() == section)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No review for " + section));
    }

    /** The posting asks for three skills; the resume fixtures answer none, one or two of them. */
    private static SkillComparison.Comparison comparison() {
        return new SkillComparison.Comparison(List.of(
                verdict("java", SkillImportance.CRITICAL, SkillStatus.STRONG),
                verdict("mysql", SkillImportance.IMPORTANT, SkillStatus.PARTIAL),
                verdict("docker", SkillImportance.NICE_TO_HAVE, SkillStatus.MISSING)), List.of());
    }

    private static SkillVerdict verdict(String slug, SkillImportance importance, SkillStatus status) {
        // foundUnder is the posting's heading on every one of these, which is exactly why reading it
        // to answer a question about the resume would have been wrong.
        return new SkillVerdict(slug, slug, SkillCategory.LANGUAGE, importance, status,
                status == SkillStatus.MISSING ? 0 : 2, "", "Requirements");
    }

    /** A resume with every section, bullets, numbers, and two demands demonstrated in context. */
    private static ResumeInsight full() {
        return resume(EnumSet.of(ResumeSection.CONTACT, ResumeSection.SUMMARY, ResumeSection.SKILLS,
                        ResumeSection.EXPERIENCE, ResumeSection.PROJECTS, ResumeSection.EDUCATION),
                shape(620, 40, 10, 6, false),
                List.of(skill("java", Set.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE,
                                ResumeSection.PROJECTS, ResumeSection.SUMMARY)),
                        skill("mysql", Set.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE))));
    }

    /** A resume that is present but weak in most ways the reviewer can measure. */
    private static ResumeInsight thin() {
        return resume(EnumSet.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE),
                shape(180, 14, 0, 0, false),
                List.of(skill("java", Set.of(ResumeSection.SKILLS))));
    }

    private static ResumeInsight resume(Set<ResumeSection> sections, ResumeShape shape,
                                        List<ResumeSkill> skills) {
        return new ResumeInsight(skills, sections, Optional.of(4), shape, "resume text");
    }

    private static ResumeSkill skill(String slug, Set<ResumeSection> sections) {
        return new ResumeSkill(slug, slug, SkillCategory.LANGUAGE, sections.size(), sections);
    }

    private static ResumeShape shape(int words, int lines, int bullets, int quantified,
                                     boolean artefacts) {
        return new ResumeShape(words, lines, bullets, quantified, true, true, true, artefacts);
    }
}
