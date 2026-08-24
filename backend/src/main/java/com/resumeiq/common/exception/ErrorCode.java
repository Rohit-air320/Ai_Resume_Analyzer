package com.resumeiq.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error identifiers returned in every error response.
 *
 * <p>The frontend switches on these codes to pick user-facing copy, so error text can
 * change freely without breaking clients, and no client ever string-matches English.
 * Each code carries the HTTP status it maps to, keeping status selection in one place.
 */
public enum ErrorCode {

    /** Request body or parameters failed Bean Validation. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /** Request was syntactically fine but semantically unusable. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST),

    /** No credentials, or credentials that are expired or malformed. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /**
     * Email and password did not match an account.
     *
     * <p>One code for both "no such email" and "wrong password", because two codes would
     * turn the login form into an account-enumeration oracle.
     */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    /**
     * The refresh token is missing, expired, or was already used.
     *
     * <p>Distinct from {@link #UNAUTHORIZED} because the frontend must react differently:
     * an expired access token is worth one silent refresh, whereas this means the session
     * is finished and the only correct move is to show the login screen.
     */
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),

    /** Too many attempts from this email or address. Carries {@code Retry-After}. */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),

    /** Authenticated, but the resource belongs to somebody else. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** Resource does not exist, or does not exist for this user. */
    NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Unique constraint violation, e.g. registering an email that already exists. */
    CONFLICT(HttpStatus.CONFLICT),

    /** Correct path, wrong HTTP verb. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** Request Content-Type is not one this endpoint accepts. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** Upload was not a PDF or DOCX. */
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** Upload exceeded the configured maximum size. */
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

    /** File was accepted but no text could be extracted (empty, corrupt, or scanned). */
    UNREADABLE_FILE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** AI provider was unreachable or timed out. */
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    /** AI responded, but the payload failed schema validation. */
    AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY),

    /** Anything unanticipated. Details are logged, never returned. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /**
     * Maps a status raised by Spring MVC itself (unknown path, wrong verb, unsupported
     * media type) onto our vocabulary, so framework errors reach the client in exactly
     * the same shape as application errors.
     */
    public static ErrorCode fromStatus(int statusCode) {
        return switch (statusCode) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> FILE_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            default -> statusCode >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
        };
    }
}
