package com.resumeiq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. Swagger UI is served at {@code /swagger-ui.html} and
 * doubles as living API documentation for {@code docs/api.md}.
 *
 * <p>The JWT bearer security scheme is registered in Phase 3, alongside the filter
 * chain that actually enforces it.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI resumeIqOpenApi(ResumeIqProperties properties) {
        return new OpenAPI().info(new Info()
                .title(properties.app().name() + " API")
                .version(properties.app().version())
                .description("""
                        Analyses a candidate resume against a target job description and returns
                        ATS compatibility, job match, skill gaps, keyword coverage and actionable
                        recommendations.

                        All analysis runs server side. The AI provider key never reaches the browser.
                        """)
                .contact(new Contact().name("ResumeIQ"))
                .license(new License().name("MIT")));
    }
}
