package com.resumeiq.jobdescription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The four job-description endpoints over HTTP, against the real skill catalogue.
 *
 * <p>This is the only test in the suite that runs with {@code resumeiq.seed.skills=true}. Everywhere
 * else the catalogue is a handful of rows built by hand, which is right for testing the matcher —
 * but the promise this feature makes to a user is "paste a posting and see what it asks for", and
 * that promise is kept by 129 catalogue entries and a parser agreeing with each other. A posting
 * whose Java is found because the test inserted Java proves less than one whose Java is found
 * because the shipped catalogue has it.
 *
 * <p>The assertions worth reading are the ones about what the response refuses to say.
 * {@link #keepsThePerksOutOfTheAdvice()} checks that a React course in the benefits section is
 * reported as mentioned rather than required, and that nothing from that section reaches the
 * keyword list; {@link #admitsWhenThePostingHadNoHeadings()} checks that a posting read as
 * requirements-by-default still reports {@code structured: false} and an empty
 * {@code sectionsFound}, so the UI cannot claim a heading the user would scroll up and fail to
 * find. Both are places where a confident, wrong answer would be easy to ship and hard to notice.
 *
 * <p>Property overrides, and why. A database of its own because these tests commit — MockMvc calls
 * are not wrapped in a test transaction, and rows left behind would turn up inside repository
 * slices that never asked for them. That same isolation is what makes seeding safe here. A small
 * {@code max-per-user} so the quota is reachable in three requests. And a storage directory under
 * {@code build/}: nothing in this class uploads anything, but the application creates the directory
 * on startup and it should not appear in the working tree.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:resumeiq-postings;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "resumeiq.seed.skills=true",
        "resumeiq.auth.bcrypt-strength=4",
        "resumeiq.upload.storage-dir=./build/test-uploads/job-description-api",
        "resumeiq.posting.max-per-user=2",
})
class JobDescriptionApiTest {

    private static final String PASSWORD = "example-passphrase-9";

    /**
     * A posting written the way postings are written: a company blurb, the day-to-day work, the
     * hard requirements, the nice-to-haves, and the perks. Every body line ends in a full stop,
     * which is not decoration — it is what stops the section splitter from reading a body line as
     * a heading, and it is how a real posting reads anyway.
     */
    private static final String POSTING = """
            About us
            Northwind moves freight for small carriers across Europe, and our platform is the
            reason they can compete with the national fleets.

            Responsibilities
            Build and operate REST services with Spring Boot.
            Work with product to shape what we ship next.

            Requirements:
            5+ years of professional experience with Java.
            Solid MySQL, including a working grasp of query plans.
            Comfortable owning a service from the first commit through production support.

            Nice to have
            Docker, and any exposure to Kubernetes.

            Benefits
            A conference budget, and a React course of your choosing.
            """;

