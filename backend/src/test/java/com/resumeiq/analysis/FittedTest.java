package com.resumeiq.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Fitted}, which is two methods and the reason a long sentence is not a 500.
 *
 * <p>Worth its own class because the arithmetic here has already been wrong once elsewhere in this
 * project: {@code AnalysisPrompts.fit} appended its marker after measuring the budget and overshot the
 * ceiling it was enforcing. {@link #countsTheMarkerInsideTheBudget(int)} is that bug written down, and
 * it is off-by-one territory where reading the code is not enough.
 */
class FittedTest {

    @Test
    @DisplayName("null stays null, so a nullable column stores null")
    void nullStaysNull() {
        assertThat(Fitted.to(null, 20)).isNull();
    }

    @Test
    @DisplayName("a blank string becomes null rather than an empty one")
    void blankBecomesNull() {
        // "" and null in the same column mean the same thing to a reader and different things to a
        // query: `where note is null` misses the empty strings, so only one of the two is allowed in.
        assertThat(Fitted.to("   \n\t ", 20)).isNull();
    }

    @Test
    @DisplayName("a value inside the limit is returned trimmed")
    void shortValueIsTrimmedAndKept() {
        assertThat(Fitted.to("  Add a metric to the settlement bullet.  ", 100))
                .isEqualTo("Add a metric to the settlement bullet.");
    }

    @Test
    @DisplayName("a value exactly at the limit is left alone")
    void exactLengthIsUntouched() {
        String twenty = "12345678901234567890";
        assertThat(Fitted.to(twenty, 20)).isEqualTo(twenty).hasSize(20);
    }

    @Test
    @DisplayName("a longer value is cut and marked, so a reader can see it was cut")
    void longValueIsCutAndMarked() {
        String fitted = Fitted.to("abcdefghij", 5);

        assertThat(fitted).isEqualTo("abcd…").hasSize(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 20, 160, 300, 400, 2000})
    @DisplayName("the marker is counted inside the budget, never added to it")
    void countsTheMarkerInsideTheBudget(int limit) {
        String tooLong = "x".repeat(limit + 50);

        String fitted = Fitted.to(tooLong, limit);

        // The whole contract. A column of `limit` characters must accept the result, at every width
        // the schema actually uses — 160 for a title, 300 for a failure reason, 400 for evidence,
        // 2000 for a detail.
        assertThat(fitted).hasSize(limit).endsWith("…");
    }

    @Test
    @DisplayName("a cut that lands on a space does not leave the space before the marker")
    void doesNotLeaveASpaceBeforeTheMarker() {
        // "Rework the " cut at 11 would read "Rework the …", which looks like a rendering bug rather
        // than a truncation. Stripping first makes it "Rework the…" and stays inside the budget.
        String fitted = Fitted.to("Rework the MySQL queries", 12);

        assertThat(fitted).isEqualTo("Rework the…");
        assertThat(fitted.length()).isLessThanOrEqualTo(12);
    }

    @Test
    @DisplayName("required never returns null, because a not-null column cannot take one")
    void requiredNeverReturnsNull() {
        assertThat(Fitted.required(null, 20)).isEmpty();
        assertThat(Fitted.required("   ", 20)).isEmpty();
    }

    @Test
    @DisplayName("required otherwise behaves exactly like to")
    void requiredOtherwiseMatchesTo() {
        assertThat(Fitted.required("  Ship it  ", 20)).isEqualTo("Ship it");
        assertThat(Fitted.required("abcdefghij", 5)).isEqualTo("abcd…");
    }
}
