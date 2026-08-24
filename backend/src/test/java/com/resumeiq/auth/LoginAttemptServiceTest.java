package com.resumeiq.auth;

import com.resumeiq.common.exception.TooManyAttemptsException;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.config.ResumeIqProperties.App;
import com.resumeiq.config.ResumeIqProperties.Auth;
import com.resumeiq.config.ResumeIqProperties.Cors;
import com.resumeiq.config.ResumeIqProperties.Seed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The sign-in throttle.
 *
 * <p>Three failures allowed per email here, twelve per address (the multiplier), and a fifteen
 * minute window — small numbers so the tests read as arithmetic rather than as loops.
 *
 * <p>What is being pinned down is mostly about who gets caught and who does not. A throttle that
 * only counts per email misses somebody working through a list of stolen addresses; one that only
 * counts per address locks out an entire office; one that never forgives turns a mistyped password
 * into a fifteen minute outage for the person who mistyped it. Each of those is a test below.
 */
class LoginAttemptServiceTest {

    private static final int MAX_EMAIL_FAILURES = 3;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final String EMAIL = "casey@example.com";
    private static final String ADDRESS = "203.0.113.7";

    private MutableClock clock;
    private LoginAttemptService throttle;

    @BeforeEach
    void freshThrottle() {
        clock = new MutableClock(Instant.parse("2026-01-05T09:00:00Z"));
        throttle = new LoginAttemptService(properties(), clock);
    }

    @Test
    @DisplayName("attempts below the allowance are let through")
    void allowsAttemptsBelowTheAllowance() {
        failTwice();

        assertAllowed(EMAIL, ADDRESS);
        assertThat(throttle.failuresForEmail(EMAIL)).isEqualTo(2);
    }

    @Test
    @DisplayName("spending the allowance locks the email and reports how long to wait")
    void locksTheEmailOnceTheAllowanceIsSpent() {
        failTimes(MAX_EMAIL_FAILURES, EMAIL, ADDRESS);

        Throwable thrown = catchThrowable(() -> throttle.assertAttemptAllowed(EMAIL, ADDRESS));

        assertThat(thrown)
                .isInstanceOf(TooManyAttemptsException.class)
                // An attacker who has locked something out should not learn from the message
                // which of the two counters they tripped, nor have the email echoed back.
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining(ADDRESS);
        TooManyAttemptsException locked = (TooManyAttemptsException) thrown;
        assertThat(locked.retryAfter()).isPositive().isLessThanOrEqualTo(LOCKOUT);
        assertThat(locked.retryAfterSeconds()).isBetween(1L, LOCKOUT.toSeconds());
    }

    @Test
    @DisplayName("one account is protected however many addresses the guesses come from")
    void locksTheEmailWhateverAddressTheGuessesComeFrom() {
        // Rotating through proxies defeats a per-address counter completely. It is the per-email
        // counter that stands between one account and a wordlist.
        throttle.recordFailure(EMAIL, "198.51.100.1");
        throttle.recordFailure(EMAIL, "198.51.100.2");
        throttle.recordFailure(EMAIL, "198.51.100.3");

        assertRefused(EMAIL, "198.51.100.4");
    }

    @Test
    @DisplayName("one machine is held back even when no single email fails twice")
    void locksTheAddressEvenWhenNoSingleEmailRepeats() {
        // A stolen credential list tried one email at a time: every per-email counter sits at
        // one failure, so only the address counter can see this at all.
        for (int i = 0; i < MAX_EMAIL_FAILURES * LoginAttemptService.ADDRESS_BUDGET_MULTIPLIER; i++) {
            throttle.recordFailure("victim-" + i + "@example.com", ADDRESS);
        }

        assertThat(throttle.failuresForEmail("victim-0@example.com")).isOne();
        assertRefused("someone-new@example.com", ADDRESS);
        // ...and the lock belongs to the machine, not to the account it was trying.
        assertAllowed("someone-new@example.com", "198.51.100.9");
    }

    @Test
    @DisplayName("a correct password clears both counters")
    void successClearsBothCounters() {
        failTwice();

        throttle.recordSuccess(EMAIL, ADDRESS);

        assertThat(throttle.failuresForEmail(EMAIL)).isZero();
        // Counting from zero again, so the next mistype is a first mistype.
        throttle.recordFailure(EMAIL, ADDRESS);
        assertAllowed(EMAIL, ADDRESS);
        assertThat(throttle.failuresForEmail(EMAIL)).isOne();
    }

