package com.resumeiq.common.exception;

/**
 * Thrown when a request carries no usable identity.
 *
 * <p>It takes an {@link ErrorCode} rather than fixing one, because 401 covers three
 * situations the frontend has to tell apart: bad credentials at the login form, an access
 * token that has simply aged out, and a refresh token that is gone or has been replaced.
 * The status is the same; the correct next move is not.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /** Wrong email or wrong password — deliberately indistinguishable to the caller. */
    public static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(
                ErrorCode.INVALID_CREDENTIALS, "That email and password combination is not correct.");
    }

    /** The refresh token is missing, expired, or has already been exchanged once. */
    public static UnauthorizedException sessionExpired() {
        return new UnauthorizedException(
                ErrorCode.SESSION_EXPIRED, "Your session has ended. Please sign in again.");
    }
}
