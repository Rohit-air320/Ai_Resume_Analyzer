package com.resumeiq.common.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The application's clock, at the precision the database can actually keep.
 *
 * <p>{@link Instant#now()} reads the platform clock, and how fine that clock is depends on the
 * machine: microseconds on most Linux hosts, <strong>100-nanosecond ticks on Windows</strong>.
 * A timestamp column is {@code TIMESTAMP(6)} in H2 and {@code DATETIME(6)} in MySQL — six
 * fractional digits, microseconds. So an entity holding {@code 14:35:59.464664900Z} is stored as
 * {@code 14:35:59.464665Z} and comes back rounded, and the object in memory no longer matches
 * the row it was written from.
 *
 * <p>That is not a rounding curiosity, it is an API inconsistency. {@code POST
 * /api/job-descriptions} serialises the entity it just saved, while every later {@code GET}
 * serialises the row read back — so the same posting reported two different {@code createdAt}
 * strings, and a client that compared or cached them was wrong through no fault of its own. The
 * failure is also platform-dependent, which is the worst kind: the test that found it passed on
 * Linux and failed on Windows.
 *
 * <p>Truncating at the source fixes it once for everything. An entity's timestamp is equal to
 * what the database holds from the moment it is set, so a round trip changes nothing and no
 * caller has to know any of this. The cost is a microsecond of resolution that nothing in this
 * product could use.
 *
 * <p>Use this instead of {@code Instant.now()} everywhere in {@code src/main} — the verifier's
 * {@code verify_stored_timestamps} check enforces it. Where a value is compared against a stored
 * one rather than written ({@code deleteByExpiresAtBefore}, a token's expiry test) truncation is
 * harmless, and one clock for the whole application beats a rule with exceptions to remember.
 */
public final class Timestamps {

    /**
     * What a timestamp column keeps: {@code DATETIME(6)}/{@code TIMESTAMP(6)} is microseconds.
     *
     * <p>If a column is ever declared with a different precision, this constant and that column
     * have to move together — which is the point of naming it rather than writing
     * {@code ChronoUnit.MICROS} in a lifecycle callback.
     */
    public static final ChronoUnit STORED_PRECISION = ChronoUnit.MICROS;

    private Timestamps() {
    }

    /** Now, rounded down to what a timestamp column can hold. */
    public static Instant now() {
        return Instant.now().truncatedTo(STORED_PRECISION);
    }

    /**
     * The same instant at storage precision. For a time that came from somewhere else — a parsed
     * request, a fixed clock in a test — and is about to be written to a column.
     *
     * @return {@code null} for a {@code null} input, so an optional column stays optional
     */
    public static Instant atStoredPrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(STORED_PRECISION);
    }
}
