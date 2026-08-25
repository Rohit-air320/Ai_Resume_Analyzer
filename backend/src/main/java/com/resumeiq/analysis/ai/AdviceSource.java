package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.engine.AnalysisFacts;

/**
 * Writes the words of an analysis.
 *
 * <p>Two implementations, chosen by configuration rather than by failure: {@link AiAdviceSource} asks a
 * model, {@link OfflineAdviceSource} writes from the findings in code. Both are real modes. The offline
 * one is what runs with no API key configured, and it is also the fallback when the provider is down,
 * which is why it had to be written as something worth reading rather than a placeholder.
 *
 * <p>Neither implementation decides a score. That happened before either of them was called.
 */
public interface AdviceSource {

    /**
     * Writes advice for one analysis.
     *
     * <p>Must not throw for a provider failure — a failed call means offline advice, not a failed
     * analysis. The scores are already computed by this point and the user is entitled to them.
     *
     * @param facts       the computed findings, which are the authority on what is true
     * @param postingText the posting as pasted, for a model that wants the original wording
     */
    AiAdvice adviseOn(AnalysisFacts facts, String postingText);

    /** Name for the log line and the analysis record. Never contains a key. */
    String describe();
}
