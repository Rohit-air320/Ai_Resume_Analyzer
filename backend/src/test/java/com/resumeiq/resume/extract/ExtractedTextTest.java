package com.resumeiq.resume.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Text cleanup, which is the least glamorous code in the project and among the most
 * consequential.
 *
 * <p>Every case here is something a real PDF or DOCX actually does, and every one of them
 * would cost a match later: a skill written with an {@code fi} ligature is not the same
 * string as the same skill typed normally, a soft hyphen left inside a word splits it into
 * something nothing will find, and a bullet from a symbol font is a code point that means
 * nothing outside that font. None of it is visible when you look at the text, which is why
 * it is worth a test rather than a glance.
 *
 * <p>The inputs are written as {@code \\u} escapes rather than pasted characters. That is
 * deliberate: half of these characters are invisible, and a test whose input cannot be seen
 * in the source is a test nobody can review — or preserve through a careless save.
 */
class ExtractedTextTest {

    @Test
    @DisplayName("ligatures become the letters they stand for")
    void unpacksLigatures() {
        // U+FB01 is one character. Left alone, "Certi[fi]cation" and "Certification" are
        // different strings, and only one of them matches anything.
        ExtractedText extracted = ExtractedText.of("Certi\uFB01cation and in\uFB02ation", 1);

        assertThat(extracted.text()).isEqualTo("Certification and inflation");
    }

    @Test
    @DisplayName("invisible characters that break words are removed")
    void removesInvisibleCharacters() {
        // Soft hyphen, zero-width space, byte-order mark. A PDF exporter inserts the first
        // when it reflows a word across a line break; none of the three can be seen.
        ExtractedText extracted =
                ExtractedText.of("Java\u00ADScript\u200B and SQL\uFEFF", null);

        assertThat(extracted.text()).isEqualTo("JavaScript and SQL");
    }

    @Test
    @DisplayName("symbol-font bullets become real bullets rather than mystery characters")
    void normalisesPrivateUseGlyphs() {
        // U+F0B7 is Wingdings' bullet, in Unicode's private use area. Keeping it as a bullet
        // preserves "one item per line", which the section analysis will want later.
        ExtractedText extracted = ExtractedText.of("\uF0B7 Java\n\uF0B7 Spring Boot", 1);

        assertThat(extracted.text()).isEqualTo("• Java\n• Spring Boot");
    }

    @Test
    @DisplayName("non-breaking spaces become ordinary spaces")
    void normalisesExoticSpaces() {
        ExtractedText extracted = ExtractedText.of("Spring\u00A0Boot and\u00A0MySQL", null);

        assertThat(extracted.text()).isEqualTo("Spring Boot and MySQL");
    }

    @Test
    @DisplayName("a form feed between pages becomes a space, not a gap in a word")
    void neutralisesControlCharacters() {
        ExtractedText extracted = ExtractedText.of("Page one\fPage two", 2);

        assertThat(extracted.text()).isEqualTo("Page one Page two");
    }

    @Test
    @DisplayName("Windows line endings and padded columns collapse")
    void tidiesWhitespace() {
        String raw = "PRIYA SHARMA   \r\n\r\n\r\n\r\nEXPERIENCE\r\nJava        Spring Boot\r\n";

        ExtractedText extracted = ExtractedText.of(raw, 1);

        // Two newlines survive as a paragraph break; four do not. Runs of padding spaces,
        // which is how a two-column layout arrives, become one.
        assertThat(extracted.text()).isEqualTo("PRIYA SHARMA\n\nEXPERIENCE\nJava Spring Boot");
    }

    @Test
    @DisplayName("null and blank input are empty text rather than an exception")
    void toleratesNothing() {
        assertThat(ExtractedText.of(null, null).text()).isEmpty();
        assertThat(ExtractedText.of("   \n\n  ", null).text()).isEmpty();
        assertThat(ExtractedText.of(null, null).wordCount()).isZero();
    }

    @Test
    @DisplayName("bullets and dashes are not counted as words")
    void countsOnlyRealWords() {
        // Nine whitespace-separated tokens, five of which contain a letter or a digit. A count
        // that counted punctuation would flatter every bulleted resume.
        ExtractedText extracted =
                ExtractedText.of("• Java • Spring Boot — 3 years -", null);

        assertThat(extracted.wordCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("the page count is carried through untouched, including when it is unknown")
    void keepsPageCount() {
        assertThat(ExtractedText.of("Some resume text", 3).pageCount()).isEqualTo(3);
        assertThat(ExtractedText.of("Some resume text", null).pageCount()).isNull();
    }

    @Test
    @DisplayName("truncation cuts at a line break, not mid-word")
    void truncatesOnALineBoundary() {
        ExtractedText extracted =
                ExtractedText.of("First line here\nSecond line here\nThird", 2);

        // 20 characters lands inside "Second line here", so the cut retreats to the newline
        // before it. Nobody gets half a word.
        ExtractedText shortened = extracted.truncatedTo(20);

        assertThat(shortened.text()).isEqualTo("First line here");
        assertThat(shortened.wordCount()).isEqualTo(3);
        assertThat(shortened.pageCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("text already within the limit is returned unchanged")
    void leavesShortTextAlone() {
        ExtractedText extracted = ExtractedText.of("Short enough", null);

        assertThat(extracted.truncatedTo(1_000)).isSameAs(extracted);
    }

    @Test
    @DisplayName("a document with no line breaks is still truncated")
    void truncatesTextWithNoLineBreaks() {
        ExtractedText extracted = ExtractedText.of("x".repeat(500), null);

        // The line-break search finds nothing here. Falling back to the hard limit is what
        // stops one strange document from bypassing the cap entirely.
        assertThat(extracted.truncatedTo(100).text()).hasSize(100);
    }

    @Test
    @DisplayName("the minimum-length check is on characters, not words")
    void reportsWhetherThereIsEnoughText() {
        ExtractedText extracted = ExtractedText.of("Java developer", null);

        assertThat(extracted.hasAtLeast(14)).isTrue();
        assertThat(extracted.hasAtLeast(15)).isFalse();
    }

    @Test
    @DisplayName("the preview is one line, cut on a word boundary, and marked as cut")
    void previewsOnALine() {
        ExtractedText extracted =
                ExtractedText.of("PRIYA SHARMA\n\nBackend developer in Bengaluru", null);

        String preview = extracted.preview(25);

        assertThat(preview).isEqualTo("PRIYA SHARMA Backend…");
        assertThat(preview).doesNotContain("\n");
    }

    @Test
    @DisplayName("a short resume previews in full, with no ellipsis")
    void previewsShortTextWhole() {
        assertThat(ExtractedText.of("PRIYA SHARMA", null).preview(320)).isEqualTo("PRIYA SHARMA");
    }
}
