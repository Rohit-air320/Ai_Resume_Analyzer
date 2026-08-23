package com.resumeiq.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Type-safe binding for every {@code resumeiq.*} setting.
 *
 * <p>Validated at startup, so a missing or malformed value fails fast with a clear
 * message instead of surfacing as a null pointer during the first request.
 *
 * @param app  application identity reported by the health endpoint
 * @param cors browser origins permitted to call this API
 */
@Validated
@ConfigurationProperties(prefix = "resumeiq")
public record ResumeIqProperties(
        @Valid @NotNull App app,
        @Valid @NotNull Cors cors
) {

    /**
     * @param name    display name of the product
     * @param version version reported by {@code GET /api/health}
     */
    public record App(@NotBlank String name, @NotBlank String version) {
    }

    /**
     * @param allowedOrigins exact browser origins allowed to call the API.
     *                       Wildcards are intentionally unsupported: credentialed
     *                       requests (Phase 3) require explicit origins.
     */
    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
