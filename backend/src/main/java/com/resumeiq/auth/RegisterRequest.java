package com.resumeiq.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}.
 *
 * <p>The upper bound on the password is 72 characters, and that number is not arbitrary: BCrypt
 * hashes at most 72 bytes and silently ignores everything after them. Accepting a longer value
 * would mean somebody who typed a 90-character passphrase could later sign in with only its
 * first 72 — a truncation nobody told them about. Rejecting it is more honest than hiding it.
 *
 * <p>There is no {@code toString} override to write here: records generate one that includes
 * every component, so this type is deliberately never logged. The password lives in memory long
 * enough to be hashed and goes no further.
 *
 * @param email    login identity, normalised to lower case before it is stored
 * @param password plaintext, hashed on arrival and never persisted in this form
 * @param fullName display name, shown in the sidebar and on reports
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        String fullName
) {
}
