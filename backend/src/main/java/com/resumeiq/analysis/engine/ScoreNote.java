package com.resumeiq.analysis.engine;

/**
 * One component of a score, in words.
 *
 * <p>These exist so that a number can be defended. They are what the prompt sends the model as
 * evidence, what the offline writer turns into prose, and what a developer reads in a log line when
 * a score looks wrong. Writing them as they are computed is the only way they stay true: a
 * reconstruction after the fact is a guess about arithmetic that has already happened.
 *
 * @param label   short name of the component, as a person would say it — "Contact details",
 *                "Quantified impact"
 * @param earned  points earned
 * @param outOf   points available, so a reader can see the weight as well as the result
 * @param comment what was actually observed. Specific: "3 of 5 required skills demonstrated", not
 *                "skills coverage is moderate".
 */
public record ScoreNote(String label, int earned, int outOf, String comment) {

    /** A note for a component with no point budget of its own — an explanation rather than a score. */
    public static ScoreNote of(String label, String comment) {
        return new ScoreNote(label, 0, 0, comment);
    }

    /** True when this note carries points rather than only an explanation. */
    public boolean isScored() {
        return outOf > 0;
    }

    /** "12/16 — Quantified impact: 3 bullets carry a number" */
    @Override
    public String toString() {
        return isScored()
                ? earned + "/" + outOf + " — " + label + ": " + comment
                : label + ": " + comment;
    }
}
