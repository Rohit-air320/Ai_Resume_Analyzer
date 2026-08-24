package com.resumeiq.user;

/**
 * Authorisation role. Kept as a single column rather than a join table because ResumeIQ has
 * exactly two kinds of caller and no concept of granular permissions; a {@code user_roles}
 * table would be three joins in service of one boolean.
 *
 * <p>Phase 3 maps these to Spring Security authorities by prefixing {@code ROLE_}.
 */
public enum Role {

    /** Owns resumes and analyses. Every account created through {@code /api/auth/register}. */
    USER,

    /** Reserved for operational endpoints. No self-service path creates one. */
    ADMIN
}
