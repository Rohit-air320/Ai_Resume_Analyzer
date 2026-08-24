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

    /** Every alias in use, for the seeder's uniqueness guard. */
    @Query("select a from Skill s join s.aliases a")
    List<String> findAllAliases();
}
