package com.resumeiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the ResumeIQ REST API.
 *
 * <p>Packages are organised by feature (auth, resume, jobdescription, analysis, ai)
 * rather than by technical layer, so everything belonging to one capability lives
 * together and module boundaries stay visible.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ResumeIqApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeIqApplication.class, args);
    }
}
