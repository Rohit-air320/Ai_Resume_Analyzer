package com.resumeiq.analysis;

/**
 * Fits a string to a column.
 *
 * <p>Exists because of one failure mode that is embarrassing in a specific way: every string on an
 * analysis is written by a language model, the columns holding them are bounded, and a model that
 * writes 2,001 characters into a 2,000-character column throws a {@code DataException} at flush —
 * turning an analysis whose scores were computed correctly, whose advice passed every validation and
 * whose user is waiting on a spinner into a 500. The numbers were right and the request still failed
 * over a sentence that ran long.
 *
 * <p>So nothing goes into a column without coming through here first, and the class is deliberately
 * boring: one method, no state, called at every assignment in {@link AnalysisWriter}. The alternative
 * — validating lengths in the reader or the sanitiser — spreads the same knowledge across three
 * classes that would then all need updating when a column changes.
 *
 * <h2>Truncation is not silent</h2>
 *
 * <p>A cut string ends in an ellipsis, so a user reading a suggestion that stops mid-thought can see
 * that it stopped rather than wondering whether the model lost its train of thought. The ellipsis is
 * counted inside the budget rather than added to it, which is the mistake this project has already
 * made once: {@code AnalysisPrompts.fit} appended its marker after measuring and overshot the ceiling
 * it was enforcing.
 */
public final class Fitted {

    /** Marks a cut. One character, so the arithmetic below stays readable. */
    private static final String ELLIPSIS = "…";

    private Fitted() {
    }

    /**
     * The value, trimmed, cut to {@code maxLength} if it is longer.
     *
     * @param value     any string, or null
     * @param maxLength the column width, which must leave room for the marker
     * @return null for null or blank input, so a nullable column stores null rather than {@code ""}
     */
    public static String to(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength - 1).stripTrailing() + ELLIPSIS;
    }

    /**
     * The same, but never null.
     *
     * <p>For the {@code nullable = false} columns, where a blank string is the honest value: the
     * alternative is a constraint violation on a row whose other twenty fields are fine.
     */
    public static String required(String value, int maxLength) {
        String fitted = to(value, maxLength);
        return fitted == null ? "" : fitted;
    }
}
