package com.resumeiq.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin configuration for the Vite dev server and any deployed frontend.
 *
 * <p>Origins come from {@code resumeiq.cors.allowed-origins} (env var
 * {@code CORS_ALLOWED_ORIGINS}), never from a hardcoded localhost URL, so the same
 * build runs locally and in production.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ResumeIqProperties properties;

    public WebMvcConfig(ResumeIqProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
