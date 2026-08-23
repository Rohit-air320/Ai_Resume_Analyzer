package com.resumeiq.health;

import java.time.Instant;
import java.util.List;

/**
 * Payload of {@code GET /api/health}.
 *
 * @param status         always {@code UP} when the application can answer requests
 * @param application    product name from configuration
 * @param version        application version from configuration
 * @param activeProfiles which Spring profiles are running, e.g. {@code [dev]}
 * @param checkedAt      server time the check was answered
 */
public record HealthResponse(
        String status,
        String application,
        String version,
        List<String> activeProfiles,
        Instant checkedAt
) {
}
