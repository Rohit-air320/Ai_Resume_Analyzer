package com.resumeiq.resume.extract;

import com.resumeiq.common.text.PlainText;

/**
 * The text of a document, cleaned up and measured.
 *
 * <p>Extraction is only half the job. What comes out of a PDF is technically the right
 * characters and practically unusable: soft hyphens mid-word, bullet glyphs from a symbol
 * font, {@code fi} and {@code fl} as single ligature code points, non-breaking spaces where
 * spaces belong, and a blank line between every visual line. The cleaning itself lives in
 * {@link PlainText}, because a pasted job description arrives with the same problems from a
 * different direction and the two must agree on what they do about it.
 *
 * <p>What this record adds is measurement and the two limits that go with it — a page count
 * the format may or may not report, a word count that ends up on screen, a length cap, and a
 * preview. Those are document concerns, not text concerns.
 *
 * @param text      normalised text, ready to store and to send to the model
 * @param pageCount pages, where the format knows; null where it does not
 * @param wordCount whitespace-separated tokens containing at least one letter or digit
 */
public record ExtractedText(String text, Integer pageCount, int wordCount) {

    /**
     * Cleans raw extractor output and counts it.
     *
     * @param rawText   whatever the parser produced, possibly null
     * @param pageCount pages if the format reports them, otherwise null
     */
    public static ExtractedText of(String rawText, Integer pageCount) {
        String cleaned = PlainText.normalise(rawText);
        return new ExtractedText(cleaned, pageCount, PlainText.countWords(cleaned));
    }

    /**
     * Applies the stored-length cap, cutting at a line break so the text never stops
     * mid-word. Returns {@code this} when there is nothing to cut, which is the normal case —
     * the word count does not need recomputing and the caller keeps the object it had.
     */
    public ExtractedText truncatedTo(int maxCharacters) {
        if (text.length() <= maxCharacters) {
            return this;
        }
        String shortened = PlainText.truncate(text, maxCharacters);
        return new ExtractedText(shortened, pageCount, PlainText.countWords(shortened));
    }

    /** True when there is enough text here to be worth analysing. */
    public boolean hasAtLeast(int minimumCharacters) {
        return text.length() >= minimumCharacters;
    }

    /**
     * A short opening excerpt, shown to the owner of the resume so they can confirm we read
     * the right document. Cut on a word boundary; never used for anything but display.
     */
    public String preview(int maxCharacters) {
        return PlainText.preview(text, maxCharacters);
    }
}
