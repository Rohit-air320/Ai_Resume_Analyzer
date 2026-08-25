package com.resumeiq.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The shared skill taxonomy. Reference data, so nothing here is scoped to a user.
 */
public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Skill> findBySlugIn(Collection<String> slugs);

    List<Skill> findAllByOrderByDisplayNameAsc();

    List<Skill> findByCategoryOrderByDisplayNameAsc(SkillCategory category);

    /**
     * Resolves an alternative spelling to its canonical skill.
     *
     * <p>Written as JPQL because the property is inside an {@code @ElementCollection} and the
     * derived-query parser cannot express "join the collection and match an element". The alias
     * is expected already slugified — {@link Skill#slugify} is the single place that decides
     * what two spellings being the same means, and doing it in SQL instead would put that rule
     * in two places.
     */
    @Query("select s from Skill s join s.aliases a where a = :alias")
    Optional<Skill> findByAlias(@Param("alias") String alias);

    /**
     * Every alias in use.
     *
     * <p>The seeder used to read this to guard alias uniqueness and now derives the same set from
     * {@link #findAllWithAliases()}, which it needs anyway. It stays because "no alias is claimed
     * twice and none shadows a slug" is asserted directly against the seeded catalogue, and that
     * assertion is the reason a misspelling in {@code skills.json} cannot quietly make skill
     * resolution depend on row order.
     */
    @Query("select a from Skill s join s.aliases a")
    List<String> findAllAliases();

    /**
     * The whole catalogue with aliases already loaded, for building a {@link SkillIndex}.
     *
     * <p>{@code findAll()} will not do. Aliases are a lazy {@code @ElementCollection}, and with
     * {@code open-in-view: false} reading them after the service method returns throws
     * {@code LazyInitializationException} — or, worse, works inside a transaction and fails only
     * in the code path nobody tested. Fetching them in one query also avoids one extra select per
     * skill, which on a few hundred rows is the difference between one query and a few hundred.
     *
     * <p>{@code distinct} is required: a join to a collection repeats the parent row once per
     * alias, so without it a skill with four aliases comes back four times.
     */
    @Query("select distinct s from Skill s left join fetch s.aliases")
    List<Skill> findAllWithAliases();
}
