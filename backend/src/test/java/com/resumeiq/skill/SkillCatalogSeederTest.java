package com.resumeiq.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The startup seeding of the skill taxonomy.
 *
 * <p>The seeder is constructed by hand rather than injected: {@code @DataJpaTest} deliberately
 * leaves {@code @Component} beans out, and building it here also proves it has no dependency
 * beyond a repository, a mapper and one flag. Its own {@code @Transactional} does nothing when
 * called directly — the test's transaction is what wraps these writes, and rolls them back.
 */
@RepositoryTest
class SkillCatalogSeederTest {

    @Autowired
    private SkillRepository skills;

    @Autowired
    private TestEntityManager em;

    private SkillCatalogSeeder seeder(boolean enabled) {
        // The seeder reads one flag. It takes the whole properties record because that is a
        // single object, and a null for the parts it ignores would invite a null check into
        // production code that has no reason to want one.
        return new SkillCatalogSeeder(skills, new ObjectMapper(),
                TestProperties.withSeedSkills(enabled));
    }

    @Test
    @DisplayName("the catalogue loads once and a second run changes nothing")
    void isIdempotent() {
        SkillCatalogSeeder.SeedResult first = seeder(true).seed();
        em.flush();
        em.clear();

        int catalogueSize = first.skillsInserted() + first.skillsSkipped();
        assertThat(catalogueSize).isGreaterThan(100);
        assertThat(skills.count()).isEqualTo(catalogueSize);

        SkillCatalogSeeder.SeedResult second = seeder(true).seed();
        em.flush();

        // Restarting the app must be free. Anything else means duplicated rows or a startup that
        // fails on the unique index the second time it runs.
        assertThat(second.changedNothing()).isTrue();
        assertThat(second.skillsSkipped()).isEqualTo(catalogueSize);
        assertThat(skills.count()).isEqualTo(catalogueSize);
    }

    @Test
    @DisplayName("no alias is claimed by two skills, and no alias shadows another skill's slug")
    void aliasesResolveDeterministically() {
        seeder(true).seed();
        em.flush();
        em.clear();

        List<String> aliases = skills.findAllAliases();

        assertThat(aliases).isNotEmpty().doesNotHaveDuplicates();
        assertThat(aliases).allSatisfy(alias ->
                assertThat(skills.findBySlug(alias))
                        .as("alias '%s' must not also be a canonical slug", alias)
                        .isEmpty());
    }

    @Test
    @DisplayName("the spellings people actually write resolve to the right skill")
    void resolvesRealWorldSpellings() {
        seeder(true).seed();
        em.flush();
        em.clear();

        assertThat(skills.findByAlias("springboot")).get()
                .extracting(Skill::getDisplayName).isEqualTo("Spring Boot");
        assertThat(skills.findByAlias("k8s")).get()
                .extracting(Skill::getDisplayName).isEqualTo("Kubernetes");
        assertThat(skills.findByAlias("js")).get()
                .extracting(Skill::getDisplayName).isEqualTo("JavaScript");
        assertThat(skills.findByAlias("oops")).get()
                .extracting(Skill::getDisplayName).isEqualTo("Object-Oriented Programming");
        assertThat(skills.findByAlias("not-a-real-alias")).isEmpty();
    }

    @Test
    @DisplayName("punctuated names survive the round trip to the database")
    void storesAwkwardNames() {
        seeder(true).seed();
        em.flush();
        em.clear();

        assertThat(skills.findBySlug("cplusplus")).get()
                .extracting(Skill::getDisplayName).isEqualTo("C++");
        assertThat(skills.findBySlug("csharp")).get()
                .extracting(Skill::getDisplayName).isEqualTo("C#");
        assertThat(skills.findBySlug("dotnet")).get()
                .extracting(Skill::getDisplayName).isEqualTo(".NET");
        assertThat(skills.findBySlug("node-js")).get()
                .extracting(Skill::getDisplayName).isEqualTo("Node.js");
        assertThat(skills.findBySlug("ci-cd")).get()
                .extracting(Skill::getDisplayName).isEqualTo("CI/CD");
    }

    @Test
    @DisplayName("every category in the taxonomy has skills in it")
    void coversEveryCategory() {
        seeder(true).seed();
        em.flush();
        em.clear();

        // An empty category is a dead filter in the UI and a blind spot in the gap analysis.
        for (SkillCategory category : SkillCategory.values()) {
            assertThat(skills.findByCategoryOrderByDisplayNameAsc(category))
                    .as("category %s has no skills", category)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("seeding can be switched off")
    void respectsTheFlag() {
        long before = skills.count();

        seeder(false).run(new DefaultApplicationArguments());
        em.flush();

        assertThat(skills.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("the runner path seeds the same way the direct call does")
    void runsAsAnApplicationRunner() {
        seeder(true).run(new DefaultApplicationArguments());
        em.flush();
        em.clear();

        assertThat(skills.count()).isGreaterThan(100);
        assertThat(skills.findBySlug("java")).isPresent();
    }
}
