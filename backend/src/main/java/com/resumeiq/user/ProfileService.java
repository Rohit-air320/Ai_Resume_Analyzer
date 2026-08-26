package com.resumeiq.user;

import com.resumeiq.common.exception.ResourceNotFoundException;
import com.resumeiq.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading and editing the signed-in account.
 *
 * <p>Two operations over one row, and the interesting part is the row it is allowed to reach. The
 * identifier comes from the authenticated caller, never from the path or the body, so there is no
 * request shape that names somebody else's account — the spec's "do not trust user-provided ids"
 * enforced by not accepting one.
 *
 * <p>What a caller may change is bounded by {@link UpdateProfileRequest} rather than by a check in
 * here: it has three components, none of which is an email, a password or a role. That is worth doing
 * with the type rather than with an {@code if}, because an {@code if} has to be remembered when a field
 * is added to the record and the record has to be edited on purpose.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    /** The caller's own account. */
    @Transactional(readOnly = true)
    public UserProfileResponse of(AuthenticatedUser caller) {
        return UserProfileResponse.from(require(caller));
    }

    /**
     * Updates the three editable fields.
     *
     * <p>A full replacement rather than a patch, which is why {@code fullName} is {@code @NotBlank} and
     * the two optional fields are cleared when omitted. A partial update over a record cannot tell
     * "leave the target role alone" from "I no longer have one" — both arrive as null — and inventing a
     * sentinel to distinguish them would be a worse API than sending three fields.
     */
    @Transactional
    public UserProfileResponse update(AuthenticatedUser caller, UpdateProfileRequest request) {
        User user = require(caller);
        user.setFullName(request.fullName().strip());
        user.setTargetRole(blankToNull(request.targetRole()));
        user.setExperienceLevel(request.experienceLevel());

        // The name is not logged — it is the one field here that identifies a person.
        log.info("Updated profile for user {}", caller.publicId());
        return UserProfileResponse.from(users.save(user));
    }

    /**
     * The caller's row.
     *
     * <p>A valid token for an account that no longer exists is a 404 rather than a 500. It is a real
     * case — a token outlives the row it was minted for by however long is left on its expiry — and
     * "this account is gone" is a truthful answer the client can act on by signing out.
     */
    private User require(AuthenticatedUser caller) {
        return users.findById(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", caller.publicId()));
    }

    /**
     * Empty means absent.
     *
     * <p>An empty target role and a missing one are the same fact, and storing {@code ""} would make
     * every reader test for two things. The dashboard's empty state checks for null; a stray empty
     * string there renders a heading with a blank in it.
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
