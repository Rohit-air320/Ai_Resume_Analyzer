package com.resumeiq.jobdescription.parse;

import com.resumeiq.skill.SkillCategory;

/**
 * A catalogue skill the posting asks for, with the evidence for that reading.
 *
 * @param slug            canonical key, so this can be compared with what a resume contains
 *                        without comparing display strings
 * @param displayName     the catalogue's spelling, which is what the UI shows — a posting writing
 *                        "springboot" should still read as "Spring Boot" on screen
 * @param category        used to group the skills list
 * @param importance      how badly the posting wants it, read from where it was found
 * @param mentions        how many times it appears anywhere in the posting. Not a score: a posting
 *                        that says "Java" six times does not want Java six times as much. It is
 *                        shown as evidence and used only to break ties in the ordering.
 * @param strongestSection the section the {@code importance} was decided by
 * @param foundUnder      the heading of that section, as the poster wrote it, or null when the
 *                        skill was found in text with no heading above it. This is the single most
 *                        convincing thing the UI can display — "Docker — found under: Nice to
 *                        have" is an argument, where "Docker — preferred" is an assertion.
 */
public record DetectedSkill(
        String slug,
        String displayName,
        SkillCategory category,
        SkillImportance importance,
        int mentions,
        PostingSection strongestSection,
        String foundUnder
) {

    /** True when missing this skill is worth calling a gap. */
    public boolean isRequired() {
        return importance == SkillImportance.REQUIRED;
    }
}
