package com.resumeiq.analysis;

/**
 * The two documents to compare, and what the role is called.
 *
 * <p>Plain text in, nothing else. No entity, no id, no user — which is what lets the whole analysis be
 * exercised from a unit test with two string literals, and is why the tests for this phase need neither a
 * database nor a web context.
 *
 * @param resumeText  the extracted resume text. Never logged and never returned by the API.
 * @param postingText the job posting as the user pasted it
 * @param roleTitle   the role title, used to address the advice. Treated as a label, never as a fact.
 */
public record AnalysisInput(String resumeText, String postingText, String roleTitle) {

    /** Blank-safe accessors, so one null does not become a {@code NullPointerException} four layers down. */
    public AnalysisInput {
        resumeText = resumeText == null ? "" : resumeText;
        postingText = postingText == null ? "" : postingText;
        roleTitle = roleTitle == null ? "" : roleTitle.strip();
    }
}
