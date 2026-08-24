package com.resumeiq.auth;

import com.resumeiq.common.exception.TooManyAttemptsException;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed sign-in attempts and locks out the ones that keep failing.
 *
 * <p>A password endpoint with no throttle is a password endpoint an attacker can try a hundred
 * thousand times. BCrypt at cost 12 makes each guess expensive, but "expensive" is a rate, not
 * a limit — this puts the limit in.
 *
 * <p><strong>Two counters, not one.</strong> Per-email catches somebody working through a
 * wordlist against one account. Per-address catches somebody working through a list of accounts
 * from one machine, which the per-email counter never sees because no single email fails twice.
 * The address budget is deliberately looser: an office, a university or a mobile carrier can put
 * hundreds of legitimate people behind one address, and locking that out is a self-inflicted
 * outage.
 *
 * <p><strong>Why in memory, and what that costs.</strong> The brief ruled out a new dependency,
 * so this is a {@link ConcurrentHashMap} rather than Redis. The honest consequences: counters
 * reset when the process restarts, and two instances behind a load balancer each keep their own
 * tally, so the effective limit is the configured one multiplied by the instance count. Both are
 * acceptable for a throttle whose job is to turn an unbounded attack into a slow one — and the
 * seam is one interface wide, so the day this runs as more than one instance the map becomes a
 * shared store without touching the sign-in flow.
 *
 * <p>The map is keyed partly by attacker-supplied input, which makes it a memory-exhaustion
 * target in its own right; {@link #MAX_TRACKED_KEYS} and the sweep in {@link #recordFailure} are
 * what stop a script from filling the heap with invented email addresses.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * How much looser the per-address budget is than the per-email one.
     *
     * <p>Four accounts' worth of failures from one address before it is held back: enough that a
     * shared office does not lock itself out on a bad morning, tight enough that a list of
     * stolen emails cannot be worked through from one machine.
     */
    static final int ADDRESS_BUDGET_MULTIPLIER = 4;

    /**
     * Upper bound on distinct emails and addresses tracked at once.
     *
     * <p>Roughly a megabyte of small records. Once reached, entries already past their window
     * are swept; if the map is still full, new keys are not admitted — the throttle degrades to
     * "not tracking this one" rather than to "out of memory", which is the right way round.
     */
    static final int MAX_TRACKED_KEYS = 20_000;

    private static final String EMAIL_PREFIX = "email:";
    private static final String ADDRESS_PREFIX = "ip:";

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final int maxEmailFailures;
    private final int maxAddressFailures;
    private final Duration lockout;
    private final Clock clock;

    /**
     * The injection point.
     *
     * <p>{@code @Autowired} is not decoration here, it is required. Spring infers a constructor
     * only when a class declares exactly one; the test seam below makes two, and faced with a
     * choice it does not make one — it looks for a no-arg constructor, fails to find it, and the
     * application does not start. Annotating the intended constructor is what resolves that, and
     * it is the price of keeping the seam.
     */
    @Autowired
    public LoginAttemptService(ResumeIqProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * Test seam. The behaviour that matters most here — that a quiet window forgives earlier
     * mistakes — is a function of elapsed time, and the only alternatives to injecting a clock
     * are a test that sleeps for the lockout window or a window nobody would configure. Both are
     * worse than one extra constructor.
     */
    LoginAttemptService(ResumeIqProperties properties, Clock clock) {
        this.maxEmailFailures = properties.auth().maxLoginAttempts();
        this.maxAddressFailures = maxEmailFailures * ADDRESS_BUDGET_MULTIPLIER;
        this.lockout = Duration.ofMinutes(properties.auth().lockoutMinutes());
        this.clock = clock;
    }

    /**
     * Refuses the attempt if either counter is in its lockout window.
     *
     * <p>Called before the password is checked, so a locked-out caller never reaches BCrypt.
     * The message names neither counter and neither the email nor the address: an attacker who
     * has locked something out should not learn which thing they locked.
     *
     * @throws TooManyAttemptsException carrying how long to wait
     */
    public void assertAttemptAllowed(String email, String ipAddress) {
        Instant now = clock.instant();
        Duration wait = longestRemainingLock(
                remainingLock(emailKey(email), now), remainingLock(addressKey(ipAddress), now));
        if (wait != null) {
            throw new TooManyAttemptsException(
                    "Too many sign-in attempts. Please wait before trying again.", wait);
        }
    }

    /** Counts a failure against both the email and the address. */
    public void recordFailure(String email, String ipAddress) {
        Instant now = clock.instant();
        sweepIfCrowded(now);
        register(emailKey(email), maxEmailFailures, now);
        register(addressKey(ipAddress), maxAddressFailures, now);
    }

    /**
     * Clears both counters after a correct password.
     *
     * <p>Clearing the address counter on success is deliberate: a real person signing in from an
     * address is evidence the traffic is not a script, and without it one bad morning would
     * follow an office around for the rest of the lockout window.
     */
    public void recordSuccess(String email, String ipAddress) {
        attempts.remove(emailKey(email));
        attempts.remove(addressKey(ipAddress));
    }

    /** Test seam and operational escape hatch. Drops every counter. */
    void reset() {
        attempts.clear();
    }

    /** Failures currently counted against an email, ignoring any that have aged out. */
    int failuresForEmail(String email) {
        AttemptRecord record = attempts.get(emailKey(email));
        return record == null || record.hasAgedOut(clock.instant(), lockout) ? 0 : record.failures();
    }

    private void register(String key, int allowance, Instant now) {
        // Read outside the mapping function: ConcurrentHashMap holds a bin lock during compute,
        // and reaching back into the same map from inside it is asking for trouble.
        boolean atCapacity = attempts.size() >= MAX_TRACKED_KEYS;

        AttemptRecord updated = attempts.compute(key, (ignoredKey, existing) -> {
            if (existing == null && atCapacity) {
                // Map is full even after sweeping. Admitting this key would trade a bounded
                // throttle for an unbounded one, so the failure goes uncounted instead.
                return null;
            }
            AttemptRecord base = existing == null || existing.hasAgedOut(now, lockout)
                    ? AttemptRecord.empty()
                    : existing;
            return base.withFailure(now, allowance, lockout);
        });

        if (updated != null && updated.failures() == allowance) {
            // Logged on the attempt that tips the key over, so one lockout produces one line.
            // The key is logged, not the password or the attempt itself: an operator needs to
            // know what is being hammered; nobody needs the credentials that were tried.
            log.warn("Sign-in lockout started for {} after {} failed attempt(s)",
                    key, updated.failures());
        }
    }

    /** Remaining lock on one key, or null if that key is not locked. */
    private Duration remainingLock(String key, Instant now) {
        AttemptRecord record = attempts.get(key);
        if (record == null || record.lockedUntil() == null || !record.lockedUntil().isAfter(now)) {
            return null;
        }
        return Duration.between(now, record.lockedUntil());
    }

    private static Duration longestRemainingLock(Duration first, Duration second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.compareTo(second) >= 0 ? first : second;
    }

    /**
     * Drops records whose window has passed, but only once the map is large enough for it to
     * matter — sweeping on every failed login would be a full scan for no benefit.
     */
    private void sweepIfCrowded(Instant now) {
        if (attempts.size() < MAX_TRACKED_KEYS) {
            return;
        }
        int before = attempts.size();
        attempts.values().removeIf(record -> record.hasAgedOut(now, lockout));
        log.info("Swept sign-in attempt records: {} -> {}", before, attempts.size());
    }

    private static String emailKey(String email) {
        return EMAIL_PREFIX + User.normalizeEmail(email);
    }

    private static String addressKey(String ipAddress) {
        return ADDRESS_PREFIX + (ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress);
    }

    /**
     * One key's tally. Immutable, so {@link ConcurrentHashMap#compute} can replace it
     * atomically and two simultaneous failed logins cannot interleave into a lost increment.
     *
     * @param failures     failures counted in the current window
     * @param lastFailedAt when the most recent one happened
     * @param lockedUntil  when the lock lifts, or null if the allowance is not spent yet
     */
    private record AttemptRecord(int failures, Instant lastFailedAt, Instant lockedUntil) {

        static AttemptRecord empty() {
            return new AttemptRecord(0, null, null);
        }

        /**
         * Adds one failure, starting the lock if the allowance is now spent.
         *
         * <p>A further failure while locked would push the deadline out again, which would make
         * an account holdable hostage indefinitely — except that the check runs before the
         * password is ever compared, so a locked key never reaches this method. The ordering in
         * {@code AuthService} is what makes that true, and it is the reason it is ordered that
         * way.
         */
        AttemptRecord withFailure(Instant now, int allowance, Duration lockout) {
            int total = failures + 1;
            Instant until = total >= allowance ? now.plus(lockout) : lockedUntil;
            return new AttemptRecord(total, now, until);
        }

        /**
         * True once the whole record is stale: not locked, and nothing has failed for a full
         * window. A quiet hour is what forgives earlier mistakes, so an honest person who
         * mistyped four times yesterday starts today with a clean slate.
         */
        boolean hasAgedOut(Instant now, Duration lockout) {
            boolean lockFinished = lockedUntil == null || !lockedUntil.isAfter(now);
            boolean idle = lastFailedAt == null || !lastFailedAt.plus(lockout).isAfter(now);
            return lockFinished && idle;
        }
    }
}
