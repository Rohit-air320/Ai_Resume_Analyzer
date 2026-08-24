package com.resumeiq.auth;

import com.resumeiq.user.User;
import com.resumeiq.user.UserProfileResponse;

import java.time.Duration;

/**
 * What a successful register, login or refresh returns.
 *
 * <p>Note what is not here: the refresh token. It travels only as an httpOnly cookie, so no
 * JavaScript on the page can read it — which is the whole point of the split. If it appeared in
 * this body as well, a single cross-site scripting bug would hand over a week of access instead
 * of the fifteen minutes an access token is worth.
 *
 * <p>{@code expiresInSeconds} is sent so the client can refresh a little early rather than
 * waiting to be told 401. Decoding the token to find its expiry would work too, but that would
 * make the frontend depend on the token's internal shape, and a bearer token should be opaque to
 * the code carrying it.
 *
 * @param accessToken       short-lived JWT, held in memory by the frontend and never in storage
 * @param tokenType         always {@code Bearer}; sent so the client does not hardcode the scheme
 * @param expiresInSeconds  lifetime of this access token
 * @param user              the signed-in account, so the UI can render immediately without a
 *                          second call
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse user
) {

    private static final String BEARER = "Bearer";

    public static AuthResponse of(String accessToken, Duration lifetime, User user) {
        return new AuthResponse(
                accessToken, BEARER, lifetime.toSeconds(), UserProfileResponse.from(user));
    }
}
