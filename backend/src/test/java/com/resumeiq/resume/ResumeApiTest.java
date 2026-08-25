package com.resumeiq.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.support.DocumentFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The four resume endpoints over HTTP, with real files, a real filter chain and a real disk.
 *
 * <p>The tests that matter most in this class are the ones about other people's resumes.
 * {@link #keepsResumesPrivateBetweenAccounts()} and {@link #willNotDeleteSomebodyElsesResume()}
 * are the spec's first named requirement — that a user can reach only their own data — and
 * they are the failures a slice test cannot honestly cover, because the answer depends on the
 * token in the header, the query in the repository and the status the handler chooses, all
 * agreeing.
 *
 * <p>Two property overrides. A database of its own, because these tests upload through HTTP
 * and HTTP commits: there is no test transaction wrapping a MockMvc call to roll back, and
 * those rows would otherwise appear inside repository slices that never asked for them. And a
 * storage directory under {@code build/}, which is disposable, is not on the classpath, and is
 * emptied when the class finishes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:resumeiq-resumes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "resumeiq.seed.skills=false",
        "resumeiq.auth.bcrypt-strength=4",
        "resumeiq.upload.storage-dir=" + ResumeApiTest.STORAGE_DIR,
        "resumeiq.upload.max-resumes-per-user=3",
})
class ResumeApiTest {

    /** Referenced from the annotation above, so it has to be a compile-time constant. */
    static final String STORAGE_DIR = "./build/test-uploads/resume-api";

