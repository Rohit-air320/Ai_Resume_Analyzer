package com.resumeiq.recommendation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** Re-running an analysis replaces its advice; writable transaction required. */
    @Transactional
    int deleteByAnalysis_Id(Long analysisId);
}
