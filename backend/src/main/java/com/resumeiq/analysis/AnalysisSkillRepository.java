package com.resumeiq.analysis;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Skill verdicts. Read through their analysis for the results page, and aggregated across
 * analyses for the skill-gap page.
 */
public interface AnalysisSkillRepository extends JpaRepository<AnalysisSkill, Long> {

    List<AnalysisSkill> findByAnalysisId(Long analysisId);

    List<AnalysisSkill> findByAnalysisIdAndStatus(Long analysisId, SkillStatus status);

    /**
     * The user's recurring gaps, most frequent first — the query the skill-gap page is built on.
     *
     * <p>Grouped by the canonical display name where the mention resolved to the taxonomy and by
     * the raw name where it did not, so unmatched skills still count instead of vanishing. The
     * {@code left join} matters: an inner join would silently drop every unresolved mention,
     * which is precisely the set most likely to contain a genuinely unusual requirement.
     *
     * <p>{@code coalesce} is repeated in the {@code group by} rather than referenced by its
     * alias, because JPQL does not allow grouping by a select alias.
     */
    @Query("""
            select coalesce(sk.displayName, asl.rawName) as label,
                   count(asl) as occurrences
            from AnalysisSkill asl
              left join asl.skill sk
              join asl.analysis a
            where a.user.id = :userId and asl.status = :status
            group by coalesce(sk.displayName, asl.rawName)
            order by count(asl) desc, coalesce(sk.displayName, asl.rawName) asc
            """)
    List<SkillGapCount> countGapsForUser(
            @Param("userId") Long userId,
            @Param("status") SkillStatus status,
            Pageable pageable);

    /** Cascade handles the normal path; this is for re-running an analysis in place. */
    @Transactional
    int deleteByAnalysisId(Long analysisId);
}
