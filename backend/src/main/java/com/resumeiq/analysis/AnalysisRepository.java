package com.resumeiq.analysis;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Analyses, always reached through their owner.
 *
 * <p>Note what is absent: there is no {@code findByPublicId}. Ownership is part of every lookup
 * signature, so a service cannot load an analysis without saying whose it is, and the
 * authorisation check cannot be left out by accident.
 */
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Optional<Analysis> findByPublicIdAndUserId(UUID publicId, Long userId);

    /**
     * The results page in one round trip.
     *
     * <p>Without the entity graph this is the classic N+1: one query for the analysis, one for
     * its skills, one per skill for the taxonomy row, one for the recommendations. Thirty skills
     * becomes thirty-three queries.
     *
     * <p>The keyword and section-score element collections are deliberately left out. Hibernate
     * refuses to fetch two list collections in one query — {@code MultipleBagFetchException},
     * because two bags in one result set cannot be told apart — and adding them here would trade
     * three queries for a startup failure. They load lazily inside the same transaction.
     */
    @EntityGraph(attributePaths = {"skills", "skills.skill", "recommendations"})
    Optional<Analysis> findDetailByPublicIdAndUserId(UUID publicId, Long userId);

    /**
     * Backs {@code GET /api/analyses} and the dashboard's recent list.
     *
     * <p>Written as JPQL rather than a derived query so the resume label and job title come
     * across in the same statement. Pass an unsorted {@link Pageable} — the ordering is fixed
     * here, and a {@code Sort} on the pageable would be appended to it.
     */
    @Query("""
            select a.publicId as publicId,
                   a.status as status,
                   a.overallScore as overallScore,
                   a.atsScore as atsScore,
                   a.jobMatchScore as jobMatchScore,
                   jd.title as jobTitle,
                   jd.company as company,
                   r.label as resumeLabel,
                   a.createdAt as createdAt,
                   a.completedAt as completedAt
            from Analysis a
              join a.jobDescription jd
              join a.resume r
            where a.user.id = :userId
            order by a.createdAt desc
            """)
    List<AnalysisSummary> findSummariesForUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Score history, oldest first, for the trend chart.
     *
     * <p>Only completed runs: a failed analysis has null scores and would draw a hole in the
     * line. {@code createdAt} is aliased to {@code recordedAt} because the chart cares when the
     * resume was measured, not what the column happens to be called.
     */
    @Query("""
            select a.createdAt as recordedAt,
                   a.overallScore as overallScore,
                   a.atsScore as atsScore,
                   a.jobMatchScore as jobMatchScore
            from Analysis a
            where a.user.id = :userId and a.status = com.resumeiq.analysis.AnalysisStatus.COMPLETED
            order by a.createdAt asc
            """)
    List<ScorePoint> findScoreHistoryForUser(@Param("userId") Long userId);

    /** The three dashboard headline numbers, over completed runs only. */
    @Query("""
            select count(a) as analysisCount,
                   avg(a.overallScore) as averageScore,
                   max(a.overallScore) as bestScore
            from Analysis a
            where a.user.id = :userId and a.status = com.resumeiq.analysis.AnalysisStatus.COMPLETED
            """)
    DashboardTotals findTotalsForUser(@Param("userId") Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, AnalysisStatus status);

    /**
     * Removes every analysis of one resume. Called before deleting the resume itself, because
     * the foreign key is not nullable — see {@code ResumeRepository.deleteByPublicIdAndUserId}.
     *
     * <p>A derived delete loads the entities before removing them, which is exactly what is
     * wanted here: the cascade to skills, recommendations, keywords and section scores runs
     * through JPA rather than being left to the database.
     *
     * <p>Both deletes are {@code @Transactional} because Spring Data's implementation is
     * read-only by default, and a derived delete under {@code FlushMode.MANUAL} removes nothing
     * while still returning a count. See {@link com.resumeiq.resume.ResumeRepository} for the
     * long version.
     */
    @Transactional
    int deleteByResumeId(Long resumeId);

    @Transactional
    int deleteByPublicIdAndUserId(UUID publicId, Long userId);

    boolean existsByResumeId(Long resumeId);

    boolean existsByJobDescriptionId(Long jobDescriptionId);
}
