package com.resumeiq.common.text;

import com.resumeiq.common.text.TextTokenizer.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared definition of "a word".
 *
 * <p>Two features read prose through this class and both have to get the same answer, so the cases
 * here are the ones where a naive split disagrees with a reader: punctuation inside a word,
 * punctuation between phrases, and the one-letter skill names.
 */
class TextTokenizerTest {

    @Test
    @DisplayName("punctuation inside a word becomes a boundary the catalogue can match across")
    void splitsInsideAWord() {
        assertThat(TextTokenizer.tokens("Node.js")).extracting(Token::key)
                .containsExactly("node", "js");
        assertThat(TextTokenizer.tokens("CI/CD")).extracting(Token::key)
                .containsExactly("ci", "cd");
        assertThat(TextTokenizer.tokens("React/Redux")).extracting(Token::key)
                .containsExactly("react", "redux");
    }

    @Test
    @DisplayName("every token keeps the whole word it came from")
    void keepsTheSourceWord() {
        assertThat(TextTokenizer.tokens("Node.js")).extracting(Token::original)
                .containsExactly("Node.js", "Node.js");
    }

    @Test
    @DisplayName("a run of tokens rejoins into the slug the catalogue is keyed by")
    void rebuildsASlug() {
        List<Token> tokens = TextTokenizer.tokens("Spring Boot and Node.js");

        assertThat(TextTokenizer.keyOf(tokens, 0, 2)).isEqualTo("spring-boot");
        assertThat(TextTokenizer.keyOf(tokens, 3, 2)).isEqualTo("node-js");
        assertThat(TextTokenizer.keyOf(tokens, 0, 1)).isEqualTo("spring");
    }

    @Test
    @DisplayName("the display form of a run shows each source word once")
    void rebuildsTheDisplayForm() {
        List<Token> tokens = TextTokenizer.tokens("Spring Boot and Node.js");

        assertThat(TextTokenizer.displayOf(tokens, 0, 2)).isEqualTo("Spring Boot");
        // Two tokens, one word. Without the de-duplication this would read "Node.js Node.js".
        assertThat(TextTokenizer.displayOf(tokens, 3, 2)).isEqualTo("Node.js");
    }

    @Test
    @DisplayName("a comma is a boundary no term may span")
    void breaksAtPunctuationAReaderPausesOn() {
        // The case this exists for: a matcher walking the whole line freely finds "Java Script"
        // here and reports JavaScript to somebody who wrote neither.
        assertThat(TextTokenizer.segments("Comfortable with Java, Script writing is a bonus"))
                .containsExactly("Comfortable with Java", "Script writing is a bonus");

        assertThat(TextTokenizer.segments("Java; Spring Boot (preferred): MySQL"))
                .containsExactly("Java", "Spring Boot", "preferred", "MySQL");
    }

    @Test
    @DisplayName("a sentence period breaks, a period inside a word does not")
    void tellsTwoKindsOfPeriodApart() {
        assertThat(TextTokenizer.segments("Build APIs. Ship them."))
                .containsExactly("Build APIs", "Ship them");
        assertThat(TextTokenizer.segments("We use Node.js here")).containsExactly("We use Node.js here");
    }

    @Test
    @DisplayName("a spaced dash breaks, a hyphen inside a word does not")
    void tellsTwoKindsOfDashApart() {
        assertThat(TextTokenizer.segments("Java — five years")).containsExactly("Java", "five years");
        assertThat(TextTokenizer.segments("day-to-day ownership"))
                .containsExactly("day-to-day ownership");
    }

    @Test
    @DisplayName("a bullet or a stray markdown mark produces no token of its own")
    void dropsPunctuationOnItsOwn() {
        assertThat(TextTokenizer.tokens("* Java")).extracting(Token::key).containsExactly("java");
        assertThat(TextTokenizer.tokens("- •")).isEmpty();
    }

    @Test
    @DisplayName("a one-letter term is credible only when it was written as that capital letter")
    void guardsOneLetterTerms() {
        // "R" is a real language and a one-character key matches an enormous amount of text.
        assertThat(TextTokenizer.isCredible(new Token("r", "R"))).isTrue();
        assertThat(TextTokenizer.isCredible(new Token("r", "R&D"))).isFalse();
        assertThat(TextTokenizer.isCredible(new Token("c", "(c)"))).isFalse();
        assertThat(TextTokenizer.isCredible(new Token("r", "r"))).isFalse();

        // Longer keys are specific enough to speak for themselves, whatever the casing.
        assertThat(TextTokenizer.isCredible(new Token("java", "java"))).isTrue();
    }

    @Test
    @DisplayName("blank input produces nothing rather than an empty-string token")
    void handlesBlankInput() {
        assertThat(TextTokenizer.segments(null)).isEmpty();
        assertThat(TextTokenizer.segments("  \n , ; ")).isEmpty();
        assertThat(TextTokenizer.tokens(null)).isEmpty();
        assertThat(TextTokenizer.tokens("   ")).isEmpty();
    }
}
