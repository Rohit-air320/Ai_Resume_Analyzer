package com.resumeiq.analysis.engine;

/**
 * The measurable shape of a resume, independent of what it says.
 *
 * <p>Every field here is a yes/no or a count taken straight from the text, and together they are
 * most of what an ATS score is really made of. Content is judged by comparing skills against a
 * posting; this is the other half — whether the document can be read at all, and whether it reads
 * like a resume or like a page of prose.
 *
 * @param wordCount          total words. Both extremes are a problem: under about 250 words there is
 *                           not enough to assess, and over about 1,000 the reader stops.
 * @param lineCount          non-blank lines, used to judge density
 * @param bulletCount        lines that begin with a bullet marker. A resume written in paragraphs is
 *                           harder for both a parser and a person.
 * @param quantifiedLines    lines containing a number that reads as impact — a percentage, an
 *                           amount, a count of users or engineers. The single strongest signal that
 *                           a resume describes outcomes rather than duties.
 * @param hasEmail           an email address was found. Its absence is an instant ATS failure: the
 *                           parser has nothing to key the candidate on.
 * @param hasPhone           a phone number was found
 * @param hasLink            a URL or a LinkedIn/GitHub profile was found
 * @param hasLayoutArtefacts pipe characters, repeated tabs or wide runs of spaces survived
 *                           extraction, which is what a table or a two-column layout leaves behind.
 *                           Those layouts are the most common reason a resume parses into nonsense.
 */
public record ResumeShape(
        int wordCount,
        int lineCount,
        int bulletCount,
        int quantifiedLines,
        boolean hasEmail,
        boolean hasPhone,
        boolean hasLink,
        boolean hasLayoutArtefacts
) {

    /** Nothing measured — the shape of an empty document. */
    public static ResumeShape empty() {
        return new ResumeShape(0, 0, 0, 0, false, false, false, false);
    }

    /**
     * How many of the three contact signals are present.
     *
     * <p>Three rather than four, because a location is not reliably detectable from text and
     * penalising its absence would mean penalising a document for something this parser cannot see.
     */
    public int contactSignals() {
        return (hasEmail ? 1 : 0) + (hasPhone ? 1 : 0) + (hasLink ? 1 : 0);
    }

    /** True when the document is written as bullets rather than as paragraphs. */
    public boolean isBulleted() {
        return bulletCount >= 5;
    }
}
