package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.KeywordKind;

/**
 * One of a posting's important terms, and whether the resume uses it.
 *
 * @param term        the term as the posting wrote it
 * @param occurrences how often the posting used it
 * @param weight      the posting parser's ranking score, which already accounts for the section the
 *                    term appeared in. Used as the weight in the keyword score, so a term from the
 *                    requirements counts for more than one from the benefits.
 * @param kind        {@code MATCHED} when the resume covers it, {@code ABSENT} when it does not.
 *                    {@code SUGGESTED} is not produced here — a suggestion is advice, and advice is
 *                    written by the advice layer from these findings.
 */
public record KeywordVerdict(String term, int occurrences, int weight, KeywordKind kind) {

    /** True when the resume already uses this term. */
    public boolean isMatched() {
        return kind == KeywordKind.MATCHED;
    }
}
