package com.resumeiq.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starting the application a second time, against a catalogue that is already in the database.
 *
 * <p>This is the only test that exercises the seeder the way production does, and it exists because
 * every other one hid a real bug. {@link SkillCatalogSeederTest} is a {@code @DataJpaTest} that
 * builds the seeder with {@code new} — so there is no proxy, its {@code @Transactional} is inert, and
 * the test's own transaction keeps a session open for the whole method. {@code JobDescriptionApiTest}
 * seeds at startup but into an empty database, where every skill is a brand new object. Between them,
 * the path that matters was never run: an <em>existing</em> row, loaded with <em>no</em> ambient
 * transaction.
 *
 * <p>On that path {@link SkillCatalogSeeder#run} called {@link SkillCatalogSeeder#seed} on
 * {@code this}. A self-invocation does not pass through the transactional proxy, so the startup
 * seeding ran with no transaction at all; each repository call committed on its own and handed back a
 * detached entity, and the first read of a skill's lazy alias set threw
 * {@code LazyInitializationException}. A fresh database never noticed. The second start of every
 * MySQL deployment would have — the app would have failed to boot, once, on the machine where it
 * mattered most.
 *
 * <p>So: the seeder is <b>injected</b>, not constructed, because the proxy is the thing under test;
 * there is deliberately no {@code @Transactional} on this class; and seeding is left <b>on</b>, which
 * makes the startup pass the first boot and the calls below the second.
 *
 * <p>Its own in-memory database, like every other test whose writes commit — the dev profile keeps
 * H2 alive for the whole JVM, and a shared database that changes with test order is how a suite
 * starts failing only on CI.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "resumeiq.seed.skills=true",
        "spring.datasource.url=jdbc:h2:mem:resumeiq-seed-commits;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class SkillCatalogSeederCommitsTest {

    @Autowired
    private SkillCatalogSeeder seeder;

    @Autowired
    private SkillRepository skills;

    @Test
    @DisplayName("a second start reads the committed catalogue, changes nothing, and does not throw")
    void seedsAgainstACommittedCatalogue() {
        // Written by the ApplicationRunner during context startup, and committed.
        long catalogueSize = skills.count();
        List<String> aliasesBefore = skills.findAllAliases();
        assertThat(catalogueSize).isGreaterThan(100);
        assertThat(aliasesBefore).isNotEmpty();

        SkillCatalogSeeder.SeedResult second = seeder.seed();

        // Every skill on this pass is an existing row. Reaching this assertion at all is most of
        // the point: the alias comparison inside seed() has to read a lazy collection on each one.
        assertThat(second.changedNothing()).isTrue();
        assertThat(second.skillsInserted()).isZero();
        assertThat(second.aliasesAdded()).isZero();
        assertThat(second.skillsSkipped()).isEqualTo((int) catalogueSize);

        // And through the runner, which is the entry point Boot actually calls.
        seeder.run(new DefaultApplicationArguments());

        assertThat(skills.count()).isEqualTo(catalogueSize);
        // No alias was duplicated or dropped along the way. Both would break canonicalisation
        // silently: a duplicate makes resolution depend on row order, a dropped one makes a
        // spelling people write stop matching.
        assertThat(skills.findAllAliases())
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(aliasesBefore);
    }
}
