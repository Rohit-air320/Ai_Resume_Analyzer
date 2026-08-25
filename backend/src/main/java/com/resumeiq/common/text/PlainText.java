package com.resumeiq.common.text;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Turns text from anywhere into text this application can compare.
 *
 * <p>Two features feed on human-authored text and both are ruined by the same handful of
 * invisible characters. A resume arrives from a PDF parser with soft hyphens inside words,
 * {@code fi} and {@code fl} as single ligature code points, and bullet glyphs from a symbol
 * font that land in Unicode's private use area. A job description arrives from a browser
 * paste with non-breaking spaces, a byte-order mark from a Word copy, and whatever the
 * posting site used for its bullets. Different provenance, identical failure: a skill
 * written "Certiﬁcation" with a ligature does not equal "Certification", and the analysis
 * is quietly wrong for a reason nobody would ever find.
 *
 * <p>So the rule lives here, once. Two copies of a normalisation rule is not duplication
 * that stays harmless — it is duplication that drifts, and the day one copy learns about a
 * new invisible character the other keeps producing text that does not match.
 *
 * <p>The order of the steps is the design, and getting it wrong is invisible until a match
 * silently fails. NFKC first, because it decomposes ligatures and fullwidth forms and folds
 * every exotic space onto an ordinary one — every pattern after it sees ordinary characters.
 */
public final class PlainText {

    /** Three or more newlines collapse to a paragraph break; PDFs emit runs of them. */
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

    /** Trailing spaces and tabs at end of line, which every PDF produces and nothing needs. */
    private static final Pattern TRAILING_SPACE = Pattern.compile("[ \\t]+(?=\\n)");

    /** Runs of spaces and tabs. Column layouts arrive padded with dozens of them. */
    private static final Pattern REPEATED_SPACE = Pattern.compile("[ \\t]{2,}");

    /** A token counts as a word if it contains a letter or a digit — bullets do not. */
    private static final Pattern WORD = Pattern.compile(".*[\\p{L}\\p{Nd}].*", Pattern.DOTALL);

    /**
     * Characters to delete outright: zero-width space, zero-width non-joiner and joiner,
     * word joiner, byte-order mark, and the soft hyphen that PDF exporters leave inside
     * words after they reflow. Each of these is invisible and each one breaks a match.
     */
    private static final Pattern INVISIBLE =
            Pattern.compile("[\\u00AD\\u200B\\u200C\\u200D\\u2060\\uFEFF]");

    /**
     * Unicode's private use area, where symbol fonts put their glyphs. Wingdings' bullet
     * arrives as U+F0B7, which means nothing to anyone outside that font. They become a real
     * bullet rather than being dropped, because "one item per bulleted line" is structure the
     * section analysis wants.
     */
    private static final Pattern PRIVATE_USE = Pattern.compile("[\\uE000-\\uF8FF]+");

    /** Any other control character that survived, tab and newline excepted. */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    private PlainText() {
    }

    /**
     * Cleans text without changing what it says.
     *
     * <p>Note what this does <em>not</em> do: it does not lower-case, strip punctuation, or
     * remove anything a reader would notice. The output is still the author's text and is
     * safe to store and to show back to them. Matching-time folding is a separate concern
     * and belongs to whoever is matching.
     *
     * @param rawText anything, including null
     * @return cleaned text, never null
     */
    public static String normalise(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String text = Normalizer.normalize(rawText, Normalizer.Form.NFKC);
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = PRIVATE_USE.matcher(text).replaceAll("•");
        text = INVISIBLE.matcher(text).replaceAll("");
        text = CONTROL.matcher(text).replaceAll(" ");
        text = REPEATED_SPACE.matcher(text).replaceAll(" ");
        text = TRAILING_SPACE.matcher(text).replaceAll("");
        text = EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    /**
     * Counts words the way a person would.
     *
     * <p>A bullet character, an em dash and a lone hyphen are all whitespace-separated
     * tokens and none of them is a word, so a naive {@code split("\\s+").length} reports a
     * bulleted resume as considerably longer than it is. That number ends up on screen next
     * to the word "words", so it has to be defensible.
     */
    public static int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int words = 0;
        for (String token : text.split("\\s+")) {
            if (WORD.matcher(token).matches()) {
                words++;
            }
        }
        return words;
    }

    /**
     * A short opening excerpt for display, cut on a word boundary.
     *
     * @param text         already-normalised text
     * @param maxCharacters longest excerpt to return, before the ellipsis
     */
    public static String preview(String text, int maxCharacters) {
        String flattened = text.replace('\n', ' ').replaceAll("\\s{2,}", " ").strip();
        if (flattened.length() <= maxCharacters) {
            return flattened;
        }
        int lastSpace = flattened.lastIndexOf(' ', maxCharacters);
        return flattened.substring(0, lastSpace > 0 ? lastSpace : maxCharacters).strip() + "…";
    }

    /**
     * Applies a length cap, cutting at a line break so the text never stops mid-word.
     *
     * @param text          already-normalised text
     * @param maxCharacters hard ceiling
     * @return {@code text} itself when there is nothing to cut, which is the normal case
     */
    public static String truncate(String text, int maxCharacters) {
        if (text.length() <= maxCharacters) {
            return text;
        }
        int cut = text.lastIndexOf('\n', maxCharacters);
        // Text with no line break in its first several thousand characters is unusual but has
        // to be handled: fall back to the hard limit. The half-way test stops a document whose
        // only newline is near the start from being truncated to almost nothing.
        return text.substring(0, cut > maxCharacters / 2 ? cut : maxCharacters).strip();
    }
}
