package com.resumeiq.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line of the arithmetic behind a score.
 *
 * <p>The stored form of the engine's {@code ScoreNote}. A component either earned points out of a
 * maximum — "18/25 — Required skills: 6 of 9 present" — or it is unscored context, in which case
 * {@code outOf} is zero and only the comment matters.
 *
 * <p>An embeddable rather than an entity because a note has no life of its own: it is never queried
 * for, never updated, and is meaningless away from the analysis that produced it. Deleting the
 * analysis should take it, and an {@code @ElementCollection} says that in the mapping instead of
 * relying on a cascade somebody has to remember to configure.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ScoreExplanation {

    /** Matches the columns below. The writer fits every value before it gets here. */
    public static final int MAX_LABEL = 60;
    public static final int MAX_COMMENT = 300;

    @Column(name = "label", nullable = false, length = MAX_LABEL)
    private String label;

    @Column(name = "earned", nullable = false)
    private int earned;

    @Column(name = "out_of", nullable = false)
    private int outOf;

    @Column(name = "comment", length = MAX_COMMENT)
    private String comment;

    /** True when this note carries arithmetic rather than context. */
    public boolean isScored() {
        return outOf > 0;
    }
}
