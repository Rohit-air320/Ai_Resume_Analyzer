package com.resumeiq.common.exception;

/**
 * Base type for every error this application raises deliberately.
 *
 * <p>Carrying an {@link ErrorCode} means the HTTP status is decided where the error is
 * detected — in the service that understands the situation — instead of being guessed
 * later by a controller or an exception handler.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
