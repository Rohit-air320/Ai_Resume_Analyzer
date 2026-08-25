package com.resumeiq.jobdescription.parse;

import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.skill.Skill;
import com.resumeiq.skill.SkillCategory;
import com.resumeiq.skill.SkillRepository;
import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestProperties;
import com.resumeiq.user.ExperienceLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parser wired to a real catalogue.
 *
 * <p>The pieces it calls are pure and are tested on their own; what only shows up here is the
 * wiring: that the catalogue is read from the database with its aliases attached, and what happens
 * to a posting whose formatting did not survive the clipboard. That second case is the one worth
 * reading — a posting with no headings is read as requirements, and yet
 * {@link PostingInsight#sectionsFound()} still says no requirements heading was found, so the UI
 * can never claim a heading exists that the user would scroll up and fail to see.
 *
 * <p>The catalogue is emptied and rebuilt per test. The dev profile's H2 database is shared across
 * the suite, and a test whose expected skills depend on rows another class happened to leave behind
 * is a test that fails for reasons that have nothing to do with it.
 */
@RepositoryTest
class JobPostingParserTest {

    private static final String STRUCTURED_POSTING = """
            Senior Backend Engineer

            About us
            Northwind builds logistics software for small carriers.

            Responsibilities
            Build and operate REST APIs with Spring Boot.

            Requirements:
            5+ years of experience with Java and MySQL.
            Comfortable owning services end to end.

            Nice to have
            Docker and Kubernetes.

            Benefits
            A conference budget and a React course of your choosing.
            """;

    @Autowired
    private SkillRepository skills;

    private JobPostingParser parser;

    @BeforeEach
    void setUp() {
        // deleteAll rather than deleteAllInBatch: aliases live in an @ElementCollection, and a bulk
        // delete of the parent rows leaves them behind to trip the foreign key.
        skills.deleteAll();
        skills.saveAndFlush(skill("java", "Java", SkillCategory.LANGUAGE));
        skills.saveAndFlush(skill("mysql", "MySQL", SkillCategory.DATABASE));
        skills.saveAndFlush(skill("docker", "Docker", SkillCategory.DEVOPS));
        skills.saveAndFlush(skill("react", "React", SkillCategory.FRAMEWORK));

        Skill springBoot = skill("spring-boot", "Spring Boot", SkillCategory.FRAMEWORK);
        springBoot.addAlias("springboot");
        skills.saveAndFlush(springBoot);

        parser = new JobPostingParser(skills, TestProperties.defaults());
    }

    @Test
    @DisplayName("a posting with headings is read section by section")
    void readsAStructuredPosting() {
        PostingInsight insight = parser.parse(STRUCTURED_POSTING, "Senior Backend Engineer");

        assertThat(insight.structured()).isTrue();
        // Declaration order, from the EnumSet — not hash order. This set reaches the JSON, and a
        // field whose array order changes between identical requests cannot be tested.
        assertThat(insight.sectionsFound()).containsExactly(
                PostingSection.PREFERRED, PostingSection.BENEFITS, PostingSection.COMPANY,
                PostingSection.REQUIREMENTS, PostingSection.RESPONSIBILITIES);
        assertThat(insight.wordCount()).isGreaterThan(30);

        assertThat(insight.requiredSkills()).extracting(DetectedSkill::displayName)
                // Spring Boot is required because the day-to-day work names it, which is the only
                // place plenty of postings ever name their stack.
                .containsExactly("Java", "MySQL", "Spring Boot");
        assertThat(insight.preferredSkills()).extracting(DetectedSkill::displayName)
                .containsExactly("Docker");
        assertThat(insight.skillSlugs()).containsExactly("java", "mysql", "spring-boot",
                "docker", "react");
    }

    @Test
    @DisplayName("a term the catalogue has never heard of comes back as a keyword instead")
    void reportsUnknownTermsAsKeywords() {
        PostingInsight insight = parser.parse(STRUCTURED_POSTING, "Senior Backend Engineer");

        assertThat(insight.skillSlugs()).doesNotContain("kubernetes");
        assertThat(insight.keywordTerms()).contains("Kubernetes")
                // A skill already reported is not repeated as a keyword, and neither are the words
                // inside it.
                .doesNotContain("Java", "Spring", "Boot", "Docker");
    }

    @Test
    @DisplayName("the perks section contributes no advice, whatever it mentions")
    void keepsThePerksOutOfTheAdvice() {
        PostingInsight insight = parser.parse(STRUCTURED_POSTING, "Senior Backend Engineer");

        // React is mentioned, and it is mentioned as a training budget. Reporting it as something
        // the posting asks for would be a confident lie about the job.
        assertThat(insight.skills()).filteredOn(skill -> skill.slug().equals("react"))
                .singleElement()
                .satisfies(react -> {
                    assertThat(react.importance()).isEqualTo(SkillImportance.MENTIONED);
                    assertThat(react.foundUnder()).isEqualTo("Benefits");
                });
        assertThat(insight.keywordTerms()).doesNotContain("conference", "budget", "course");
    }

    @Test
    @DisplayName("experience is read from the years and the title together")
    void readsTheExperienceDemand() {
        ExperienceDemand experience =
                parser.parse(STRUCTURED_POSTING, "Senior Backend Engineer").experience();

        assertThat(experience.minYears()).isEqualTo(5);
        // Five years alone is MID. The title is what makes this senior, so the title is what is
        // quoted back.
        assertThat(experience.level()).isEqualTo(ExperienceLevel.SENIOR);
        assertThat(experience.evidence()).isEqualTo("senior");
    }

    @Test
    @DisplayName("a posting with no headings is read as requirements, and still admits it had none")
    void readsAnUnstructuredPostingAsRequirements() {
        PostingInsight insight = parser.parse("""
                We are hiring a backend engineer for our logistics platform.
                The person we hire will write Java and Spring Boot services against MySQL,
                and will help us move the last of them into Docker.
                """, "Backend Engineer");

        // Someone who pastes a wall of text still means "this is what the job needs", and a parser
        // that answered "nothing is required here" would be right and useless.
        assertThat(insight.requiredSkills()).extracting(DetectedSkill::displayName)
                .containsExactly("Docker", "Java", "MySQL", "Spring Boot");
        // And the promotion does not invent a heading. Both of these stay honest about the input.
        assertThat(insight.structured()).isFalse();
        assertThat(insight.sectionsFound()).isEmpty();
        assertThat(insight.skills()).allSatisfy(skill ->
                assertThat(skill.foundUnder()).isNull());
    }

    @Test
    @DisplayName("an alias in the catalogue resolves to its canonical skill")
    void resolvesAliasesFromTheDatabase() {
        PostingInsight insight = parser.parse("We build SpringBoot services.", "Engineer");

        // The alias lives in a lazy @ElementCollection, so this also fails if the catalogue is ever
        // loaded with findAll() instead of findAllWithAliases().
        assertThat(insight.skills()).singleElement()
                .extracting(DetectedSkill::displayName)
                .isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("a skill added to the catalogue is picked up without a restart")
    void readsTheCatalogueOnEveryParse() {
        String posting = "We are moving our pipelines to Kubernetes.";
        assertThat(parser.parse(posting, "Engineer").skills()).isEmpty();

        skills.saveAndFlush(skill("kubernetes", "Kubernetes", SkillCategory.DEVOPS));

        // Nothing is cached, deliberately: a catalogue cached in a field means the skill added on
        // Tuesday is invisible until the next deploy, which is miserable to diagnose.
        assertThat(parser.parse(posting, "Engineer").skills())
                .extracting(DetectedSkill::slug)
                .containsExactly("kubernetes");
    }

    @Test
    @DisplayName("text with nothing in it parses to the empty insight rather than failing")
    void handlesEmptyText() {
        assertThat(parser.parse(null, "Engineer")).isEqualTo(PostingInsight.empty());
        assertThat(parser.parse("   \n\n   ", "Engineer")).isEqualTo(PostingInsight.empty());
        assertThat(parser.parse("", null).experience().isStated()).isFalse();
    }

    @Test
    @DisplayName("the keyword cap from configuration is respected")
    void appliesTheConfiguredKeywordCap() {
        ResumeIqProperties.Posting capped = TestProperties.posting(200, 20_000, 3);
        JobPostingParser tightParser = new JobPostingParser(skills, TestProperties.withPosting(capped));

        PostingInsight insight = tightParser.parse(STRUCTURED_POSTING, "Senior Backend Engineer");

        // A checklist of two hundred keywords is not advice anyone can act on, and presenting one
        // is how a tool ends up encouraging keyword stuffing.
        assertThat(insight.keywords()).hasSize(3);
    }

    // ---------------------------------------------------------------- helpers

    private static Skill skill(String slug, String displayName, SkillCategory category) {
        return Skill.builder().slug(slug).displayName(displayName).category(category).build();
    }
}
