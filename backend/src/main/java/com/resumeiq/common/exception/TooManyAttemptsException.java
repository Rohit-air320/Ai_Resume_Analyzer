package com.resumeiq.common.exception;

import java.time.Duration;

/**
 * Thrown when an identity or an address has failed too many attempts in a row.
 *
 * <p>It carries the wait so the handler can send a {@code Retry-After} header: a client that
 * is told how long to wait can show a countdown instead of hammering the endpoint, and an
 * honest user locked out by somebody else's guessing at least learns when it ends.
 */
public class TooManyAttemptsException extends ApiException {

    private final Duration retryAfter;

    public TooManyAttemptsException(String message, Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /** Whole seconds, rounded up and never zero, which is what {@code Retry-After} takes. */
    public long retryAfterSeconds() {
        return Math.max(1L, (retryAfter.toMillis() + 999L) / 1000L);
    }
}
