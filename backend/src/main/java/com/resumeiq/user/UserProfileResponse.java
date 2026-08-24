package com.resumeiq.user;

import java.time.Instant;
import java.util.UUID;

/**
 * The account, as the frontend is allowed to see it.
 *
 * <p>Built by hand from the entity rather than serialised from it. That is the spec's "never
 * expose entities" rule, and the reason it exists is visible here: {@code User} also holds
 * {@code passwordHash}, and an entity returned directly from a controller is one forgotten
 * annotation away from putting it on the wire. A record with an explicit list of components
 * cannot leak a field that was added to the table later.
 *
 * @param id             public identifier — the {@code publicId}, never the database key
 * @param email          login address
 * @param fullName       display name
 * @param targetRole     role the person is aiming for, null until they set one
 * @param experienceLevel self-reported level, null until they set one
 * @param role           authorisation role, so the UI can hide what it must not offer
 * @param memberSince    when the account was created
 * @param lastLoginAt    previous sign-in, null on the very first one
 */
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String targetRole,
        ExperienceLevel experienceLevel,
        Role role,
        Instant memberSince,
        Instant lastLoginAt
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getTargetRole(),
                user.getExperienceLevel(),
                user.getRole(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
