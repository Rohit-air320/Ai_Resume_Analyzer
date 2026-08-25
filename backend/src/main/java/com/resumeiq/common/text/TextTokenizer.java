package com.resumeiq.common.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The single definition of "a word" for everything that reads human-written text here.
 *
 * <p>Two features read prose and both have to answer the same question — where does one term end
 * and the next begin — and they have to answer it identically. If the skill matcher decides
 * "Spring Boot" is one skill while the keyword extractor counts "spring" and "boot" as two
 * keywords, the API tells the user that Spring Boot is required <em>and</em> that they should
 * consider adding the keyword "boot". Both statements come from the same sentence and only one of
 * them is sane. Phase 6 reads resumes with the same two questions, and reuses this.
 *
 * <h2>How a token gets its key</h2>
 *
 * <p>Every word is reduced through {@link Slug}, which is already the one place that decides
 * whether two spellings are the same thing. That has a useful side effect: slugging turns any run
 * of punctuation into a hyphen, so a word carries its own internal boundaries.
 * {@code "CI/CD"} becomes {@code ci-cd} and splits into {@code ci} and {@code cd};
 * {@code "Node.js"} becomes {@code node-js} and splits into {@code node} and {@code js};
 * {@code "React/Redux"} splits into two terms without a special case. Rejoining keys with a hyphen
 * reconstructs a slug, which is exactly what the skill catalogue is keyed by — so matching a
 * multi-word term is a map lookup rather than a search.
 *
 * <h2>Why segments exist</h2>
 *
 * <p>Multi-word matching needs a boundary or it will invent terms nobody wrote. In "Comfortable
 * with Java, Script writing is a bonus", a matcher that walks the whole line freely finds
 * "Java Script" and reports JavaScript. So text is first cut at the punctuation a reader would
 * pause on — line ends, commas, semicolons, colons, brackets, bullets, sentence periods, spaced
 * dashes — and no term may span two segments. Punctuation <em>inside</em> a word is left alone,
 * because that is the case slugging already handles correctly.
 */
public final class TextTokenizer {

    /**
     * Where one phrase stops and the next starts.
     *
     * <p>A period counts only when followed by whitespace or the end of the text, so the dot in
     * "Node.js" survives and the one ending a sentence does not. A dash counts only with spaces
     * around it, so "day-to-day" stays whole while "Java — five years" does not. Notably absent:
     * the slash, because slugging already splits "CI/CD" into two tokens while still allowing the
     * two-token match the catalogue needs.
     */
    private static final Pattern SEGMENT_BREAK = Pattern.compile(
            "[\\n,;:()\\[\\]{}|•·!?\"]|\\.(?=\\s|$)|\\s[-–—]\\s");

    private TextTokenizer() {
    }

    /**
     * A word, in both the form used for matching and the form a person wrote.
     *
     * @param key      slugged form, used for every comparison. Never blank.
     * @param original the source word as typed, kept only so suggestions can be shown back in the
     *                 author's own capitalisation — "Kubernetes", not "kubernetes"
     */
    public record Token(String key, String original) {
    }

    /** Cuts text into phrases that a term may not span. Blank segments are dropped. */
    public static List<String> segments(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String candidate : SEGMENT_BREAK.split(text)) {
            String trimmed = candidate.strip();
            if (!trimmed.isEmpty()) {
                segments.add(trimmed);
            }
        }
        return segments;
    }

    /**
     * Tokenises one segment.
     *
     * <p>A source word can produce more than one token — "Node.js" produces two — and each of
     * them keeps the whole original word. That is deliberate: it lets a caller rebuild the display
     * form of a multi-token term by walking the tokens and dropping repeats, without having to
     * know which words happened to contain punctuation.
     */
    public static List<Token> tokens(String segment) {
        if (segment == null || segment.isBlank()) {
            return List.of();
        }
        List<Token> tokens = new ArrayList<>();
        for (String word : segment.strip().split("\\s+")) {
            String slug = Slug.of(word);
            if (slug == null || slug.isEmpty()) {
                // Punctuation on its own: a bullet, a lone dash, a stray asterisk from markdown.
                continue;
            }
            for (String part : slug.split("-")) {
                if (!part.isEmpty()) {
                    tokens.add(new Token(part, word));
                }
            }
        }
        return tokens;
    }

    /**
     * The catalogue lookup key for the {@code length} tokens starting at {@code from}. Joining
     * with a hyphen is what makes this a slug — see the class comment.
     */
    public static String keyOf(List<Token> tokens, int from, int length) {
        StringBuilder key = new StringBuilder(tokens.get(from).key());
        for (int i = from + 1; i < from + length; i++) {
            key.append('-').append(tokens.get(i).key());
        }
        return key.toString();
    }

    /**
     * The display form of a run of tokens: the words as written, with a word that produced several
     * tokens appearing once.
     */
    public static String displayOf(List<Token> tokens, int from, int length) {
        StringBuilder display = new StringBuilder();
        String previous = null;
        for (int i = from; i < from + length; i++) {
            String original = tokens.get(i).original();
            if (!original.equals(previous)) {
                if (display.length() > 0) {
                    display.append(' ');
                }
                display.append(original);
                previous = original;
            }
        }
        return display.toString();
    }

    /**
     * Guards the one-letter terms. "C" and "R" are real entries in the skill catalogue, and a
     * one-character key matches so much text that without this the parser reports R as a required
     * skill because the posting mentioned R&amp;D.
     *
     * <p>The rule is that a one-letter term must have been written as that capital letter and
     * nothing else. "Experience with R and Python" matches; "R&amp;D", "r/o" and "(c) 2026" do
     * not. Longer keys are unaffected — "java" is specific enough to speak for itself.
     */
    public static boolean isCredible(Token token) {
        return token.key().length() > 1
                || token.original().equals(token.key().toUpperCase(Locale.ROOT));
    }
}
