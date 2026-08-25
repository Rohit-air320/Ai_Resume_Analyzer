package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;

/**
 * One section of the resume, scored.
 *
 * <p>This is what the section-breakdown chart is drawn from, and the reason the sections are a closed
 * enum: a chart whose axes change between two runs of the same resume is not a chart anybody can read.
 * Every section in {@link ResumeSection} gets a review on every analysis, including the ones the
 * resume does not have — an absent section scores low and says why, which is more useful than a gap
 * in the chart where the user has to work out what is missing.
 *
 * @param section the section
 * @param score   0-100 for this section alone
 * @param present whether a heading for it was found. Kept separate from the score because "absent"
 *                and "present but weak" are different problems with different fixes, and a single
 *                low number cannot tell them apart.
 * @param note    what was observed, in one sentence. Bounded to
 *                {@link #MAX_NOTE} characters because it is stored in a column that width.
 */
public record SectionReview(ResumeSection section, int score, boolean present, String note) {

    /** Matches {@code SectionAssessment.note}. Truncation happens before persistence, not at insert. */
    public static final int MAX_NOTE = 400;

    /** Clamps the score and trims the note to the column width. */
    public SectionReview {
        score = ScoreCard.clamp(score);
        if (note != null && note.length() > MAX_NOTE) {
            note = note.substring(0, MAX_NOTE - 1).stripTrailing() + "…";
        }
    }
}
