package com.resumeiq.user;

import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in account.
 *
 * <p>No path variable, on purpose. {@code /api/profile} is always the caller's own profile, so there is
 * no id to tamper with and no ownership check to forget. An admin view of another account would be a
 * different path with a different guard, not this one with a parameter added.
 */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "Read and update the signed-in account")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(
            summary = "Get your profile",
            description = "Email, name, target role, experience level, role and sign-in dates. Never "
                    + "the password hash — the response is a record with a fixed list of fields, not a "
                    + "serialised entity.")
    public UserProfileResponse get(@CurrentUser AuthenticatedUser caller) {
        return profiles.of(caller);
    }

    @PutMapping
    @Operation(
            summary = "Update your profile",
            description = "Replaces the three editable fields and returns the saved profile. PUT rather "
                    + "than PATCH because it is a full replacement: send all three, and omitting the "
                    + "target role or experience level clears it. Email, password and role cannot be "
                    + "changed here — the request body has no field for them. Responds 400 if the name "
                    + "is missing or any field is too long.")
    public UserProfileResponse update(@CurrentUser AuthenticatedUser caller,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(caller, request);
    }
}