    private static final String PASSWORD = "example-passphrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ResumeStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearState() {
        // These tests commit, so each one starts by emptying the tables it writes to. Resumes
        // first: the foreign key points that way.
        jdbc.update("delete from resumes");
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from users");
    }

    @AfterAll
    static void removeStoredFiles() throws IOException {
        Path root = Paths.get(STORAGE_DIR);
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(root)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    @DisplayName("a PDF upload is created, read, and reported with what was extracted")
    void uploadsAPdf() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        String body = upload(token, "priya-cv.pdf", MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines()), "Backend CV")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Backend CV"))
                .andExpect(jsonPath("$.originalFilename").value("priya-cv.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.status").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.analysable").value(true))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.wordCount").isNumber())
                // The internal row id and the storage key are not part of the contract, and the
                // key especially: a client that learns a filename on the server will try to ask
                // for it.
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).get("wordCount").asInt()).isGreaterThan(40);
    }

    @Test
    @DisplayName("a DOCX upload works the same way, and the label defaults to the filename")
    void uploadsADocx() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        upload(token, "Priya Resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DocumentFixtures.docx(DocumentFixtures.realisticResumeLines()), null)
                .andExpect(status().isCreated())
                // No label was sent, so the filename stands in — without its extension, because
                // "Priya Resume" reads better in a list than "Priya Resume.docx".
                .andExpect(jsonPath("$.label").value("Priya Resume"))
                .andExpect(jsonPath("$.status").value("TEXT_EXTRACTED"));
    }

    @Test
    @DisplayName("the file is judged by its contents, not by its name or its Content-Type")
    void ignoresWhatTheRequestClaims() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        // Named .pdf, declared application/pdf, actually a DOCX. Both pieces of metadata are
        // chosen by whoever is uploading, so neither decides anything.
        upload(token, "resume.pdf", MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.docx(DocumentFixtures.realisticResumeLines()), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    @DisplayName("a format we cannot read is refused with 415")
    void refusesAnUnsupportedFormat() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        upload(token, "resume.pdf", MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.plainText(), null)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value(containsString("PDF, DOCX")));
    }

    @Test
    @DisplayName("a legacy .doc is refused with the fix rather than a shrug")
    void refusesLegacyWordDocuments() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        upload(token, "resume.doc", "application/msword", DocumentFixtures.legacyDoc(), null)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(containsString("save it as .docx")));
    }

    @Test
    @DisplayName("a file that stores but cannot be read is kept, and says why")
    void recordsAnExtractionFailure() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        // A scan: valid PDF, no text. The row exists so the failure is visible in the list and
        // can be deleted, rather than being a toast message the user has no record of.
        upload(token, "scan.pdf", MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.pdfWithNoText(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.analysable").value(false))
                .andExpect(jsonPath("$.extractionError")
                        .value(containsString("Almost no text")));
    }

    @Test
    @DisplayName("an empty part is a bad request, not a stored empty file")
    void refusesAnEmptyFile() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        upload(token, "resume.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0], null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a file to upload."));
    }

    @Test
    @DisplayName("the list is newest first, metadata only, and never includes resume text")
    void listsResumes() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        uploadPdf(token, "first.pdf", "First");
        uploadPdf(token, "second.pdf", "Second");

        String body = mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // The list is built from a projection with no accessor for the text column, so
                // this cannot regress by accident — but it is the assertion somebody will look
                // for when they wonder whether a list response can leak a resume.
                .andExpect(jsonPath("$[0].textPreview").doesNotExist())
                .andExpect(jsonPath("$[1].textPreview").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Order is not asserted: both rows are inserted inside the same millisecond, so
        // "newest first" has nothing to sort by here and either order is correct.
        assertThat(json.readTree(body).findValuesAsText("label"))
                .containsExactlyInAnyOrder("First", "Second");
    }

    @Test
    @DisplayName("fetching one resume shows an excerpt, so the owner can confirm what we read")
    void showsAPreviewOfOneResume() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        String id = idOf(uploadPdf(token, "priya-cv.pdf", "Backend CV"));

        String body = mockMvc.perform(get("/api/resumes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andReturn().getResponse().getContentAsString();

        String preview = json.readTree(body).get("textPreview").asText();
        // An excerpt, not the document: the full text never leaves the server. The one extra
        // character allowed for is the ellipsis the preview adds when it had to cut.
        assertThat(preview)
                .contains("PRIYA SHARMA")
                .hasSizeLessThanOrEqualTo(ResumeResponse.PREVIEW_LENGTH + 1);
    }

    @Test
    @DisplayName("delete removes the record and the file behind it")
    void deletesAResume() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        String id = idOf(uploadPdf(token, "priya-cv.pdf", "Backend CV"));
        String storageKey = storageKeyOf(id);

        assertThat(storage.exists(storageKey)).isTrue();

        mockMvc.perform(delete("/api/resumes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/resumes/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        // "Delete uploaded files when requested" is in the spec's privacy list. A row removed
        // while the bytes stay on disk would satisfy the API and not the promise.
        assertThat(storage.exists(storageKey)).isFalse();
    }

    @Test
    @DisplayName("one account cannot see another account's resumes")
    void keepsResumesPrivateBetweenAccounts() throws Exception {
        String priya = signUpAndSignIn("priya@example.test");
        String stranger = signUpAndSignIn("stranger@example.test");
        String id = idOf(uploadPdf(priya, "priya-cv.pdf", "Backend CV"));

        // 404 rather than 403: "that exists, but not for you" is itself a fact about somebody
        // else's account, and an id oracle is how enumeration starts.
        mockMvc.perform(get("/api/resumes/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/resumes").header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("one account cannot delete another account's resume")
    void willNotDeleteSomebodyElsesResume() throws Exception {
        String priya = signUpAndSignIn("priya@example.test");
        String stranger = signUpAndSignIn("stranger@example.test");
        String id = idOf(uploadPdf(priya, "priya-cv.pdf", "Backend CV"));
        String storageKey = storageKeyOf(id);

        mockMvc.perform(delete("/api/resumes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound());

        // The row and the file both survive. A delete that answered 404 and removed the file
        // anyway would be the worst possible outcome, and is exactly what a missing owner clause
        // in the delete statement would produce.
        assertThat(storage.exists(storageKey)).isTrue();
        mockMvc.perform(get("/api/resumes/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + priya))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the per-account limit is enforced, and deleting one makes room")
    void enforcesTheQuota() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        String firstId = idOf(uploadPdf(token, "one.pdf", "One"));
        uploadPdf(token, "two.pdf", "Two");
        uploadPdf(token, "three.pdf", "Three");

        upload(token, "four.pdf", MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines()), "Four")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("Delete one")));

        mockMvc.perform(delete("/api/resumes/" + firstId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // A quota, not a judgement: the message says how to proceed and the endpoint honours it.
        uploadPdf(token, "four.pdf", "Four");
    }

    @Test
    @DisplayName("a label longer than the column is refused by validation, not truncated silently")
    void refusesAnOverlongLabel() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        upload(token, "priya-cv.pdf", MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines()), "L".repeat(141))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a filename carrying a local path is stored as a name, not as a path")
    void doesNotKeepTheUploadersPath() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        // Internet Explorer and some mobile browsers send the full local path. Keeping it would
        // put the person's name and operating system in every screenshot of their resume list.
        String id = idOf(upload(token, "C:\\Users\\priya\\Desktop\\cv.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines()), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("cv.pdf"))
                .andExpect(jsonPath("$.label").value("cv")));

        // And the name on disk is ours regardless: a date shard, a UUID, the real extension.
        assertThat(storageKeyOf(id))
                .matches("\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf")
                .doesNotContain("cv")
                .doesNotContain("priya");
    }

    @Test
    @DisplayName("every resume endpoint is closed to anonymous callers")
    void requiresAuthentication() throws Exception {
        String someId = "11111111-1111-1111-1111-111111111111";

        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "resume.pdf",
                                MediaType.APPLICATION_PDF_VALUE, DocumentFixtures.pdf("x"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resumes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resumes/" + someId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/resumes/" + someId)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions upload(String token, String filename, String contentType,
                                 byte[] content, String label) throws Exception {
        // Declared as the base builder type deliberately: param() is declared there and
        // returns it, so a multipart-typed local would not compile. The object underneath is
        // still the multipart builder, which is what perform() looks at.
        MockHttpServletRequestBuilder request = multipart("/api/resumes/upload")
                .file(new MockMultipartFile("file", filename, contentType, content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (label != null) {
            request = request.param("label", label);
        }
        return mockMvc.perform(request);
    }

    private ResultActions uploadPdf(String token, String filename, String label) throws Exception {
        return upload(token, filename, MediaType.APPLICATION_PDF_VALUE,
                DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines()), label)
                .andExpect(status().isCreated());
    }

    private String signUpAndSignIn(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", "Priya Sharma"))))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String idOf(ResultActions actions) throws Exception {
        JsonNode body = json.readTree(actions.andReturn().getResponse().getContentAsString());
        return body.get("id").asText();
    }

    /**
     * The storage key, read straight from the database.
     *
     * <p>It has to come from here: the key is deliberately absent from every response, so a
     * test that wanted to assert about the file on disk has nowhere else to get it. That is
     * itself worth noticing — if this helper could be written against the API, the API would be
     * telling clients the names of files on the server.
     */
    private String storageKeyOf(String publicId) {
        return jdbc.queryForObject(
                "select storage_key from resumes where public_id = ?", String.class, publicId);
    }
}