    @Test
    @DisplayName("a quiet window forgives earlier mistakes")
    void forgivesAQuietWindow() {
        failTwice();

        clock.advance(LOCKOUT.plusSeconds(1));
        failTwice();

        // Two, not four: yesterday's mistypes are not held against today's.
        assertThat(throttle.failuresForEmail(EMAIL)).isEqualTo(2);
        assertAllowed(EMAIL, ADDRESS);
    }

    @Test
    @DisplayName("the lock lifts on its own when the window passes")
    void liftsTheLockWhenTheWindowPasses() {
        failTimes(MAX_EMAIL_FAILURES, EMAIL, ADDRESS);
        assertRefused(EMAIL, ADDRESS);

        clock.advance(LOCKOUT.plusSeconds(1));

        assertAllowed(EMAIL, ADDRESS);
        assertThat(throttle.failuresForEmail(EMAIL)).isZero();
    }

    @Test
    @DisplayName("the same address in different shapes shares one counter")
    void countsOneAddressOnce() {
        // Nothing normalises an address, so this is really about the fallback: a request with no
        // usable address must not get a fresh, unlimited bucket every time.
        int addressBudget = MAX_EMAIL_FAILURES * LoginAttemptService.ADDRESS_BUDGET_MULTIPLIER;
        for (int i = 0; i < addressBudget; i++) {
            throttle.recordFailure("anon-" + i + "@example.com", i % 2 == 0 ? null : "  ");
        }

        assertRefused("anon-fresh@example.com", null);
        assertRefused("anon-fresh@example.com", "");
    }

    @Test
    @DisplayName("an email is counted the same however it was typed")
    void countsEmailsWithoutCaringHowTheyWereTyped() {
        // Sign-in already normalises before looking the account up. If the throttle did not, three
        // capitalisations would buy three times the allowance for the same account.
        throttle.recordFailure("Casey@Example.com", ADDRESS);
        throttle.recordFailure("  casey@example.com  ", ADDRESS);
        throttle.recordFailure("CASEY@EXAMPLE.COM", ADDRESS);

        assertRefused(EMAIL, ADDRESS);
        assertThat(throttle.failuresForEmail("cAsEy@example.com")).isEqualTo(MAX_EMAIL_FAILURES);
    }

    @Test
    @DisplayName("the tracked-key bound holds, and stale keys are swept to make room")
    void boundsTheNumberOfTrackedKeys() {
        // The map is keyed partly by attacker-supplied input, so filling it is an attack in its
        // own right. Two keys per failure — one email, one address — so half the bound gets there.
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED_KEYS / 2; i++) {
            throttle.recordFailure("flood-" + i + "@example.com", "10.1." + (i / 256) + "." + (i % 256));
        }

        throttle.recordFailure("overflow@example.com", ADDRESS);

        // Refused admission rather than admitted at the cost of the heap: the throttle degrades
        // to "not tracking this one", which is the right way for it to fail.
        assertThat(throttle.failuresForEmail("overflow@example.com")).isZero();

        // Once the flood has aged out, the sweep reclaims the space and tracking resumes.
        clock.advance(LOCKOUT.plusSeconds(1));
        throttle.recordFailure("overflow@example.com", ADDRESS);

        assertThat(throttle.failuresForEmail("overflow@example.com")).isOne();
    }

    @Test
    @DisplayName("reset drops every counter")
    void resetDropsEveryCounter() {
        failTimes(MAX_EMAIL_FAILURES, EMAIL, ADDRESS);
        assertRefused(EMAIL, ADDRESS);

        throttle.reset();

        assertAllowed(EMAIL, ADDRESS);
        assertThat(throttle.failuresForEmail(EMAIL)).isZero();
    }

    private void failTwice() {
        failTimes(2, EMAIL, ADDRESS);
    }

    private void failTimes(int times, String email, String address) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(email, address);
        }
    }

    private void assertAllowed(String email, String address) {
        assertThatNoException().isThrownBy(() -> throttle.assertAttemptAllowed(email, address));
    }

    private void assertRefused(String email, String address) {
        assertThat(catchThrowable(() -> throttle.assertAttemptAllowed(email, address)))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    private static ResumeIqProperties properties() {
        return new ResumeIqProperties(
                new App("ResumeIQ", "0.1.0"),
                new Cors(List.of("http://example.test")),
                new Seed(false),
                new Auth("", 15, 7, 4, "resumeiq_rt", "Lax", false,
                        MAX_EMAIL_FAILURES, (int) LOCKOUT.toMinutes()));
    }

    /** A clock the test moves by hand, so the forgiveness window costs no wall-clock time. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            // Nothing under test reads the zone; every value it produces is an Instant.
            return this;
        }
    }
}
