package com.resumeiq.common.exception;

/**
 * Thrown when a resource does not exist — or, just as importantly, does not exist
 * <em>for the requesting user</em>.
 *
 * <p>Ownership failures on a lookup deliberately surface as 404 rather than 403: telling
 * an attacker "this analysis exists but is not yours" leaks the existence of other users'
 * data. Read paths return not-found; explicit write attempts use
 * {@link ForbiddenException}.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.NOT_FOUND, "%s %s was not found".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
