package com.resumeiq.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/login}.
 *
 * <p>Validation here is looser than on registration on purpose. A sign-in form's job is to
 * compare what was typed against what is stored, not to grade it: telling somebody their entry
 * is "too short to be a password" reveals the rule the stored password satisfies, and rejecting
 * a malformed address with a different response than a wrong one hands over a cheap way to test
 * which addresses are worth attacking. So both fields are checked only for presence and for a
 * length that keeps a megabyte of junk out of BCrypt, and everything else is one 401.
 *
 * @param email    address as typed; normalised before lookup
 * @param password plaintext, compared against the stored hash and then discarded
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 200, message = "Password must be at most 200 characters")
        String password
) {
}
