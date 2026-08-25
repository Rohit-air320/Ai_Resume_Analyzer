package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.recommendation.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading a model's reply.
 *
 * <p>Most of these tests are about responses that are wrong in small ways, because that is what the real
 * ones are. A model asked for bare JSON returns it most of the time, and the rest of the time returns the
 * same JSON inside a markdown fence, or after a friendly preamble, or with a sentence added past the
 * closing brace, or with the keys spelled the way the findings spelled them rather than the way the
 * schema did. Every one of those is a response somebody's advice can be recovered from, and discarding
 * them would mean silently serving offline advice to a user whose model answered perfectly well.
 *
 * <p>The one deliberate refusal is {@link #aBareStringKeywordIsDropped()}. It is the only place in this
 * class where recovering something would be the wrong call.
 */
class AiAdviceReaderTest {

    private static final String MODEL = "test-model";

    @Test
    @DisplayName("a clean response reads into every field")
    void readsACleanResponse() {
        AiAdvice advice = read("""
                {
                  "overallScore": 71,
                  "atsScore": 84,
                  "jobMatchScore": 63,
                  "skillsMatchScore": 58,
                  "keywordScore": 66,
                  "experienceScore": 80,
                  "overallFeedback": "A strong backend resume that undersells its data work.",
                  "improvements": [
                    {"title": "Quantify the migration", "detail": "Say how many rows moved.",
                     "priority": "HIGH", "section": "EXPERIENCE"}
                  ],
                  "skillGaps": [
                    {"skill": "docker", "detail": "Named in the posting, absent here.",
                     "priority": "MEDIUM"}
                  ],
                  "recommendedProjects": [
                    {"title": "Containerise the reconciler", "detail": "Package it and publish it.",
                     "skills": ["docker", "kubernetes"]}
                  ],
                  "learningRecommendations": [
                    {"title": "Container fundamentals", "detail": "Images, layers, networking.",
                     "url": "https://docs.example.test/containers", "priority": "HIGH"}
                  ],
                  "suggestedKeywords": [
                    {"term": "container orchestration", "placement": "the Ledger project bullet"}
                  ],
                  "sectionScores": [
                    {"section": "SUMMARY", "note": "Names the role but not the evidence."}
                  ]
                }
                """);

        assertThat(advice.overallFeedback()).isEqualTo(
                "A strong backend resume that undersells its data work.");
        assertThat(advice.source()).isEqualTo(MODEL);
        assertThat(advice.improvements()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Quantify the migration");
            assertThat(item.priority()).isEqualTo(Priority.HIGH);
            assertThat(item.section()).isEqualTo(ResumeSection.EXPERIENCE);
        });
        assertThat(advice.skillGaps()).singleElement()
                .satisfies(gap -> assertThat(gap.slug()).isEqualTo("docker"));
        assertThat(advice.recommendedProjects()).singleElement()
                .satisfies(idea -> assertThat(idea.skillSlugs()).containsExactly("docker",
                        "kubernetes"));
        assertThat(advice.learningRecommendations()).singleElement()
                .satisfies(topic -> assertThat(topic.resourceUrl())
                        .isEqualTo("https://docs.example.test/containers"));
        assertThat(advice.suggestedKeywords()).singleElement()
                .satisfies(keyword -> assertThat(keyword.placement())
                        .isEqualTo("the Ledger project bullet"));
        assertThat(advice.sectionNotes()).singleElement()
                .satisfies(note -> assertThat(note.section()).isEqualTo(ResumeSection.SUMMARY));
        assertThat(advice.modelScores())
                .containsEntry("overallScore", 71)
                .containsEntry("atsScore", 84)
                .hasSize(6);
    }

    @Test
    @DisplayName("a fenced response reads the same as a bare one")
    void readsThroughAMarkdownFence() {
        AiAdvice advice = read("""
                ```json
                {"overallFeedback": "Solid.", "improvements": []}
                ```
                """);

        assertThat(advice.overallFeedback()).isEqualTo("Solid.");
    }

    @Test
    @DisplayName("a preamble and a postscript are both stepped over")
    void readsThroughAPreambleAndPostscript() {
        AiAdvice advice = read("""
                Here is the analysis you asked for:

                {"overallFeedback": "Solid."}

                Let me know if you would like me to expand on any of these points.
                """);

        assertThat(advice.overallFeedback()).isEqualTo("Solid.");
    }

    @Test
    @DisplayName("a missing key is an empty list, not a rejected response")
    void aMissingKeyIsAnEmptyList() {
        // The trade this makes: one absent field would otherwise discard five good suggestions and
        // serve the user offline advice instead of most of what the model actually wrote.
        AiAdvice advice = read("""
                {"overallFeedback": "Only this key came back.",
                 "improvements": [{"title": "Add numbers", "detail": "To the first two bullets."}]}
                """);

        assertThat(advice.overallFeedback()).isEqualTo("Only this key came back.");
        assertThat(advice.improvements()).hasSize(1);
        assertThat(advice.skillGaps()).isEmpty();
        assertThat(advice.recommendedProjects()).isEmpty();
        assertThat(advice.learningRecommendations()).isEmpty();
        assertThat(advice.suggestedKeywords()).isEmpty();
        assertThat(advice.sectionNotes()).isEmpty();
        assertThat(advice.modelScores()).isEmpty();
    }

    @Test
    @DisplayName("a key that came back as the wrong type yields nothing rather than throwing")
    void aWrongTypedKeyYieldsNothing() {
        AiAdvice advice = read("""
                {"overallFeedback": ["not", "a", "string"],
                 "improvements": {"title": "an object where an array belongs"},
                 "skillGaps": "docker",
                 "overallScore": "seventy"}
                """);

        assertThat(advice.overallFeedback()).isEmpty();
        assertThat(advice.improvements()).isEmpty();
        assertThat(advice.skillGaps()).isEmpty();
        assertThat(advice.modelScores()).isEmpty();
        assertThat(advice.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("both accepted spellings work for a gap's skill and a topic's link")
    void acceptsBothSpellings() {
        AiAdvice advice = read("""
                {"skillGaps": [{"slug": "kubernetes", "detail": "Absent."}],
                 "learningRecommendations": [
                   {"title": "Orchestration", "resourceUrl": "https://example.test/k8s"}],
                 "suggestedKeywords": [{"term": "CI/CD", "where": "the tooling bullet"}]}
                """);

        // "skill", "url" and "placement" are what the prompt asks for; "slug", "resourceUrl" and
        // "where" are what a model writes when it has read the findings block closely. Accepting both
        // costs one line each and saves three whole discarded lists.
        assertThat(advice.skillGaps()).singleElement()
                .satisfies(gap -> assertThat(gap.slug()).isEqualTo("kubernetes"));
        assertThat(advice.learningRecommendations()).singleElement()
                .satisfies(topic -> assertThat(topic.resourceUrl())
                        .isEqualTo("https://example.test/k8s"));
        assertThat(advice.suggestedKeywords()).singleElement()
                .satisfies(keyword -> assertThat(keyword.placement())
                        .isEqualTo("the tooling bullet"));
    }

    @Test
    @DisplayName("a bare string in the keyword list is dropped, not salvaged")
    void aBareStringKeywordIsDropped() {
        // The one recovery this reader refuses to make. A term with no placement is precisely the
        // keyword-stuffing suggestion the rules forbid: "add Kubernetes" with no honest answer to
        // "where". Salvaging it as a term with an empty placement would launder a forbidden
        // suggestion into an allowed shape.
        AiAdvice advice = read("""
                {"suggestedKeywords": ["kubernetes", {"term": "docker"},
                   {"term": "terraform", "placement": ""},
                   {"term": "aws", "placement": "the platform bullet"}]}
                """);

        assertThat(advice.suggestedKeywords()).singleElement()
                .satisfies(keyword -> assertThat(keyword.term()).isEqualTo("aws"));
    }

    @Test
    @DisplayName("an item with no title is dropped, because there is nothing to show for it")
    void anUntitledItemIsDropped() {
        AiAdvice advice = read("""
                {"improvements": [{"detail": "No title on this one."},
                   {"title": "   ", "detail": "Blank title."},
                   {"title": "Real", "detail": "Kept."}],
                 "recommendedProjects": [{"detail": "Untitled project."}],
                 "learningRecommendations": [{"detail": "Untitled topic."}],
                 "skillGaps": [{"detail": "No skill named."}]}
                """);

        assertThat(advice.improvements()).singleElement()
                .satisfies(item -> assertThat(item.title()).isEqualTo("Real"));
        assertThat(advice.recommendedProjects()).isEmpty();
        assertThat(advice.learningRecommendations()).isEmpty();
        assertThat(advice.skillGaps()).isEmpty();
    }

    @Test
    @DisplayName("an unparseable priority defaults to medium instead of losing the suggestion")
    void anUnknownPriorityDefaultsToMedium() {
        AiAdvice advice = read("""
                {"improvements": [{"title": "A", "priority": "urgent"},
                   {"title": "B", "priority": "Minor"},
                   {"title": "C", "priority": "somewhat important"},
                   {"title": "D"}]}
                """);

        // A default rather than a rejection: priority is a badge colour, and discarding a good
        // suggestion over one is the wrong trade.
        assertThat(advice.improvements()).extracting(AiAdvice.Improvement::priority)
                .containsExactly(Priority.HIGH, Priority.LOW, Priority.MEDIUM, Priority.MEDIUM);
    }

    @Test
    @DisplayName("a section name the enum does not have is dropped from a note and nulled on an item")
    void anUnknownSectionIsDropped() {
        AiAdvice advice = read("""
                {"sectionScores": [{"section": "PUBLICATIONS", "note": "Not one of ours."},
                   {"section": "work history", "note": "Nor this."},
                   {"section": "Experience", "note": "This one is real."}],
                 "improvements": [{"title": "Fix it", "section": "PUBLICATIONS"}]}
                """);

        assertThat(advice.sectionNotes()).singleElement().satisfies(note -> {
            assertThat(note.section()).isEqualTo(ResumeSection.EXPERIENCE);
            assertThat(note.note()).isEqualTo("This one is real.");
        });
        // On an improvement the section is a hint about where to apply the advice, so losing it costs
        // nothing worth discarding the advice over.
        assertThat(advice.improvements()).singleElement()
                .satisfies(item -> assertThat(item.section()).isNull());
    }

    @Test
    @DisplayName("section names are matched case-insensitively and with spaces for underscores")
    void sectionNamesAreMatchedLoosely() {
        AiAdvice advice = read("""
                {"sectionScores": [{"section": "certifications", "note": "Fine as it is."},
                   {"section": "Skills", "note": "Reorder it."},
                   {"section": "nice to have", "note": "Dropped, no such section."}]}
                """);

        assertThat(advice.sectionNotes()).extracting(AiAdvice.SectionNote::section)
                .containsExactly(ResumeSection.CERTIFICATIONS, ResumeSection.SKILLS);
    }

    @Test
    @DisplayName("the model's scores are clamped to the scale rather than trusted")
    void modelScoresAreClamped() {
        AiAdvice advice = read("""
                {"overallScore": 140, "atsScore": -20, "jobMatchScore": 61.7,
                 "skillsMatchScore": null}
                """);

        assertThat(advice.modelScores())
                .containsEntry("overallScore", 100)
                .containsEntry("atsScore", 0)
                .containsEntry("jobMatchScore", 61)
                .doesNotContainKey("skillsMatchScore");
    }

    @Test
    @DisplayName("a truncated response is a failure, because there is nothing to recover")
    void aTruncatedResponseIsRejected() {
        // The one response shape that is not recoverable: the output limit was hit mid-object, so the
        // span from the first brace to the last is not valid JSON.
        assertThatThrownBy(() -> read("""
                {"overallFeedback": "Started well", "improvements": [{"title": "Add num
                """))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("Falling back to computed advice");
    }

    @Test
    @DisplayName("a response with no object in it at all is a failure")
    void aResponseWithNoObjectIsRejected() {
        assertThatThrownBy(() -> read("I cannot help with that request."))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("did not return a JSON object");
    }

    @Test
    @DisplayName("an object wrapped in an array is recovered, since the brace span finds it anyway")
    void anArrayWrappedObjectIsRecovered() {
        // Not designed for, but worth pinning: the span from the first brace to the last lands on the
        // inner object, so a model that wrapped its answer in an array is understood.
        assertThat(read("[{\"overallFeedback\": \"wrapped in an array\"}]").overallFeedback())
                .isEqualTo("wrapped in an array");
    }

    @Test
    @DisplayName("content after the first object is ignored, even when it carries braces of its own")
    void trailingContentAfterTheFirstObjectIsIgnored() {
        // This test previously asserted that two objects are a failure, on the strength of a comment in
        // the reader claiming the brace span would produce invalid JSON. Both were wrong: Jackson reads
        // the first complete value and ignores trailing tokens unless FAIL_ON_TRAILING_TOKENS is set.
        // Rohit's build caught it. Keeping the leniency is the better outcome rather than a concession —
        // the second assertion is the case it buys, and switching it off would throw away a good
        // response over a closing pleasantry that happens to contain a brace.
        assertThat(read("{\"overallFeedback\": \"one\"} {\"overallFeedback\": \"two\"}")
                .overallFeedback()).isEqualTo("one");
        assertThat(read("{\"overallFeedback\": \"Solid.\"}\n\nHope this helps! {see the summary}")
                .overallFeedback()).isEqualTo("Solid.");
        // Taking the first of two answers is arbitrary, and safe for one reason worth stating: nothing
        // here decides what the user sees. Every structured claim still has to survive the sanitiser,
        // and a response that survives none of it is discarded by AiAdviceSource in favour of the
        // offline writer. So the cost of guessing wrong is prose, never a wrong number or a false gap.
    }

    @Test
    @DisplayName("an empty response is a failure and never a silently empty result")
    void anEmptyResponseIsRejected() {
        assertThatThrownBy(() -> read(""))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("empty response");
        assertThatThrownBy(() -> read("   \n  "))
                .isInstanceOf(AiInvalidResponseException.class);
        assertThatThrownBy(() -> AiAdviceReader.read(null))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    @DisplayName("a response from an unnamed model still carries a usable source")
    void anUnnamedModelStillHasASource() {
        assertThat(AiAdviceReader.read(new AiCompletion("{\"overallFeedback\": \"Fine.\"}", ""))
                .source()).isEqualTo("ai");
        assertThat(AiAdviceReader.read(new AiCompletion("{\"overallFeedback\": \"Fine.\"}", null))
                .source()).isEqualTo("ai");
    }

    private static AiAdvice read(String responseText) {
        return AiAdviceReader.read(new AiCompletion(responseText, MODEL));
    }
}
