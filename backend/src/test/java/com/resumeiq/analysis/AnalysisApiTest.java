package com.resumeiq.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.support.AnalysisFixtures;
import com.resumeiq.support.AnalysisIntegrationTest;
import com.resumeiq.support.ApiClient;
import com.resumeiq.support.DatabaseCleaner;
import com.resumeiq.support.DocumentFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The analysis, dashboard and recommendation endpoints over HTTP.
 *
 * <p>Every fixture here is built through the API it will be analysed by: an account is registered, a
 * real PDF is uploaded and extracted, a real posting is pasted and parsed, and only then is an analysis
 * run against them. That is slower than inserting rows and it is the only version worth having — what
 * is being tested is whether seven components agree, and inserting rows would let the test agree with
 * itself instead. It also means the skill matches come from the shipped catalogue rather than from a
 * hand-written index, so "we found Java in your resume and Docker missing from it" is checked as the
 * promise the product makes rather than as the matcher's arithmetic.
 *
 * <p>The assertions worth reading are the ones about what the endpoints refuse to do.
 * {@link #refusesToAnalyseAResumeItCouldNotRead()} checks that a scanned resume gets a 422 and an
 * explanation rather than a confident low score, because a score computed from no text measures the
 * extraction and reads as a verdict on the CV. {@link #willNotAnalyseSomebodyElsesResume()} and
 * {@link #keepsAnalysesPrivateBetweenAccounts()} check the spec's first named requirement. And
 * {@link #storesNoVerbatimModelResponse()} checks that a column stays empty, which is the only way to
 * assert a decision about what is deliberately not kept.
 *
 * <p>Note the absence of absolute score assertions. {@code overallScore >= 75} would be a test of this
 * week's weights, and it would fail for a change that made them better. What is asserted instead is the
 * property that has to hold for any weighting worth shipping — see
 * {@link #scoresAStrongResumeAboveAThinOne()}.
 */
@AnalysisIntegrationTest
class AnalysisApiTest {

    private static final String PRIYA = "priya@example.test";
    private static final String STRANGER = "stranger@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;

    @BeforeEach
    void clearState() {
        api = new ApiClient(mockMvc, json);
        DatabaseCleaner.clear(jdbc);
    }

    @AfterAll
    static void removeStoredFiles() throws IOException {
        Path root = Paths.get(AnalysisIntegrationTest.STORAGE_DIR);
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
    @DisplayName("an analysis returns every output the spec lists")
    void producesEveryOutputTheSpecLists() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        String resumeId = uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME);
        String postingId = savePosting(token);

        api.analyse(token, resumeId, postingId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.target.resumeId").value(resumeId))
                .andExpect(jsonPath("$.target.resumeLabel").value("Backend CV"))
                .andExpect(jsonPath("$.target.jobDescriptionId").value(postingId))
                .andExpect(jsonPath("$.target.jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.target.company").value("Northwind"))

                // Six scores, all of them numbers the engine computed.
                .andExpect(jsonPath("$.overallScore").exists())
                .andExpect(jsonPath("$.atsScore").exists())
                .andExpect(jsonPath("$.jobMatchScore").exists())
                .andExpect(jsonPath("$.skillsMatchScore").exists())
                .andExpect(jsonPath("$.keywordScore").exists())
                .andExpect(jsonPath("$.experienceScore").exists())
                .andExpect(jsonPath("$.scoreBreakdown.length()", greaterThan(0)))

                // The findings. These slugs come from the shipped catalogue, not from a fixture this
                // test inserted, which is the difference between testing the matcher and testing the
                // claim on the landing page.
                .andExpect(jsonPath("$.detectedSkills[*].slug",
                        hasItems("java", "spring-boot", "mysql")))
                .andExpect(jsonPath("$.detectedSkills[*].status", everyItem(not(is("MISSING")))))
                .andExpect(jsonPath("$.missingSkills[*].slug", hasItems("docker", "kubernetes")))
                .andExpect(jsonPath("$.missingSkills[*].status", everyItem(is("MISSING"))))
                .andExpect(jsonPath("$.matchingKeywords.length()", greaterThan(0)))
                .andExpect(jsonPath("$.sectionScores.length()")
                        .value(ResumeSection.values().length))

                // The advice.
                .andExpect(jsonPath("$.overallFeedback").isNotEmpty())
                .andExpect(jsonPath("$.improvements.length()", greaterThan(0)))
                .andExpect(jsonPath("$.recommendedProjects.length()", greaterThan(0)))
                .andExpect(jsonPath("$.learningRecommendations.length()", greaterThan(0)))
                .andExpect(jsonPath("$.suggestedKeywords.length()", greaterThan(0)))

                // Provenance. No key ran in this suite, so the offline writer produced the prose and
                // the response says so rather than passing derived advice off as a model's reading of
                // somebody's resume.
                .andExpect(jsonPath("$.provenance.modelWritten").value(false))
                .andExpect(jsonPath("$.provenance.writtenBy").isNotEmpty())
                .andExpect(jsonPath("$.provenance.analyzerVersion").isNotEmpty())
                .andExpect(jsonPath("$.provenance.processingMs").exists())

                // Omitted rather than null: the mapper leaves it out for a completed run, so a client
                // testing `if (response.failureReason)` is right either way.
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    @DisplayName("every score is a percentage and every suggested keyword says where it belongs")
    void keepsTheNumbersInRangeAndTheAdviceHonest() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        JsonNode analysis = api.analysisOf(token,
                uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME), savePosting(token));

        for (String field : List.of("overallScore", "atsScore", "jobMatchScore",
                "skillsMatchScore", "keywordScore", "experienceScore")) {
            assertThat(analysis.get(field).asInt())
                    .as("%s is a percentage", field)
                    .isBetween(0, 100);
        }

        // The one assertion standing between this product and a keyword stuffing tool: a term with no
        // honest place to go is dropped rather than suggested, so everything that reaches the response
        // carries the section it belongs in.
        assertThat(analysis.get("suggestedKeywords").size()).isPositive();
        for (JsonNode suggestion : analysis.get("suggestedKeywords")) {
            assertThat(suggestion.get("term").asText()).isNotBlank();
            assertThat(suggestion.get("placement").asText()).isNotBlank();
        }

        // A gap is a gap in both directions: nothing in the missing list may also be detected.
        assertThat(slugs(analysis, "missingSkills"))
                .isNotEmpty()
                .doesNotContainAnyElementsOf(slugs(analysis, "detectedSkills"));

        // Every skill the resume demonstrates carries its evidence, which is what keeps the advice
        // checkable rather than asserted: "strengthen your Spring Boot bullet" is only legitimate
        // advice if there is a Spring Boot bullet, and the note is where it was found.
        for (JsonNode skill : analysis.get("detectedSkills")) {
            assertThat(skill.get("note").asText())
                    .as("evidence for %s", skill.get("name").asText())
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("a strong resume outscores a thin one against the same posting")
    void scoresAStrongResumeAboveAThinOne() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        String postingId = savePosting(token);
        JsonNode strong = api.analysisOf(token,
                uploadResume(token, "Strong", AnalysisFixtures.STRONG_RESUME), postingId);
        JsonNode thin = api.analysisOf(token,
                uploadResume(token, "Thin", AnalysisFixtures.THIN_RESUME), postingId);

        // Relative, not absolute. "The strong resume scores at least 75" is an assertion about this
        // week's weights and would fail for a change that improved them. This one holds for any
        // weighting worth shipping, and it fails for the bug that actually matters: a scorer that has
        // stopped discriminating and returns much the same number for everything.
        assertThat(strong.get("overallScore").asInt())
                .isGreaterThan(thin.get("overallScore").asInt());
        assertThat(strong.get("atsScore").asInt()).isGreaterThan(thin.get("atsScore").asInt());
        assertThat(strong.get("missingSkills").size())
                .isLessThan(thin.get("missingSkills").size());
    }

    @Test
    @DisplayName("creating an analysis and fetching it return the same document")
    void theCreateAndReadResponsesAreTheSameDocument() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        JsonNode created = api.analysisOf(token,
                uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME), savePosting(token));

        JsonNode fetched = api.getJson(token, "/api/analyses/" + created.get("id").asText());

        // Character for character, because both are one mapper reading one row. This is what makes the
        // create response trustworthy: it is not a second rendering of the outcome that produced it,
        // it is a read of what was stored — so a mapping bug appears in both endpoints instead of
        // hiding in whichever one has fewer tests. It is also what catches the subtler version:
        // collections that come back from the database in a different order than they went in.
        assertThat(fetched.toString()).isEqualTo(created.toString());
    }

    @Test
    @DisplayName("a scanned resume is refused with an explanation, not scored")
    void refusesToAnalyseAResumeItCouldNotRead() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        String postingId = savePosting(token);

        // A blank page uploads successfully — it is a valid PDF — and extracts nothing.
        String scanned = api.idOf(api.uploadPdf(token, "Scan", DocumentFixtures.pdfWithNoText())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysable").value(false)));

        api.analyse(token, scanned, postingId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNREADABLE_FILE"))
                .andExpect(jsonPath("$.message", containsString("nothing to score")))
                .andExpect(jsonPath("$.message", containsString("text-based PDF")));

        // And nothing was written. A refused request is not a failed analysis: a row here would put a
        // point on the history chart for a resume that was never read, and 422 is the status that says
        // "the request was fine, the document was not".
        assertThat(countOf("analyses")).isZero();
    }

    @Test
    @DisplayName("a request with no ids is a field error, not a null pointer four layers down")
    void validatesTheRequest() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");

        mockMvc.perform(post("/api/analyses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItems("resumeId", "jobDescriptionId")));
    }

    @Test
    @DisplayName("a resume that belongs to somebody else cannot be analysed")
    void willNotAnalyseSomebodyElsesResume() throws Exception {
        String priya = api.signUp(PRIYA, "Priya Raman");
        String stranger = api.signUp(STRANGER, "Someone Else");
        String priyasResume = uploadResume(priya, "Backend CV", AnalysisFixtures.STRONG_RESUME);
        String strangersPosting = savePosting(stranger);

        // Both ids exist and both are valid somewhere. Neither pairing belongs to the caller, and the
        // lookup is ownership-scoped, so the answer is 404 in both directions rather than an analysis
        // of a stranger's CV — or worse, a stranger's CV scored and filed under this account.
        api.analyse(stranger, priyasResume, strangersPosting)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        api.analyse(priya, priyasResume, strangersPosting)
                .andExpect(status().isNotFound());

        assertThat(countOf("analyses")).isZero();
    }

    @Test
    @DisplayName("one account cannot read or list another account's analyses")
    void keepsAnalysesPrivateBetweenAccounts() throws Exception {
        String priya = api.signUp(PRIYA, "Priya Raman");
        String stranger = api.signUp(STRANGER, "Someone Else");
        String id = api.analysisOf(priya,
                        uploadResume(priya, "Backend CV", AnalysisFixtures.STRONG_RESUME),
                        savePosting(priya))
                .get("id").asText();

        // 404 rather than 403: "that exists, but not for you" is itself a fact about another account,
        // and an id oracle is where enumeration starts.
        mockMvc.perform(get("/api/analyses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/analyses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // The recommendations feed reads through the same ownership join, and it is the endpoint where
        // a missing filter would be least visible: these rows have no user column of their own, so the
        // only thing between them and the wrong reader is a join two levels deep.
        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // And the dashboard, which counts rather than lists. A count is a leak too.
        JsonNode dashboard = api.getJson(stranger, "/api/dashboard");
        assertThat(dashboard.get("counts").get("analyses").asInt()).isZero();
    }

    @Test
    @DisplayName("one account cannot delete another account's analysis")
    void willNotDeleteSomebodyElsesAnalysis() throws Exception {
        String priya = api.signUp(PRIYA, "Priya Raman");
        String stranger = api.signUp(STRANGER, "Someone Else");
        String id = api.analysisOf(priya,
                        uploadResume(priya, "Backend CV", AnalysisFixtures.STRONG_RESUME),
                        savePosting(priya))
                .get("id").asText();

        mockMvc.perform(delete("/api/analyses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound());

        // The row survives. A delete that answers 404 and removes it anyway is the worst outcome
        // available here, and it is exactly what a missing owner clause in the delete statement would
        // produce — which is why the owner is in the statement as well as in the lookup.
        mockMvc.perform(get("/api/analyses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + priya))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deleting an analysis takes its advice with it and leaves the documents alone")
    void deletesAnAnalysisAndItsChildren() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        String resumeId = uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME);
        String postingId = savePosting(token);
        String id = api.analysisOf(token, resumeId, postingId).get("id").asText();

        assertThat(countOf("recommendations")).isPositive();
        assertThat(countOf("analysis_skills")).isPositive();

        mockMvc.perform(delete("/api/analyses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/analyses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // The cascade is the point. Skills, keywords, section scores and recommendations have no life
        // outside their analysis, and an orphaned recommendation would keep turning up on the feed
        // pointing at an analysis that no longer exists.
        assertThat(countOf("analyses")).isZero();
        assertThat(countOf("recommendations")).isZero();
        assertThat(countOf("analysis_skills")).isZero();
        assertThat(countOf("analysis_keywords")).isZero();
        assertThat(countOf("analysis_section_scores")).isZero();
        assertThat(countOf("analysis_score_notes")).isZero();

        // The resume and the posting are untouched, so the same pair can be analysed again — which is
        // the normal way somebody checks whether an edit helped.
        mockMvc.perform(get("/api/resumes/" + resumeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        api.analyse(token, resumeId, postingId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the same pair can be analysed twice, and the list stays scores and labels")
    void allowsRepeatedAnalysesAndListsThemBriefly() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        String resumeId = uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME);
        String postingId = savePosting(token);

        String first = api.analysisOf(token, resumeId, postingId).get("id").asText();
        String second = api.analysisOf(token, resumeId, postingId).get("id").asText();

        // Not idempotent, on purpose. Re-running the same pair is how a user checks whether an edit
        // helped, so each call is a row and the history is the feature rather than a duplicate.
        assertThat(second).isNotEqualTo(first);

        mockMvc.perform(get("/api/analyses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].overallScore").exists())
                .andExpect(jsonPath("$[0].jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$[0].resumeLabel").value("Backend CV"))
                // A list row is scores and labels. Skills, keywords and advice come with a single
                // analysis; here they would be several child collections fetched per row to render a
                // table of numbers. The projection has no accessor for them at all, so this is
                // enforced by the query rather than by remembering to filter on the way out.
                .andExpect(jsonPath("$[0].detectedSkills").doesNotExist())
                .andExpect(jsonPath("$[0].improvements").doesNotExist())
                .andExpect(jsonPath("$[0].overallFeedback").doesNotExist());
    }

    @Test
    @DisplayName("no verbatim model response is kept, by design")
    void storesNoVerbatimModelResponse() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        api.analysisOf(token, uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME),
                savePosting(token));

        // The column exists for diagnosing a provider and is deliberately never written on a
        // successful run. Filling it would keep a second verbatim copy of generated text about
        // somebody's resume that nothing reads: the parts worth keeping are already rows, and the
        // parts that failed validation are the parts we decided not to stand behind.
        Integer stored = jdbc.queryForObject(
                "select count(*) from analyses where raw_response is not null", Integer.class);
        assertThat(stored).isZero();
    }

    @Test
    @DisplayName("the dashboard reports counts, a trend and the gaps that keep recurring")
    void buildsTheDashboard() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        String resumeId = uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME);
        String postingId = savePosting(token);
        api.analysisOf(token, resumeId, postingId);
        api.analysisOf(token, resumeId, postingId);

        JsonNode dashboard = api.getJson(token, "/api/dashboard");

        assertThat(dashboard.get("counts").get("analyses").asInt()).isEqualTo(2);
        assertThat(dashboard.get("counts").get("resumes").asInt()).isEqualTo(1);
        assertThat(dashboard.get("counts").get("jobDescriptions").asInt()).isEqualTo(1);

        assertThat(dashboard.get("scores").get("average").asInt()).isBetween(0, 100);
        assertThat(dashboard.get("scores").get("best").asInt()).isBetween(0, 100);
        assertThat(dashboard.get("scores").get("latest").asInt()).isBetween(0, 100);
        assertThat(dashboard.get("scoreHistory").size()).isEqualTo(2);
        assertThat(dashboard.get("recentAnalyses").size()).isEqualTo(2);

        for (JsonNode point : dashboard.get("scoreHistory")) {
            assertThat(point.get("recordedAt").asText()).isNotBlank();
            assertThat(point.get("overall").asInt()).isBetween(0, 100);
        }

        // The most useful thing on the screen and the one thing no single analysis can tell you: two
        // analyses both missing Docker is a decision about what to learn next. It is a group-by over
        // one table, which is the whole argument for a relational store here.
        assertThat(dashboard.get("topSkillGaps").size()).isPositive();
        boolean recurring = false;
        for (JsonNode gap : dashboard.get("topSkillGaps")) {
            assertThat(gap.get("skill").asText()).isNotBlank();
            recurring = recurring || gap.get("occurrences").asInt() == 2;
        }
        assertThat(recurring).as("a gap seen in both analyses is counted twice").isTrue();
    }

    @Test
    @DisplayName("a new account gets an empty dashboard rather than an error")
    void buildsAnEmptyDashboard() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");

        JsonNode dashboard = api.getJson(token, "/api/dashboard");

        assertThat(dashboard.get("counts").get("analyses").asInt()).isZero();
        assertThat(dashboard.get("scoreHistory").size()).isZero();
        assertThat(dashboard.get("recentAnalyses").size()).isZero();
        assertThat(dashboard.get("topSkillGaps").size()).isZero();

        // Absent, not zero. SQL's avg() over no rows is null, the API passes that null through and the
        // mapper omits it — so a client can tell "nothing scored yet" from "scored zero", which need
        // different screens. Defaulting it would greet a new account with a chart reporting an overall
        // score of zero, which reads as a judgement of their resume.
        assertThat(dashboard.get("scores").get("average")).isNull();
        assertThat(dashboard.get("scores").get("best")).isNull();
        assertThat(dashboard.get("scores").get("latest")).isNull();
    }

    @Test
    @DisplayName("the recommendations feed carries the job each suggestion came from")
    void buildsTheRecommendationsFeed() throws Exception {
        String token = api.signUp(PRIYA, "Priya Raman");
        api.analysisOf(token, uploadResume(token, "Backend CV", AnalysisFixtures.STRONG_RESUME),
                savePosting(token));

        JsonNode feed = api.getJson(token, "/api/recommendations");

        assertThat(feed.size()).isPositive();
        for (JsonNode item : feed) {
            assertThat(item.get("title").asText()).isNotBlank();
            assertThat(item.get("analysisId").asText()).isNotBlank();
            // The context is the whole reason this endpoint is a joined projection rather than a list
            // of recommendation rows: "learn Docker" means something different under one job title
            // than under another, and a feed that pooled advice from six applications without saying
            // which was which would be advice about nothing in particular.
            assertThat(item.get("jobTitle").asText()).isEqualTo(AnalysisFixtures.ROLE);
        }

        mockMvc.perform(get("/api/recommendations")
                        .param("type", "LEARNING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[*].type", everyItem(is("LEARNING"))));

        // A type outside the four is a 400 rather than an empty list, because an empty list would let
        // a client's typo look like "you have no learning topics".
        mockMvc.perform(get("/api/recommendations")
                        .param("type", "NONSENSE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("every endpoint added in this phase is closed to anonymous callers")
    void requiresAuthentication() throws Exception {
        String someId = "11111111-1111-1111-1111-111111111111";

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "resumeId", someId, "jobDescriptionId", someId))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analyses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analyses/" + someId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/analyses/" + someId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recommendations")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    /** Uploads one of the resume fixtures as a real PDF and returns its public id. */
    private String uploadResume(String token, String label, String text) throws Exception {
        return api.uploadResume(token, label, text.lines().toArray(String[]::new));
    }

    private String savePosting(String token) throws Exception {
        return api.savePosting(token, AnalysisFixtures.ROLE, "Northwind", AnalysisFixtures.POSTING);
    }

    /**
     * The slugs of one findings list.
     *
     * <p>A skill the catalogue has never heard of has no slug, so {@code findValuesAsText} leaves it
     * out — which is right for the comparison this feeds, since an unresolved mention cannot be in
     * two lists at once and has no slug to collide on.
     */
    private static List<String> slugs(JsonNode analysis, String field) {
        return analysis.get(field).findValuesAsText("slug");
    }

    private int countOf(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
