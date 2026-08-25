package com.resumeiq.health;

import com.resumeiq.common.domain.Timestamps;
import com.resumeiq.config.ResumeIqProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public liveness endpoint. Deliberately unauthenticated (see the Phase 3 filter chain)
 * so the frontend can confirm connectivity before a user has signed in, and so platform
 * health checks work without credentials.
 *
 * <p>It reports configuration rather than internals: no database URLs, no versions of
 * third-party libraries, nothing worth reconnoitring.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Service liveness and build information")
public class HealthController {

    private final ResumeIqProperties properties;
    private final Environment environment;

    public HealthController(ResumeIqProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @GetMapping
    @Operation(summary = "Report service status", description = "Returns UP when the API is able to serve requests.")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                properties.app().name(),
                properties.app().version(),
                List.of(environment.getActiveProfiles()),
                Timestamps.now());
    }
}
