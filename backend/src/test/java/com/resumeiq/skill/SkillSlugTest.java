package com.resumeiq.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slugification, which is the whole canonicalisation mechanism in one static method.
 *
 * <p>Worth this many cases because the failures are invisible in the UI: a skill that slugs
 * wrongly does not error, it just quietly becomes a second skill nobody ever matches.
 */
class SkillSlugTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Java,java",
            "Spring Boot,spring-boot",
            "'  spring   BOOT  ',spring-boot",
            "Node.js,node-js",
            "React.js,react-js",
            "CI/CD,ci-cd",
            "PL/SQL,pl-sql",
            "scikit-learn,scikit-learn",
            "Power BI,power-bi",
            "Angular 2+,angular-2plus"
    })
    void slugifies(String raw, String expected) {
        assertThat(Skill.slugify(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("C, C++ and C# stay three different skills")
    void doesNotCollapseTheCFamily() {
        // Stripping punctuation would turn all three into "c", and a C# developer would be told
        // they are missing C++.
        assertThat(Skill.slugify("C")).isEqualTo("c");
        assertThat(Skill.slugify("C++")).isEqualTo("cplusplus");
        assertThat(Skill.slugify("C#")).isEqualTo("csharp");
    }

    @Test
    @DisplayName("a leading dot is spelled out, an interior dot separates")
    void handlesDotsByPosition() {
        assertThat(Skill.slugify(".NET")).isEqualTo("dotnet");
        assertThat(Skill.slugify(".NET Core")).isEqualTo("dotnet-core");
        assertThat(Skill.slugify("Node.js")).isEqualTo("node-js");
    }

    @Test
    @DisplayName("nothing usable slugs to null or empty rather than to punctuation")
    void handlesUnusableInput() {
        assertThat(Skill.slugify(null)).isNull();
        assertThat(Skill.slugify("   ")).isEmpty();
        assertThat(Skill.slugify("---")).isEmpty();
    }

    @Test
    @DisplayName("addAlias normalises, and refuses an alias equal to the skill's own slug")
    void aliasesAreNormalisedAndDeduplicated() {
        Skill skill = Skill.builder()
                .slug("spring-boot")
                .displayName("Spring Boot")
                .category(SkillCategory.FRAMEWORK)
                .build();

        skill.addAlias("SpringBoot");
        skill.addAlias("Spring-Boot");   // same as the slug once normalised
        skill.addAlias("  spring framework  ");
        skill.addAlias("SpringBoot");    // already held
        skill.addAlias("   ");
        skill.addAlias(null);

        assertThat(skill.getAliases()).containsExactly("springboot", "spring-framework");
    }
}
