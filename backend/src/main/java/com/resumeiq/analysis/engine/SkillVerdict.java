package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.SkillImportance;
import com.resumeiq.analysis.SkillStatus;
import com.resumeiq.skill.SkillCategory;

/**
 * One skill, judged: what the posting wanted, what the resume showed, and the evidence for both.
 *
 * <p>This record is the smallest complete argument the product makes. Every other number is an
 * aggregate of these, and the reason it carries {@code evidence} and {@code foundUnder} rather than
 * only a status is that a verdict a user cannot check is a verdict they have to take on faith.
 * "Docker — missing (required, found under 'Requirements' in the posting)" can be verified by
 * scrolling up. "Docker — missing" cannot.
 *
 * @param slug        catalogue slug, stable across analyses and used for aggregation
 * @param displayName the catalogue's spelling
 * @param category    used to group the skill-gap view
 * @param importance  how much the posting wanted it, translated to the analysis scale
 * @param status      what the resume showed
 * @param mentions    how many times the resume mentioned it, zero when missing
 * @param evidence    a short, checkable phrase explaining the status. Bounded to fit the column it
 *                    is stored in.
 * @param foundUnder  the posting heading the demand came from, in the poster's own words. Null when
 *                    the skill is something the resume has and the posting never asked for.
 */
public record SkillVerdict(
        String slug,
        String displayName,
        SkillCategory category,
        SkillImportance importance,
        SkillStatus status,
        int mentions,
        String evidence,
        String foundUnder
) {

    /** Longest evidence the {@code analysis_skills} column accepts. */
    public static final int MAX_EVIDENCE = 400;

    /** True when this is a gap: the posting asked and the resume did not answer. */
    public boolean isGap() {
        return status == SkillStatus.MISSING;
    }

    /**
     * Weight for the coverage calculation.
     *
     * <p>Three, two, one. Kept here next to the enum it reads rather than in the score engine,
     * because "how much does a critical skill count for" is a property of importance, and a reader
     * asking that question should find the answer in one place.
     */
    public int weight() {
        return switch (importance) {
            case CRITICAL -> 3;
            case IMPORTANT -> 2;
            case NICE_TO_HAVE -> 1;
        };
    }

    /**
     * How much of that weight the resume earns.
     *
     * <p>A partial match earns most of the credit rather than half. The status means the skill is
     * there but only asserted — named in a list, never demonstrated — and that is much closer to
     * having the skill than to not having it. Scoring it at half would make a complete skills list
     * look like a serious gap, which is both wrong and the kind of wrongness that makes people
     * distrust the whole score.
     */
    public double credit() {
        return switch (status) {
            case STRONG -> 1.0;
            case PARTIAL -> 0.7;
            case MISSING -> 0.0;
        };
    }
}
