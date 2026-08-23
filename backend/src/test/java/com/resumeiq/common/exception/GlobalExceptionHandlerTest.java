package com.resumeiq.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins down the error contract the whole frontend is written against.
 *
 * <p>Uses a throwaway controller and a standalone MockMvc so it tests the handler itself
 * rather than any particular feature, and runs without a Spring context.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                // Standalone MockMvc skips Boot's auto-configuration, so without this the
                // test would serialise differently from production and could not prove
                // that nulls are omitted or that Instant becomes an ISO-8601 string.
                .setMessageConverters(new MappingJackson2HttpMessageConverter(productionObjectMapper()))
                .build();
    }

    /** Mirrors the Jackson settings in application.yml. */
    private static ObjectMapper productionObjectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Test
    @DisplayName("A missing resource returns 404 with the NOT_FOUND code")
    void mapsResourceNotFound() throws Exception {
        mockMvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resume 42 was not found"))
                .andExpect(jsonPath("$.path").value("/test/missing"))
                .andExpect(jsonPath("$.timestamp").isString())
                // An absent key, not a null one: the frontend checks for presence.
                .andExpect(content().string(not(containsString("fieldErrors"))));
    }

    @Test
    @DisplayName("An ownership failure returns 403 with the FORBIDDEN code")
    void mapsForbidden() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("An unexpected exception returns a generic 500 and leaks nothing")
    void hidesUnexpectedFailureDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong on our side. Please try again."))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("jdbc:"))));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/missing")
        void missing() {
            throw new ResourceNotFoundException("Resume", 42);
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenException("This analysis belongs to another user");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("connection to jdbc:mysql://secret-host failed");
        }
    }
}
