package com.resumeiq.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signs a user up, uploads a resume, saves a posting — over HTTP, the way a browser would.
 *
 * <p>The analysis endpoints are the first in this project whose fixtures are other endpoints: an
 * analysis needs a stored resume with extracted text and a saved posting, and both of those are
 * several requests away from a bare account. Building them with {@code JdbcTemplate} inserts would be
 * shorter and would quietly skip extraction, hashing and validation, so what the tests then analysed
 * would be a row no upload could actually produce.
 *
 * <p>It takes {@link MockMvc} and the mapper rather than being a Spring bean, because it belongs to
 * whichever test context is running and has no business being injectable into the application.
 *
 * <p>Every method asserts its own success status. A test that fails should fail on its own assertion,
 * not three lines later on a null id.
 */
public final class ApiClient {

    /** Long enough for the password policy, and obviously not a real one. */
    public static final String PASSWORD = "example-passphrase-9";

    private final MockMvc mockMvc;
    private final ObjectMapper json;

    public ApiClient(MockMvc mockMvc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    /** Registers an account and returns its access token. */
    public String signUp(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", fullName))))
                .andExpect(status().isCreated());
        return signIn(email);
    }

    /** Signs in an existing account. Useful for asserting that a change survived a new token. */
    public String signIn(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    /** Uploads a PDF whose text is the given lines, and returns the resume's public id. */
    public String uploadResume(String token, String label, String... lines) throws Exception {
        return idOf(uploadPdf(token, label, DocumentFixtures.pdf(lines))
                .andExpect(status().isCreated()));
    }

    /**
     * Uploads arbitrary bytes as a PDF, for the cases where the upload is meant to go wrong.
     *
     * <p>Returns the {@link ResultActions} rather than an id so the caller can assert on a failure —
     * a blank page uploads successfully and extracts nothing, which is exactly the resume the analysis
     * endpoint has to refuse.
     */
    public ResultActions uploadPdf(String token, String label, byte[] content) throws Exception {
        MockHttpServletRequestBuilder request = multipart("/api/resumes/upload")
                .file(new MockMultipartFile("file", "resume.pdf",
                        MediaType.APPLICATION_PDF_VALUE, content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (label != null) {
            request = request.param("label", label);
        }
        return mockMvc.perform(request);
    }

    /** Saves a job description and returns its public id. */
    public String savePosting(String token, String title, String company, String text)
            throws Exception {
        // A LinkedHashMap rather than Map.of because the company is legitimately null and Map.of
        // throws on a null value — the one place a nullable field makes the terse form unusable.
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("company", company);
        body.put("text", text);

        return idOf(mockMvc.perform(post("/api/job-descriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()));
    }

    /** Runs an analysis, asserting only that it was created. */
    public ResultActions analyse(String token, String resumeId, String jobDescriptionId)
            throws Exception {
        return mockMvc.perform(post("/api/analyses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "resumeId", resumeId,
                        "jobDescriptionId", jobDescriptionId))));
    }

    /** Runs an analysis and returns its body, having asserted a 201. */
    public JsonNode analysisOf(String token, String resumeId, String jobDescriptionId)
            throws Exception {
        return bodyOf(analyse(token, resumeId, jobDescriptionId).andExpect(status().isCreated()));
    }

    /** An authenticated GET, asserting a 200 and returning the parsed body. */
    public JsonNode getJson(String token, String path) throws Exception {
        return bodyOf(mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()));
    }

    public JsonNode bodyOf(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    public String idOf(ResultActions actions) throws Exception {
        return bodyOf(actions).get("id").asText();
    }
}
