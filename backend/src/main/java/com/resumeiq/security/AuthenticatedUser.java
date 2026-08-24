package com.resumeiq.security;

import com.resumeiq.user.Role;
import com.resumeiq.user.User;

import java.util.UUID;

/**
 * The caller, as the rest of the application sees them.
 *
 * <p>This is the principal held in the security context, and it is deliberately not an
 * entity. Two reasons. A detached {@code User} on the request thread invites lazy-loading
 * surprises with {@code open-in-view: false}, and it carries the password hash — one
 * careless log line away from being written to disk. A record of four flat values cannot
 * leak what it does not hold.
 *
 * @param id       internal row id, used to scope every repository query to this owner
 * @param publicId the id this account is known by outside the database
 * @param email    normalised login identity
 * @param fullName display name, so the header does not need a second request
 * @param role     authorisation role, mapped to a Spring Security authority
 */
public record AuthenticatedUser(Long id, UUID publicId, String email, String fullName, Role role) {

    /** Spring Security's convention: authorities checked with {@code hasRole} carry this prefix. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    public static AuthenticatedUser of(User user) {
        return new AuthenticatedUser(
                user.getId(), user.getPublicId(), user.getEmail(), user.getFullName(), user.getRole());
    }

    public String authority() {
        return AUTHORITY_PREFIX + role.name();
    }
}
