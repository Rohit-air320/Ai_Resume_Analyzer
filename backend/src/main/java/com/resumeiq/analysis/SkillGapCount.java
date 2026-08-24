package com.resumeiq.analysis;

/**
 * How often one skill has come up as a gap for a user, across every analysis they have run.
 *
 * <p>This projection is the skill-gap feature. A single gap in a single analysis is noise; the
 * same skill missing in six analyses is the thing worth spending a weekend on, and that
 * difference is only visible by counting across runs.
 */
public interface SkillGapCount {

    /** Canonical display name where the mention resolved, otherwise the raw name. */
    String getLabel();

    long getOccurrences();
}
