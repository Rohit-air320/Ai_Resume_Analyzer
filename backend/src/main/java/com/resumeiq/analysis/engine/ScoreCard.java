package com.resumeiq.analysis.engine;

import java.util.List;

/**
 * The six scores, and the arithmetic behind them.
 *
 * <p>Every number in this record is computed in Java from the resume and the posting. That is the
 * central design decision of the analysis and it is worth stating plainly: the model writes the
 * words, the code decides the numbers. Three things follow, and each of them is a property the
 * product would lose if a model were asked for a score.
 *
 * <ul>
 *   <li><strong>The same inputs always produce the same score.</strong> Somebody who edits one
 *       bullet and re-runs the analysis sees the effect of that edit, not the effect of that edit
 *       plus a sampling temperature.</li>
 *   <li><strong>The scores survive the provider being down.</strong> With no API key at all, this
 *       product still scores a resume against a posting — which is what makes the offline mode a
 *       real mode rather than a stub.</li>
 *   <li><strong>Every score can be explained.</strong> {@link #notes()} carries the components, in
 *       plain language, so "why 68?" has an answer that does not begin with "the model thought".</li>
 * </ul>
 *
 * @param overall     the headline. Weighted from fit and readability, in that order.
 * @param ats         how well an applicant-tracking system can read the document
 * @param jobMatch    how well the resume fits this specific posting
 * @param skillsMatch weighted coverage of the skills the posting named
 * @param keyword     share of the posting's important terms the resume already uses
 * @param experience  how the resume's years compare to what the posting asked for
 * @param notes       one entry per component, saying what was measured and what it earned. Written
 *                    for a person to read, because a score without a reason is a number to argue
 *                    with rather than something to act on.
 */
public record ScoreCard(
        int overall,
        int ats,
        int jobMatch,
        int skillsMatch,
        int keyword,
        int experience,
        List<ScoreNote> notes
) {

    /** Scores are percentages. Clamped rather than trusted, at every boundary. */
    public static final int MIN = 0;

    /** The top of the scale. */
    public static final int MAX = 100;

    /**
     * Clamps a value to the scale.
     *
     * <p>Used on every score as it is built and again on anything a model returns. A percentage
     * arriving as 127 or -4 is not a number to reason about, and clamping at the boundary is the one
     * response that cannot make things worse.
     */
    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /** Clamps and rounds a computed fraction to a percentage. */
    public static int percent(double fraction) {
        return clamp((int) Math.round(fraction * 100));
    }

    /**
     * The named score, so a cross-check can iterate the six without six branches at the call site.
     *
     * @param key one of the JSON field names the API uses
     * @return the score, or -1 for a name that is not a score
     */
    public int byName(String key) {
        return switch (key) {
            case "overallScore" -> overall;
            case "atsScore" -> ats;
            case "jobMatchScore" -> jobMatch;
            case "skillsMatchScore" -> skillsMatch;
            case "keywordScore" -> keyword;
            case "experienceScore" -> experience;
            default -> -1;
        };
    }

    /** The six field names, in the order the API reports them. */
    public static List<String> scoreNames() {
        return List.of("overallScore", "atsScore", "jobMatchScore", "skillsMatchScore",
                "keywordScore", "experienceScore");
    }
}
