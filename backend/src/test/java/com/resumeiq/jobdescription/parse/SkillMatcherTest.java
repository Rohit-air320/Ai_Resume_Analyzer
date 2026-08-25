package com.resumeiq.jobdescription.parse;

import com.resumeiq.skill.CatalogSkill;
import com.resumeiq.skill.SkillCategory;
import com.resumeiq.skill.SkillIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Skill detection, and the four ways a naive search gets it wrong.
 *
 * <p>Each of the first four tests is a piece of advice that would have destroyed a user's trust in
 * the feature: being told Spring Boot means two skills, that a JavaScript developer knows Java,
 * that "Java, Script writing" mentions JavaScript, or that a posting requires R because it has an
 * R&amp;D department. The rest are about the section a skill was found in, which is the only thing
 * a posting says about how badly it wants that skill.
 */
class SkillMatcherTest {

    private static final SkillIndex CATALOGUE = SkillIndex.of(List.of(
            new CatalogSkill("java", "Java", SkillCategory.LANGUAGE),
            new CatalogSkill("javascript", "JavaScript", SkillCategory.LANGUAGE),
            new CatalogSkill("spring", "Spring", SkillCategory.FRAMEWORK),
            new CatalogSkill("spring-boot", "Spring Boot", SkillCategory.FRAMEWORK),
            new CatalogSkill("amazon", "Amazon", SkillCategory.CLOUD),
            new CatalogSkill("amazon-web-services", "Amazon Web Services", SkillCategory.CLOUD),
            new CatalogSkill("docker", "Docker", SkillCategory.DEVOPS),
            new CatalogSkill("mysql", "MySQL", SkillCategory.DATABASE),
            new CatalogSkill("r", "R", SkillCategory.LANGUAGE),
            new CatalogSkill("react", "React", SkillCategory.FRAMEWORK)
    ));

    @Test
    @DisplayName("the widest term wins, so Spring Boot is one skill and not also Spring")
    void prefersTheWidestTerm() {
        assertThat(detect(requirements("Spring Boot and Amazon Web Services")))
                .extracting(DetectedSkill::displayName)
                .containsExactly("Amazon Web Services", "Spring Boot");
    }

    @Test
    @DisplayName("a narrower term still matches on its own")
    void stillMatchesTheNarrowTerm() {
        assertThat(detect(requirements("Spring and Amazon")))
                .extracting(DetectedSkill::slug)
                .containsExactlyInAnyOrder("spring", "amazon");
    }

    @Test
    @DisplayName("JavaScript is not Java")
    void comparesWholeWords() {
        assertThat(detect(requirements("Strong JavaScript skills")))
                .extracting(DetectedSkill::slug)
                .containsExactly("javascript");
    }

    @Test
    @DisplayName("a term may not span a comma")
    void doesNotMatchAcrossASegmentBoundary() {
        // "Java, Script writing" contains the words of JavaScript in order, and a matcher that
        // walked the line freely would report a language nobody wrote.
        assertThat(detect(requirements("Comfortable with Java, Script writing is a bonus")))
                .extracting(DetectedSkill::slug)
                .containsExactly("java");
    }

    @Test
    @DisplayName("a one-letter skill needs to have been written as that letter")
    void appliesTheCredibilityRules() {
        assertThat(detect(requirements("Working with the R&D team"))).isEmpty();
        assertThat(detect(requirements("Experience with R and MySQL")))
                .extracting(DetectedSkill::slug)
                .containsExactlyInAnyOrder("r", "mysql");
    }

    @Test
    @DisplayName("a skill spelled like a verb needs its capital")
    void doesNotReadProseAsASkill() {
        assertThat(detect(requirements("We react quickly to incidents"))).isEmpty();
        assertThat(detect(requirements("Build interfaces in React"))).isNotEmpty();
    }

    @Test
    @DisplayName("importance comes from the section, and the heading comes back as evidence")
    void readsImportanceFromTheSection() {
        List<DetectedSkill> detected = detect(
                new PostingBlock(PostingSection.REQUIREMENTS, "Requirements", "Java and MySQL"),
                new PostingBlock(PostingSection.PREFERRED, "Nice to have", "Docker"));

        assertThat(detected).filteredOn(skill -> skill.slug().equals("java"))
                .singleElement()
                .satisfies(java -> {
                    assertThat(java.importance()).isEqualTo(SkillImportance.REQUIRED);
                    assertThat(java.isRequired()).isTrue();
                    assertThat(java.foundUnder()).isEqualTo("Requirements");
                });
        assertThat(detected).filteredOn(skill -> skill.slug().equals("docker"))
                .singleElement()
                .satisfies(docker -> {
                    assertThat(docker.importance()).isEqualTo(SkillImportance.PREFERRED);
                    assertThat(docker.isRequired()).isFalse();
                    assertThat(docker.foundUnder()).isEqualTo("Nice to have");
                });
    }

