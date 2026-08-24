package com.resumeiq.recommendation;

/**
 * What kind of advice a recommendation is.
 *
 * <p>The four values are the spec's four output lists — resume improvements, learning topics,
 * project ideas and keyword advice. One table with a type column rather than four near-identical
 * tables: they share every column, they are always written in the same transaction, and
 * {@code GET /api/recommendations} reads across all of them.
 */
public enum RecommendationType {

    /** Change something in the resume: rewrite a bullet, quantify an outcome, reorder a section. */
    IMPROVEMENT,

    /** Learn something to close a real gap. Carries a resource link where one is useful. */
    LEARNING,

    /** Build something that would evidence a missing skill honestly. */
    PROJECT,

    /**
     * Work a term from the posting into the resume where it truthfully belongs.
     *
     * <p>Never "add these words". The spec forbids encouraging keyword stuffing, so a
     * recommendation of this type is required to name the place the term legitimately fits.
     */
    KEYWORD
}
