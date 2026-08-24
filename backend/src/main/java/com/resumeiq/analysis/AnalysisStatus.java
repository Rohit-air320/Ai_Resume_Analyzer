package com.resumeiq.analysis;

/**
 * Lifecycle of one analysis run.
 *
 * <p>The states exist because an AI call is slow and can fail. The row is written before the
 * provider is called, so a failure leaves a record the user can see and retry rather than a
 * spinner that never resolves. Phase 6 fills in the scores and moves the row to
 * {@link #COMPLETED}; the processing screen in Phase 8 polls for exactly that transition.
 */
public enum AnalysisStatus {

    /** Row created, provider not called yet. */
    QUEUED,

    /** Provider call in flight. */
    PROCESSING,

    /** Scores and recommendations are present. The only state the results page will render. */
    COMPLETED,

    /**
     * The provider failed, timed out, or returned JSON that did not satisfy the schema. The
     * reason is stored in words the user can act on; the technical detail stays in the logs.
     */
    FAILED
}
