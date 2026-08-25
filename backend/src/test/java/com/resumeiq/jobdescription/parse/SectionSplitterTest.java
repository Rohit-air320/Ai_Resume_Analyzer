package com.resumeiq.jobdescription.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Heading detection over text people wrote by hand.
 *
 * <p>Most of these tests are about the <em>absence</em> of a heading, which is the right emphasis: a
 * missed heading costs precision, an invented heading costs content. When a body line is read as a
 * label it is consumed, and the skills in it stop existing as far as the rest of the product is
 * concerned. {@link #doesNotReadASentenceAsAHeading()} and {@link #treatsBulletsAsContent()} are
 * the two that guard that, and both describe a line that broke an earlier version of the splitter.
 */
class SectionSplitterTest {

    private static final String POSTING = """
            Backend Engineer

            About us
            Acme builds logistics software in Bengaluru.

            Responsibilities
            - Build and ship services with Spring Boot
            - Review code

            Requirements:
            - Strong Java
            - Minimum 3 years of experience

            Nice to have
            - Docker and Kubernetes

            Benefits
            - Health insurance and a learning budget
            """;

    @Test
    @DisplayName("a conventional posting splits into its sections, in order")
    void splitsAConventionalPosting() {
        List<PostingBlock> blocks = SectionSplitter.split(POSTING);

        assertThat(blocks).extracting(PostingBlock::section).containsExactly(
                PostingSection.OTHER,
                PostingSection.COMPANY,
                PostingSection.RESPONSIBILITIES,
                PostingSection.REQUIREMENTS,
                PostingSection.PREFERRED,
                PostingSection.BENEFITS);

        // The title line arrives before any heading, so it belongs to nothing in particular.
        assertThat(blocks.get(0).heading()).isNull();
        assertThat(blocks.get(0).text()).isEqualTo("Backend Engineer");
    }

    @Test
    @DisplayName("the heading is kept as the poster wrote it, without its colon")
    void keepsTheHeadingAsWritten() {
        List<PostingBlock> blocks = SectionSplitter.split(POSTING);

        assertThat(blocks).extracting(PostingBlock::heading).containsExactly(
                null, "About us", "Responsibilities", "Requirements", "Nice to have", "Benefits");
    }

    @Test
    @DisplayName("each block holds the lines under its own heading and nothing else")
    void keepsBodiesWithTheirHeadings() {
        List<PostingBlock> blocks = SectionSplitter.split(POSTING);

        PostingBlock requirements = blocks.get(3);
        assertThat(requirements.text()).contains("Strong Java", "Minimum 3 years");
        assertThat(requirements.text()).doesNotContain("Docker", "Review code");
    }

    @Test
    @DisplayName("a labelled line opens a section and keeps its own content")
    void keepsTheContentOfALabelledLine() {
        List<PostingBlock> blocks = SectionSplitter.split("Skills: Java, Spring Boot, MySQL");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).section()).isEqualTo(PostingSection.REQUIREMENTS);
        assertThat(blocks.get(0).heading()).isEqualTo("Skills");
        // The old failure: the label was recognised and the rest of the line went with it, so a
        // posting written entirely in this style reported no skills at all.
        assertThat(blocks.get(0).text()).isEqualTo("Java, Spring Boot, MySQL");
    }

    @Test
    @DisplayName("markdown decoration marks a heading rather than hiding it")
    void seesThroughMarkdown() {
        List<PostingBlock> blocks = SectionSplitter.split("""
                **Requirements**
                Java and MySQL
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).section()).isEqualTo(PostingSection.REQUIREMENTS);
        assertThat(blocks.get(0).heading()).isEqualTo("Requirements");
    }

    @Test
    @DisplayName("a bullet is content, however it starts")
    void treatsBulletsAsContent() {
        List<PostingBlock> blocks = SectionSplitter.split("""
                - Must have Java
                * Requirements gathering with stakeholders
                1. Responsibilities include SQL tuning
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).section()).isEqualTo(PostingSection.OTHER);
        assertThat(blocks.get(0).text()).contains("Java", "SQL tuning");
    }

    @Test
    @DisplayName("a sentence that merely starts like a heading stays content")
    void doesNotReadASentenceAsAHeading() {
        List<PostingBlock> blocks = SectionSplitter.split("""
                We are hiring for our platform team.
                Must have Java experience
                Docker is a plus
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).heading()).isNull();
        // If "Must have Java experience" had been read as a heading, the Java would be gone.
        assertThat(blocks.get(0).text()).contains("Must have Java experience", "Docker is a plus");
    }

    @Test
    @DisplayName("a digit in the line is enough to make it content")
    void treatsALineWithANumberAsContent() {
        List<PostingBlock> blocks = SectionSplitter.split("""
                Minimum 5 years of Java
                Experience with Kubernetes is a plus
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).section()).isEqualTo(PostingSection.OTHER);
        assertThat(blocks.get(0).text()).contains("Minimum 5 years of Java");
    }

    @Test
    @DisplayName("a long heading is recognised when it is marked, and not when it is bare")
    void needsAMarkForALongHeading() {
        List<PostingBlock> marked = SectionSplitter.split("""
                Requirements and qualifications:
                Java
                """);
        assertThat(marked).hasSize(1);
        assertThat(marked.get(0).section()).isEqualTo(PostingSection.REQUIREMENTS);

        List<PostingBlock> bare = SectionSplitter.split("""
                Requirements and qualifications
                Java
                """);
        // Missed, deliberately: precision is the cheaper thing to lose. The Java is still here.
        assertThat(bare).hasSize(1);
        assertThat(bare.get(0).section()).isEqualTo(PostingSection.OTHER);
        assertThat(bare.get(0).text()).contains("Java");
    }

    @Test
    @DisplayName("an optional marker anywhere in a heading downgrades it")
    void downgradesAnOptionalHeading() {
        List<PostingBlock> blocks = SectionSplitter.split("""
                Skills we'd love to see:
                Kubernetes
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).section()).isEqualTo(PostingSection.PREFERRED);
        assertThat(blocks.get(0).importance()).isEqualTo(SkillImportance.PREFERRED);
    }

    @Test
    @DisplayName("capitals earn a heading extra words without a colon")
    void allowsShoutedHeadings() {
        List<PostingBlock> blocks = SectionSplitter.split("""
                WHAT WE ARE LOOKING FOR
                Java, MySQL
                """);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).section()).isEqualTo(PostingSection.REQUIREMENTS);
        assertThat(blocks.get(0).heading()).isEqualTo("WHAT WE ARE LOOKING FOR");
    }

    @Test
    @DisplayName("text with no headings comes back as one block, not as nothing")
    void alwaysReturnsTheText() {
        List<PostingBlock> blocks = SectionSplitter.split(
                "We need someone who can write Java and talk to customers.");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).section()).isEqualTo(PostingSection.OTHER);
        assertThat(blocks.get(0).text()).contains("Java");
    }

    @Test
    @DisplayName("blank input is no blocks at all")
    void handlesBlankInput() {
        assertThat(SectionSplitter.split(null)).isEmpty();
        assertThat(SectionSplitter.split("   \n  \n")).isEmpty();
    }
}
