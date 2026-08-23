package com.resumeiq.common.api;

import com.resumeiq.common.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every failed request returns, whatever went wrong.
 *
 * <p>One predictable envelope means the frontend has exactly one error parser. {@code code}
 * is a stable {@link ErrorCode} name for logic, {@code message} is human-readable text safe
 * to display, and {@code fieldErrors} is present only for validation failures. Stack traces
 * and exception class names never appear here — they go to the log.
 *
 * @param code        stable machine-readable identifier, e.g. {@code UNSUPPORTED_FILE_TYPE}
 * @param message     short explanation safe to show a user
 * @param fieldErrors per-field problems, omitted unless this is a validation failure
 * @param path        request path that produced the error
 * @param timestamp   when the error was produced
 */
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldViolation> fieldErrors,
        String path,
        Instant timestamp
) {

    /**
     * @param field   name of the offending field, e.g. {@code email}
     * @param message what is wrong with it, e.g. {@code must be a valid email address}
     */
    public record FieldViolation(String field, String message) {
    }

    public static ApiErrorResponse of(ErrorCode code, String message, String path) {
        return new ApiErrorResponse(code.name(), message, null, path, Instant.now());
    }

    public static ApiErrorResponse validation(String message, List<FieldViolation> fieldErrors, String path) {
        return new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED.name(), message, List.copyOf(fieldErrors), path, Instant.now());
    }
}
