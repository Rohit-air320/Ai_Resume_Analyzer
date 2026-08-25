package com.resumeiq.jobdescription.parse;

/**
 * How badly a posting wants a skill.
 *
 * <p>This distinction is the difference between advice and noise. A posting that lists
 * Kubernetes under "Nice to have" and one that lists it under "Requirements" are saying very
 * different things, and a skill-gap page that flattens them tells a candidate to go and learn
 * Kubernetes because it appeared once in a sentence about training budgets. The importance is
 * read from the section a skill was found in, which is the only signal a job posting actually
 * gives.
 *
 * <p>Where a skill appears more than once, the strongest reading wins: something named under
 * both "Requirements" and "Nice to have" is required, because it was required somewhere.
 */
public enum SkillImportance {

    /** Named in the requirements or in the day-to-day work. Missing this one costs interviews. */
    REQUIRED(3),

    /** Named as a bonus, a plus, or something they would love to see. Worth having, not fatal. */
    PREFERRED(2),

    /** Present in the text, but not in a section that asks for anything. Context, not a demand. */
    MENTIONED(1);

    private final int weight;

    SkillImportance(int weight) {
        this.weight = weight;
    }

    /** Higher means the posting wants it more. Used to reconcile repeated mentions. */
    public int weight() {
        return weight;
    }

    /** The stronger of two readings of the same skill. */
    public SkillImportance strongerOf(SkillImportance other) {
        return other != null && other.weight > weight ? other : this;
    }
}
