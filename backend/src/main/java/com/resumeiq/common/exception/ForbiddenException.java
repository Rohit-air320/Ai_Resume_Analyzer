package com.resumeiq.common.exception;

/** Thrown when an authenticated user attempts to act on somebody else's resource. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
