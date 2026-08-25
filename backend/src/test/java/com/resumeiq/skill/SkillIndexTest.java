package com.resumeiq.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue arranged for matching, and the two credibility rules.
 *
 * <p>Both rules exist because of advice that destroys trust in the whole feature: being told to
 * learn R because the posting mentioned R&amp;D, or that a company which "reacts quickly to
 * incidents" requires React. They are about how the word was <em>written</em>, which is the only
 * signal available — postings capitalise technology names and prose does not.
 */
class SkillIndexTest {

    private static final CatalogSkill JAVA =
            new CatalogSkill("java", "Java", SkillCategory.LANGUAGE);
    private static final CatalogSkill SPRING_BOOT =
            new CatalogSkill("spring-boot", "Spring Boot", SkillCategory.FRAMEWORK);
    private static final CatalogSkill AWS =
            new CatalogSkill("amazon-web-services", "Amazon Web Services", SkillCategory.CLOUD);
    private static final CatalogSkill R =
            new CatalogSkill("r", "R", SkillCategory.LANGUAGE);
    private static final CatalogSkill GO =
            new CatalogSkill("go", "Go", SkillCategory.LANGUAGE);

    private final SkillIndex index = SkillIndex.of(List.of(JAVA, SPRING_BOOT, AWS, R, GO));

    @Test
    @DisplayName("a known slug resolves whatever the casing, once it is longer than a letter")
    void findsBySlug() {
        assertThat(index.find("java", "Java")).contains(JAVA);
        assertThat(index.find("java", "java")).contains(JAVA);
        assertThat(index.find("java", "JAVA")).contains(JAVA);
        assertThat(index.find("spring-boot", "Spring")).contains(SPRING_BOOT);
    }

    @Test
    @DisplayName("a term the catalogue does not know resolves to nothing")
    void findsNothingForAnUnknownTerm() {
        assertThat(index.find("cobol", "COBOL")).isEmpty();
        assertThat(index.find("", "")).isEmpty();
    }

    @Test
    @DisplayName("a one-letter skill must be written as exactly that capital letter")
    void guardsOneLetterSkills() {
        assertThat(index.find("r", "R")).contains(R);

        // The whole reason this rule exists: "R&D" slugs to r-d, whose first token is r.
        assertThat(index.find("r", "R&D")).isEmpty();
        assertThat(index.find("r", "r")).isEmpty();
        assertThat(index.find("r", "r/o")).isEmpty();
    }

    @Test
    @DisplayName("a skill spelled like an English word must be capitalised")
    void guardsSkillsThatAreAlsoWords() {
        assertThat(index.find("go", "Go")).contains(GO);
        assertThat(index.find("go", "GO")).contains(GO);

        // "a go-getter", "go the extra mile", "things go wrong" — none of these is the language.
        assertThat(index.find("go", "go")).isEmpty();
        assertThat(index.find("go", "go-getter")).isEmpty();
    }

    @Test
    @DisplayName("the widest term in the catalogue sets the window the matcher has to try")
    void reportsTheLongestTerm() {
        // "amazon-web-services" is three words, so a matcher must try three-word windows.
        assertThat(index.maxTermWords()).isEqualTo(3);
        assertThat(SkillIndex.of(List.of(JAVA)).maxTermWords()).isEqualTo(1);
        assertThat(index.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("aliases become keys of their own")
    void indexesAliases() {
        Skill springBoot = Skill.builder()
                .slug("spring-boot")
                .displayName("Spring Boot")
                .category(SkillCategory.FRAMEWORK)
                .build();
        springBoot.addAlias("springboot");
        springBoot.addAlias("Spring-Boot");

        SkillIndex fromEntities = SkillIndex.fromEntities(List.of(springBoot));

        // "Spring-Boot" normalises onto the slug, which addAlias refuses to store twice, so two
        // keys is the right answer here and three would mean the slug had been duplicated.
        assertThat(fromEntities.size()).isEqualTo(2);
        assertThat(fromEntities.find("springboot", "SpringBoot"))
                .get()
                .extracting(CatalogSkill::displayName)
                .isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("an empty index matches nothing and still reports a usable window")
    void handlesAnEmptyCatalogue() {
        SkillIndex empty = SkillIndex.empty();

        assertThat(empty.size()).isZero();
        assertThat(empty.find("java", "Java")).isEmpty();
        // Never zero: the matcher loops from one to this number, and zero would skip every token.
        assertThat(empty.maxTermWords()).isEqualTo(1);
    }
}