    @Test
    @DisplayName("a skill in two sections takes the stronger reading, and its evidence with it")
    void reconcilesRepeatedMentions() {
        List<DetectedSkill> detected = detect(
                new PostingBlock(PostingSection.PREFERRED, "Nice to have", "Docker"),
                new PostingBlock(PostingSection.REQUIREMENTS, "Must have", "Docker and Java"));

        assertThat(detected).filteredOn(skill -> skill.slug().equals("docker"))
                .singleElement()
                .satisfies(docker -> {
                    // Required somewhere means required: telling somebody a hard requirement is
                    // optional is the more expensive mistake.
                    assertThat(docker.importance()).isEqualTo(SkillImportance.REQUIRED);
                    assertThat(docker.mentions()).isEqualTo(2);
                    // And the evidence has to be the section that decided it, not the first one
                    // seen — a "Nice to have" heading under a REQUIRED label reads as a bug.
                    assertThat(docker.foundUnder()).isEqualTo("Must have");
                    assertThat(docker.strongestSection()).isEqualTo(PostingSection.REQUIREMENTS);
                });
    }

    @Test
    @DisplayName("a skill mentioned only in the perks still says where it was found")
    void keepsTheEvidenceForAWeakReading() {
        List<DetectedSkill> detected = detect(
                new PostingBlock(PostingSection.BENEFITS, "Perks", "A budget for Docker training"));

        // The reading that most needs explaining is the weakest one. "Docker — mentioned" invites
        // "mentioned where?", and an earlier version of the matcher could not answer: it only
        // recorded the heading when a sighting beat the starting importance, which MENTIONED never
        // does.
        assertThat(detected).singleElement().satisfies(docker -> {
            assertThat(docker.importance()).isEqualTo(SkillImportance.MENTIONED);
            assertThat(docker.foundUnder()).isEqualTo("Perks");
            assertThat(docker.strongestSection()).isEqualTo(PostingSection.BENEFITS);
        });
    }

    @Test
    @DisplayName("text with no heading above it has no evidence to show, and says so with null")
    void hasNoHeadingToShowForUnheadedText() {
        assertThat(detect(new PostingBlock(PostingSection.OTHER, null, "We work in Java")))
                .singleElement()
                .satisfies(java -> assertThat(java.foundUnder()).isNull());
    }

    @Test
    @DisplayName("mentions are counted across the whole posting")
    void countsMentions() {
        List<DetectedSkill> detected = detect(
                new PostingBlock(PostingSection.REQUIREMENTS, "Requirements", "Java, Java, MySQL"),
                new PostingBlock(PostingSection.RESPONSIBILITIES, "The role", "Write Java daily"));

        assertThat(detected).extracting(DetectedSkill::displayName, DetectedSkill::mentions)
                .containsExactly(
                        // Required first, then more mentions first, then alphabetically. Fully
                        // determined, because this ordering reaches the API.
                        tuple("Java", 3),
                        tuple("MySQL", 1));
    }

    @Test
    @DisplayName("required skills are ordered ahead of preferred ones")
    void ordersByImportanceFirst() {
        List<DetectedSkill> detected = detect(
                new PostingBlock(PostingSection.PREFERRED, "Nice to have", "Docker, Docker, Docker"),
                new PostingBlock(PostingSection.REQUIREMENTS, "Requirements", "MySQL"));

        // Docker is mentioned three times and is still second: a bonus named repeatedly is still
        // a bonus, and a list sorted by frequency would put it above a hard requirement.
        assertThat(detected).extracting(DetectedSkill::slug).containsExactly("mysql", "docker");
    }

    @Test
    @DisplayName("an empty catalogue detects nothing rather than failing")
    void handlesAnEmptyCatalogue() {
        assertThat(SkillMatcher.detect(
                List.of(new PostingBlock(PostingSection.REQUIREMENTS, "Requirements", "Java")),
                SkillIndex.empty()))
                .isEmpty();
        assertThat(SkillMatcher.detect(List.of(), CATALOGUE)).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private static List<DetectedSkill> detect(PostingBlock... blocks) {
        return SkillMatcher.detect(List.of(blocks), CATALOGUE);
    }

    private static PostingBlock requirements(String text) {
        return new PostingBlock(PostingSection.REQUIREMENTS, "Requirements", text);
    }
}
