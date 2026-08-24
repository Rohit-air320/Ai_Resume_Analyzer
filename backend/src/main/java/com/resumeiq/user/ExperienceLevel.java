package com.resumeiq.user;

/**
 * Self-reported career stage, set on the profile and optional everywhere.
 *
 * <p>It is not decoration: the analysis prompt in Phase 6 uses it to calibrate advice, so a
 * student is not told to quantify the business impact of work they have not done yet, and a
 * senior engineer is not told to add a coursework section.
 */
public enum ExperienceLevel {

    /** Student or first job search, typically no professional experience yet. */
    ENTRY,

    /** Roughly one to three years. */
    JUNIOR,

    /** Roughly three to six years. */
    MID,

    /** Six years or more. */
    SENIOR,

    /** Team or org leadership, where scope matters more than tools. */
    LEAD
}