    /** The same job, pasted out of an email that lost the formatting. */
    private static final String UNSTRUCTURED_POSTING = """
            We are hiring a backend engineer to work on our logistics platform. The person we
            hire will write Java and Spring Boot services against MySQL, will own what they
            ship, and will help us move the last of our batch jobs into Docker. We work in
            small teams and ship most days.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearState() {
        // Emptied in foreign-key order by a shared helper: the list of tables a class has to clear is
        // not the list of tables it writes to, because Spring caches one context across several
        // classes and their rows outlive them.
        DatabaseCleaner.clear(jdbc);
    }

    @Test
    @DisplayName("a pasted posting comes back with the skills, keywords and seniority read out of it")
    void savesAPostingAndReturnsWhatItReadOutOfIt() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        create(token, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.company").value("Northwind"))
                // The posting itself is returned. Unlike a resume this is plain text the user typed
                // in, and handing it back is the feature: an analysis from six weeks ago is worth
                // little if you cannot see what it was measured against.
                .andExpect(jsonPath("$.text", containsString("Requirements:")))
                .andExpect(jsonPath("$.insight.structured").value(true))
                .andExpect(jsonPath("$.insight.wordCount", greaterThan(60)))
                .andExpect(jsonPath("$.insight.sectionsFound",
                        hasItems("PREFERRED", "BENEFITS", "COMPANY", "REQUIREMENTS",
                                "RESPONSIBILITIES")))

                // Four required skills, found by the shipped catalogue rather than by a fixture.
                .andExpect(jsonPath("$.insight.requiredSkills[*].slug",
                        hasItems("java", "mysql", "spring-boot")))
                // Ordering is fully determined — required first, then mentions, then name — so the
                // first entry can be asserted in full, including the heading it was found under.
                .andExpect(jsonPath("$.insight.requiredSkills[0].name").value("Java"))
                .andExpect(jsonPath("$.insight.requiredSkills[0].category").value("LANGUAGE"))
                .andExpect(jsonPath("$.insight.requiredSkills[0].mentions").value(1))
                .andExpect(jsonPath("$.insight.requiredSkills[0].foundUnder").value("Requirements"))
                // Named only in the day-to-day work, and still required: for plenty of postings that
                // section is the only place the stack is ever written down.
                .andExpect(jsonPath("$.insight.requiredSkills[?(@.slug=='spring-boot')].foundUnder",
                        contains("Responsibilities")))
                .andExpect(jsonPath("$.insight.preferredSkills[*].slug",
                        hasItems("docker", "kubernetes")))

                // Five years reads as MID; the title is what makes this senior, so the title is what
                // gets quoted back beside the badge.
                .andExpect(jsonPath("$.insight.experience.minYears").value(5))
                .andExpect(jsonPath("$.insight.experience.maxYears").doesNotExist())
                .andExpect(jsonPath("$.insight.experience.level").value("SENIOR"))
                .andExpect(jsonPath("$.insight.experience.evidence").value("senior"))

                // A keyword score only means anything relative to the other keywords of the same
                // posting, and a number on screen invites being read as a percentage. The array
                // order carries the ranking; the number stays on the server.
                .andExpect(jsonPath("$.insight.keywords[0].score").doesNotExist());
    }

    @Test
    @DisplayName("nothing from the perks section is presented as something the job asks for")
    void keepsThePerksOutOfTheAdvice() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        String body = create(token, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated())
                // React appears once, in a sentence about a training budget. Reporting it as a
                // requirement would be a confident lie about the job, and it is the single easiest
                // mistake for a keyword counter to make.
                .andExpect(jsonPath("$.insight.requiredSkills[*].slug", not(hasItem("react"))))
                .andExpect(jsonPath("$.insight.preferredSkills[*].slug", not(hasItem("react"))))
                .andExpect(jsonPath("$.insight.mentionedSkills[0].slug").value("react"))
                .andExpect(jsonPath("$.insight.mentionedSkills[0].foundUnder").value("Benefits"))
                .andReturn().getResponse().getContentAsString();

        List<String> keywords = json.readTree(body).at("/insight/keywords")
                .findValuesAsText("term");
        assertThat(keywords)
                .isNotEmpty()
                // "Add a conference budget to your resume" is not advice. The benefits section
                // carries no keyword weight at all, so none of its words can rank.
                .doesNotContain("conference", "budget", "course")
                // And a skill already reported is not repeated as a keyword, nor are the words
                // inside its name — otherwise the response says "Spring Boot is required" and, a
                // field later, "consider adding the keyword boot".
                .doesNotContain("Java", "MySQL", "Docker", "Spring", "Boot");
    }

    @Test
    @DisplayName("a posting with no headings is read as requirements, and says it had no headings")
    void admitsWhenThePostingHadNoHeadings() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        create(token, "Backend Engineer", null, UNSTRUCTURED_POSTING)
                .andExpect(status().isCreated())
                // Somebody who pastes a wall of text still means "this is what the job needs", so
                // the whole posting is read as requirements.
                .andExpect(jsonPath("$.insight.requiredSkills[*].slug",
                        hasItems("java", "mysql", "spring-boot", "docker")))
                // And the default does not invent evidence for itself. These two fields are how the
                // UI knows to say "read as requirements" rather than "the posting requires".
                .andExpect(jsonPath("$.insight.structured").value(false))
                .andExpect(jsonPath("$.insight.sectionsFound.length()").value(0))
                .andExpect(jsonPath("$.insight.requiredSkills[0].foundUnder").doesNotExist())
                // No company was sent, so the key is absent rather than an empty chip on the page.
                .andExpect(jsonPath("$.company").doesNotExist());
    }

    @Test
    @DisplayName("re-pasting a saved posting returns it with 200 instead of failing with 409")
    void rePastingTheSamePostingReturnsTheSavedOne() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        JsonNode first = bodyOf(create(token, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated()));

        // The core loop of this product is one posting and several versions of a resume. People
        // paste the same description again on Tuesday because they rewrote their bullet points on
        // Monday, and a 409 there would be defensible and infuriating.
        JsonNode again = bodyOf(create(token, "A different title entirely", null, POSTING)
                .andExpect(status().isOk()));

        assertThat(again.get("id").asText()).isEqualTo(first.get("id").asText());
        // The original title and date stand: it is the same posting, and refreshing the date would
        // reorder the user's list for no reason they could see.
        assertThat(again.get("title").asText()).isEqualTo("Senior Backend Engineer");
        // Byte-identical, not merely equal-ish. The first body serialises the entity that was just
        // saved and the second serialises the row read back, so this comparison is what proves the
        // two agree. It failed on Windows and passed on Linux until Timestamps.now() truncated the
        // clock: TIMESTAMP(6) keeps microseconds, and the Windows clock ticks finer than that.
        assertThat(again.get("createdAt").asText()).isEqualTo(first.get("createdAt").asText());
        Instant created = Instant.parse(first.get("createdAt").asText());
        assertThat(created)
                .as("a createdAt the database cannot store is a createdAt the API will contradict")
                .isEqualTo(created.truncatedTo(ChronoUnit.MICROS));

        mockMvc.perform(get("/api/job-descriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("text too short to score against is refused, with the reason and the number")
    void refusesTextTooShortToScoreAgainst() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        // Scoring a resume against three lines produces a confident number that means nothing, and
        // a confident number that means nothing is the failure mode this whole product has to avoid.
        create(token, "Backend Engineer", null, "Backend engineer wanted. Java and MySQL.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("Paste the whole posting")))
                .andExpect(jsonPath("$.message", containsString("200")))
                .andExpect(jsonPath("$.path").value("/api/job-descriptions"));
    }

    @Test
    @DisplayName("a long posting is cut at the end, where the boilerplate is, rather than refused")
    void truncatesALongPostingRatherThanRefusingIt() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        String padded = POSTING + "We also think culture matters more than perks do.\n".repeat(500);

        assertThat(padded.length()).isGreaterThan(20_000);

        JsonNode body = bodyOf(create(token, "Senior Backend Engineer", "Northwind", padded)
                // Not a 400. Refusing this would mean telling somebody to edit a job description
                // before we would look at it.
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.insight.requiredSkills[*].slug",
                        hasItems("java", "mysql", "spring-boot"))));

        // Postings run long at the end — perks, the EEO statement, how to apply — so cutting there
        // keeps every requirement. This assertion is the reason the padding goes last.
        assertThat(body.get("text").asText())
                .hasSizeLessThanOrEqualTo(20_000)
                .contains("5+ years of professional experience with Java.");
    }

    @Test
    @DisplayName("a missing title is a field error, not a posting saved with no name")
    void validatesTheTitle() throws Exception {
        String token = signUpAndSignIn("priya@example.test");

        // The title is how somebody recognises this posting in a list six weeks later, which is why
        // it is required at all.
        create(token, "   ", null, POSTING)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("title")));
    }

    @Test
    @DisplayName("the list is metadata only — no posting text and no parse")
    void listsPostingsWithoutTheirTextOrParse() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        create(token, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated());
        create(token, "Backend Engineer", null, UNSTRUCTURED_POSTING)
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/job-descriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // The list is built from a projection with no accessor for the text column, so this
                // cannot regress by accident — but it is also the difference between one parse and
                // fifty parses to render a screen that displays none of their output.
                .andExpect(jsonPath("$[0].text").doesNotExist())
                .andExpect(jsonPath("$[0].insight").doesNotExist())
                .andExpect(jsonPath("$[1].text").doesNotExist())
                .andExpect(jsonPath("$[1].insight").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Order is not asserted: both rows are written inside the same millisecond, so "newest
        // first" has nothing to sort by here and either order is correct.
        assertThat(json.readTree(body).findValuesAsText("title"))
                .containsExactlyInAnyOrder("Senior Backend Engineer", "Backend Engineer");
    }

    @Test
    @DisplayName("one account cannot see another account's postings")
    void keepsPostingsPrivateBetweenAccounts() throws Exception {
        String priya = signUpAndSignIn("priya@example.test");
        String stranger = signUpAndSignIn("stranger@example.test");
        String id = idOf(create(priya, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated()));

        // 404 rather than 403: "that exists, but not for you" is itself a fact about somebody else's
        // account, and an id oracle is how enumeration starts.
        mockMvc.perform(get("/api/job-descriptions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/job-descriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("one account cannot delete another account's posting")
    void willNotDeleteSomebodyElsesPosting() throws Exception {
        String priya = signUpAndSignIn("priya@example.test");
        String stranger = signUpAndSignIn("stranger@example.test");
        String id = idOf(create(priya, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/job-descriptions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound());

        // The row survives. A delete that answered 404 and removed it anyway would be the worst
        // outcome available, and is exactly what a missing owner clause in the delete statement
        // would produce — which is why the owner is in the statement as well as in the lookup.
        mockMvc.perform(get("/api/job-descriptions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + priya))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deleting a posting removes it")
    void deletesAPosting() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        String id = idOf(create(token, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/job-descriptions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/job-descriptions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the per-account limit stops new postings but never a re-paste")
    void enforcesTheQuotaWithoutBlockingARePaste() throws Exception {
        String token = signUpAndSignIn("priya@example.test");
        String firstId = idOf(create(token, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isCreated()));
        create(token, "Backend Engineer", null, UNSTRUCTURED_POSTING)
                .andExpect(status().isCreated());

        create(token, "Platform Engineer", null, POSTING + "We also run a monthly hack day.\n")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("Delete one")));

        // Reuse is checked before the quota, so somebody sitting at their limit can still re-paste
        // a posting they already have and get on with the analysis they came here for. Checking the
        // quota first would refuse a request that was never going to create a row.
        create(token, "Senior Backend Engineer", "Northwind", POSTING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        mockMvc.perform(delete("/api/job-descriptions/" + firstId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // A quota, not a judgement: the message said how to proceed and the endpoint honours it.
        create(token, "Platform Engineer", null, POSTING + "We also run a monthly hack day.\n")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("every job-description endpoint is closed to anonymous callers")
    void requiresAuthentication() throws Exception {
        String someId = "11111111-1111-1111-1111-111111111111";

        mockMvc.perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new CreateJobDescriptionRequest("Backend Engineer", null, POSTING))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/job-descriptions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/job-descriptions/" + someId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/job-descriptions/" + someId)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Posts a create request.
     *
     * <p>The body is serialised from the real request record rather than from a map of strings, so a
     * field renamed on the DTO breaks this class at compile time instead of producing a run of
     * puzzling 400s.
     */
    private ResultActions create(String token, String title, String company, String text)
            throws Exception {
        return mockMvc.perform(post("/api/job-descriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        new CreateJobDescriptionRequest(title, company, text))));
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

    private JsonNode bodyOf(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String idOf(ResultActions actions) throws Exception {
        return bodyOf(actions).get("id").asText();
    }
}
