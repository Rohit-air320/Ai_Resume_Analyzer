package com.resumeiq.jobdescription.parse;

/**
 * A term the posting leans on that is not a catalogue skill.
 *
 * <p>These are what fills the gap between "skills" and "everything else a screener searches for".
 * A posting asking for "microservices", "distributed systems" or "CI pipelines" is describing what
 * the work is, and a resume that never uses those words reads as a weaker match to both a human
 * skimming it and to the software that ranks it first — even when the person has done exactly that
 * work. Naming the term is useful; the suggestion is always to describe work already done in the
 * posting's vocabulary, never to add a word for its own sake.
 *
 * @param term             the term as the posting wrote it, preferring a capitalised spelling
 *                         where the posting used one
 * @param occurrences      how many times it appears in a section that asks for something
 * @param score            occurrences weighted by section — see
 *                         {@link PostingSection#keywordWeight()}. The ranking key, and meaningful
 *                         only relative to the other keywords of the same posting.
 * @param strongestSection the most demanding section it appeared in
 */
public record Keyword(String term, int occurrences, int score, PostingSection strongestSection) {

    /** True when the term came from the requirements or the day-to-day work. */
    public boolean isFromDemandingSection() {
        return strongestSection.isDemanding();
    }
}
