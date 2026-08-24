package com.resumeiq.recommendation;

/**
 * How urgent a recommendation is.
 *
 * <p>Separate from {@code displayOrder}: priority is a judgement the analysis makes and the UI
 * shows as a badge, order is the sequence the list is rendered in. Sorting by priority alone
 * would put {@code HIGH}, {@code LOW}, {@code MEDIUM} in that order, since a database orders
 * strings alphabetically and knows nothing about what the words mean.
 */
public enum Priority {

    /** Blocks the application. Fix before sending this resume. */
    HIGH,

    /** Costs score. Worth doing before the next application. */
    MEDIUM,

    /** Polish. */
    LOW
}
