package com.resumeiq.common.exception;

/** Thrown when a request collides with existing state, e.g. a duplicate email. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
