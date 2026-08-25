package com.resumeiq.jobdescription.parse;

import com.resumeiq.skill.SkillCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Keyword ranking, which is where a frequency counter turns into advice or into noise.
 *
 * <p>The tests are organised around the four decisions in the extractor, and two of them are
 * really about what this product refuses to recommend. Nothing from the perks section is advice,
 * and neither is "passionate" or "fast-paced" — telling somebody to put those on a resume would
 * make it worse, and it is exactly what a naive extractor suggests with total confidence.
 */
class KeywordExtractorTest {

    private static final int NO_CAP = 100;

    @Test
    @DisplayName("a pair seen twice becomes one term, and its halves disappear with it")
    void keepsPairsAndSilencesTheirWords() {
        List<Keyword> keywords = extract("""
                Distributed systems design
                distributed systems scaling
                """);

        // "distributed" and "systems" only ever appeared inside the pair, so subtracting the
        // pair's count takes each of them to zero without a special case for it.
        assertThat(keywords).extracting(Keyword::term)
                .containsExactly("Distributed systems", "design", "scaling");
        assertThat(keywords).first()
                .satisfies(pair -> assertThat(pair.occurrences()).isEqualTo(2));
    }

    @Test
    @DisplayName("a pair seen once is two words, not a phrase")
    void ignoresAOneOffPair() {
        assertThat(extract("Distributed caching matters"))
                .extracting(Keyword::term)
                .containsExactly("caching", "Distributed", "matters");
    }

    @Test
    @DisplayName("a word left over after its pair is claimed still counts on its own")
    void keepsTheRemainderOfAHalfUsedWord() {
        List<Keyword> keywords = extract("""
                Unit testing discipline
                unit testing coverage
                unit conversions
                """);

        assertThat(keywords).extracting(Keyword::term, Keyword::occurrences)
                .contains(tuple("Unit testing", 2))
                // Three sightings of "unit", two of them inside the kept pair. The third is real.
                .contains(tuple("Unit", 1));
    }

    @Test
    @DisplayName("nothing from the perks section is advice")
    void ignoresTheBenefitsSection() {
        List<Keyword> keywords = KeywordExtractor.extract(
                List.of(new PostingBlock(PostingSection.BENEFITS, "Perks",
                        "Conference budget, gym membership, generous equity")),
                List.of(), NO_CAP);

        // Not weak evidence — no evidence. "equity" is a frequent word in postings and would rank
        // well, and "add equity to your resume" is not something this product will ever say.
        assertThat(keywords).isEmpty();
    }

    @Test
    @DisplayName("a skill already reported is not repeated as a keyword, nor are its words")
    void excludesDetectedSkills() {
        List<Keyword> keywords = KeywordExtractor.extract(
                List.of(requirements("Spring Boot microservices")),
                List.of(new DetectedSkill("spring-boot", "Spring Boot", SkillCategory.FRAMEWORK,
                        SkillImportance.REQUIRED, 1, PostingSection.REQUIREMENTS, "Requirements")),
                NO_CAP);

        // Both halves of the slug are silenced. Otherwise the API says "Spring Boot is required"
        // and, three fields later, "consider adding the keyword boot".
        assertThat(keywords).extracting(Keyword::term).containsExactly("microservices");
    }

    @Test
    @DisplayName("stopwords, posting boilerplate and bare numbers never rank")
    void excludesWordsThatAreNotAdvice() {
        assertThat(extract("5 years of strong experience with a passionate fast-paced team"))
                .isEmpty();
    }

    @Test
    @DisplayName("the requirements section outranks the day-to-day at equal frequency")
    void weightsBySection() {
        List<Keyword> keywords = KeywordExtractor.extract(
                List.of(new PostingBlock(PostingSection.RESPONSIBILITIES, "The role", "kafka"),
                        requirements("grpc")),
                List.of(), NO_CAP);

        assertThat(keywords).extracting(Keyword::term).containsExactly("grpc", "kafka");
        assertThat(keywords).allSatisfy(keyword ->
                assertThat(keyword.isFromDemandingSection()).isTrue());
    }

    @Test
    @DisplayName("the term is quoted back in the posting's own strongest capitalisation")
    void prefersTheWrittenSpelling() {
        assertThat(extract("kubernetes clusters\nKubernetes upgrades"))
                .extracting(Keyword::term)
                .contains("Kubernetes")
                .doesNotContain("kubernetes");
    }

    @Test
    @DisplayName("the cap is applied after ranking, so what survives is the top of the list")
    void appliesTheCap() {
        List<Keyword> keywords = KeywordExtractor.extract(
                List.of(requirements("kafka kafka microservices microservices grpc")),
                List.of(), 2);

        assertThat(keywords).hasSize(2);
        assertThat(keywords).extracting(Keyword::term).containsExactly("kafka", "microservices");
    }

    @Test
    @DisplayName("a posting with nothing in it produces no keywords")
    void handlesEmptyInput() {
        assertThat(KeywordExtractor.extract(List.of(), List.of(), NO_CAP)).isEmpty();
        assertThat(extract("   ")).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private static List<Keyword> extract(String text) {
        return KeywordExtractor.extract(List.of(requirements(text)), List.of(), NO_CAP);
    }

    private static PostingBlock requirements(String text) {
        return new PostingBlock(PostingSection.REQUIREMENTS, "Requirements", text);
    }
}
