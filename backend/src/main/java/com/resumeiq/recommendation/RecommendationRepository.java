package com.resumeiq.recommendation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Recommendations, read either for one analysis or across a user's history.
 *
 * <p>There is no user column on this table — a recommendation's owner is its analysis's owner —
 * so the ownership filter walks the association: {@code findByAnalysis_User_Id...}. The
 * underscores are explicit at every step on purpose. Written as {@code findByAnalysisUserId},
 * the parser has to guess where the property boundary falls, and a future field named
 * {@code analysisUser} would silently change which query this method means.
 */
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    /** Everything one analysis produced, grouped by type and in the order it was written. */
    List<Recommendation> findByAnalysis_IdOrderByTypeAscDisplayOrderAsc(Long analysisId);

    /** Ownership-scoped read for {@code GET /api/analyses/{id}} rendered from the public id. */
    List<Recommendation> findByAnalysis_PublicIdAndAnalysis_User_IdOrderByTypeAscDisplayOrderAsc(
            UUID analysisPublicId, Long userId);

    /**
     * Backs {@code GET /api/recommendations}: the latest advice across every analysis, optionally
     * narrowed to one type. Pass a {@link Pageable} — this list grows with every run, and an
     * unbounded query here would eventually load a user's entire history to render one panel.
     */
    List<Recommendation> findByAnalysis_User_IdAndTypeOrderByCreatedAtDesc(
            Long userId, RecommendationType type, Pageable pageable);

    List<Recommendation> findByAnalysis_User_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByAnalysis_User_IdAndType(Long userId, RecommendationType type);

    /**
     * The recommendations feed: every piece of advice across the account, newest first.
     *
     * <p>A projection rather than the entity list above, because the feed shows which job each
     * suggestion came from. Reading that off the entities means {@code getAnalysis()
     * .getJobDescription().getTitle()} on every row — two lazy loads each, and no entity graph helps
     * once the collection is already in memory. The join does it in one statement.
     */
    @Query("""
            select a.publicId as analysisId,
                   r.type as type,
                   r.title as title,
                   r.detail as detail,
                   r.priority as priority,
                   r.resourceUrl as resourceUrl,
                   jd.title as jobTitle,
                   r.createdAt as createdAt
            from Recommendation r
              join r.analysis a
              join a.jobDescription jd
            where a.user.id = :userId
            order by r.createdAt desc, r.type asc, r.displayOrder asc
            """)
    List<RecommendationFeedItem> findFeedForUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * The same feed, narrowed to one type.
     *
     * <p>Two methods rather than one with {@code (:type is null or r.type = :type)}. That trick
     * works, and it costs the database its index: a predicate wrapped in {@code or} against a
     * parameter cannot be planned as an equality, so the filtered call — the common one, since the
     * UI has a tab per type — degrades to the same scan as the unfiltered one. Two queries, two
     * plans, one extra branch in the service.
     */
    @Query("""
            select a.publicId as analysisId,
                   r.type as type,
                   r.title as title,
                   r.detail as detail,
                   r.priority as priority,
                   r.resourceUrl as resourceUrl,
                   jd.title as jobTitle,
                   r.createdAt as createdAt
            from Recommendation r
              join r.analysis a
              join a.jobDescription jd
            where a.user.id = :userId and r.type = :type
            order by r.createdAt desc, r.displayOrder asc
            """)
    List<RecommendationFeedItem> findFeedForUserByType(@Param("userId") Long userId,
                                                       @Param("type") RecommendationType type,
                                                       Pageable pageable);

    /** Re-running an analysis replaces its advice; writable transaction required. */
    @Transactional
    int deleteByAnalysis_Id(Long analysisId);
}
