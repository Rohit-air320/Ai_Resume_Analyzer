package com.resumeiq.resume;

/**
 * Where an uploaded file is in the extraction pipeline.
 *
 * <p>The states exist because extraction can fail for reasons the user can act on — a scanned
 * PDF with no text layer is the common one — and "we could not read this file" is a far better
 * answer than an analysis scored against an empty document.
 */
public enum ResumeStatus {

    /** File stored, text not extracted yet. Phase 4 extracts synchronously, so this is brief. */
    UPLOADED,

    /** Text extracted and usable. The only state an analysis may be started from. */
    TEXT_EXTRACTED,

    /**
     * Extraction produced nothing usable — an image-only PDF, a corrupt file, an empty
     * document. The reason lives in {@code extractionError} in words a user can act on.
     */
    EXTRACTION_FAILED
}
