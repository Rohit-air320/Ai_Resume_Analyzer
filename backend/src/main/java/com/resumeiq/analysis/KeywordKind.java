package com.resumeiq.analysis;

/**
 * Which of the three keyword lists a term belongs to.
 *
 * <p>One table with a kind column rather than three tables, because they carry the same shape
 * and are always read together to render the keyword panel.
 */
public enum KeywordKind {

    /** Present in both the job description and the resume. Evidence the resume is on target. */
    MATCHED,

    /** In the job description, absent from the resume, and true of this person to add. */
    SUGGESTED,

    /**
     * In the job description and absent from the resume, with no basis for claiming it. Shown
     * so the user understands the gap — never as something to paste in. The spec is explicit
     * that suggestions must be truthful, so this list is informational by design.
     */
    ABSENT
}
