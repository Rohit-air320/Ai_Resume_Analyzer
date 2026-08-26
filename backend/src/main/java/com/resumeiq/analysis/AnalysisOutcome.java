package com.resumeiq.analysis;

import com.resumeiq.analysis.ai.AiAdvice;
import com.resumeiq.analysis.ai.OfflineAdviceSource;
import com.resumeiq.analysis.engine.AnalysisFacts;

/**
 * A finished analysis, before anything is persisted.
 *
 * <p>The numbers and the words, kept as two separate things all the way to the point where Phase 7 writes
 * them to MySQL. Keeping them separate to the end is what makes the central claim of this phase checkable
 * rather than merely stated: the scores in {@code facts} were computed from the documents, the text in
 * {@code advice} was written about those scores, and nothing in the second could have changed the first.
 *
 * @param facts  the computed findings, including the six scores, the skill verdicts, the keyword
 *               verdicts and the section reviews
 * @param advice the written half — feedback, improvements, gap notes, projects, learning topics, keyword
 *               placements — already validated against the facts
 */
public record AnalysisOutcome(AnalysisFacts facts, AiAdvice advice) {

    /** Which writer produced the advice, for the analysis record and the log. Never contains a key. */
    public String adviceSource() {
        return advice.source();
    }

    /**
     * True when a model wrote the advice.
     *
     * <p>Worth surfacing in the UI. A user reading advice should know whether a model read their bullets
     * or whether the suggestions were derived from the structural findings, because the two deserve
     * different amounts of trust on different kinds of claim.
     *
     * <p>Decided from the source string rather than from a list of provider names, and by asking whether
     * the offline writer produced it rather than whether some known model did. That way adding a second
     * provider does not silently make every analysis it writes look computed.
     */
    public boolean isModelWritten() {
        return isModelWritten(advice.source());
    }

    /**
     * The same question, asked of a stored analysis.
     *
     * <p>Split out as a static so the rule lives in one place. Phase 7 stores the source string on the
     * analysis row and has to answer this again when the row is read back weeks later, and two copies
     * of a heuristic are two things to keep in step.
     *
     * @param source the stored source string, which may be null on a row that never completed
     */
    public static boolean isModelWritten(String source) {
        return source != null && !source.startsWith(OfflineAdviceSource.DESCRIPTION);
    }
}
