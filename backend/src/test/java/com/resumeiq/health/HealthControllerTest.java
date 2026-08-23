package com.resumeiq.health;

import com.resumeiq.config.ResumeIqProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test. Verifies the route, the JSON field names the frontend depends on,
 * and that dates serialise as ISO-8601 strings rather than epoch numbers.
 */
@WebMvcTest(HealthController.class)
@EnableConfigurationProperties(ResumeIqProperties.class)
@ActiveProfiles("dev")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health reports UP with application identity")
    void returnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("ResumeIQ"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.activeProfiles[0]").value("dev"))
                .andExpect(jsonPath("$.checkedAt").isString());
    }
}
