package com.resumeiq.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The three profile fields a user can change.
 *
 * <p>Note what is not here: no email, no password, no role. Each of those is a different operation
 * with a different guard — changing an email needs re-verification, changing a password needs the old
 * one, and changing a role is not a thing a user does to themselves — and folding them into one
 * "update profile" request is how an endpoint ends up with a nullable field that quietly grants an
 * account administrator rights. A request that cannot express a privilege change cannot be made to
 * perform one.
 *
 * @param targetRole      the role being aimed at, used as the default title on a new analysis and to
 *                        make the dashboard's empty state specific. Optional
 * @param experienceLevel optional, and null is meaningful — "prefer not to say" rather than "entry"
 */
@Schema(description = "Editable profile fields")
public record UpdateProfileRequest(

        @Schema(description = "Display name", example = "Rohit Sharma")
        @NotBlank(message = "Enter your name.")
        @Size(max = 120, message = "Names are limited to 120 characters.")
        String fullName,

        @Schema(description = "The role you are targeting", example = "Backend Engineer")
        @Size(max = 120, message = "Target roles are limited to 120 characters.")
        String targetRole,

        @Schema(description = "Where you are in your career")
        ExperienceLevel experienceLevel
) {
}
