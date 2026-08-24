package com.resumeiq.analysis;

/**
 * How much the job description cares about a skill.
 *
 * <p>Without this, a gap list is a flat pile of twenty items and the user has no idea what to
 * do first. With it, the skill-gap page can lead with the two critical gaps and push the
 * nice-to-haves down — which is the difference between advice and a word cloud.
 *
 * <p>The order of the constants is the display order, so {@code Comparable} sorts a gap list
 * correctly. That is also why {@code @Enumerated(STRING)} matters: reordering these constants
 * to change presentation must never rewrite the meaning of stored rows.
 */
public enum SkillImportance {

    /** The posting treats it as a requirement. Missing it is likely an automatic filter. */
    CRITICAL,

    /** Named as expected or preferred. Missing it costs match score. */
    IMPORTANT,

    /** Listed as a bonus. Worth adding only if it is true. */
    NICE_TO_HAVE
}
