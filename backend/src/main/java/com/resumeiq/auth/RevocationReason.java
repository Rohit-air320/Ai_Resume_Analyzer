package com.resumeiq.auth;

/**
 * Why a refresh token stopped being usable.
 *
 * <p>Kept because "revoked" alone cannot answer the question that matters after an incident:
 * was this session ended by the person, by the normal rotation of a token, or by somebody
 * replaying a token that had already been spent?
 */
public enum RevocationReason {

    /** Exchanged for a new token in the normal way. The expected end of every token's life. */
    ROTATED,

    /** The person signed out. Ends the whole family, on every device that shared it. */
    SIGNED_OUT,

    /**
     * A token was presented that had already been used.
     *
     * <p>Either the token was stolen and replayed, or the legitimate client retried after its
     * rotation response was lost. Both cases are treated as theft: the entire family is
     * revoked and the person signs in again. A false alarm costs one login; the alternative
     * costs the account.
     */
    REUSE_DETECTED
}
