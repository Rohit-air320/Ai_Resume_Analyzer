package com.resumeiq.recommendation;

import com.resumeiq.analysis.Analysis;
import com.resumeiq.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One piece of actionable advice produced by an analysis.
 *
 * <p>Owned by its analysis, never shared. Advice only means something next to the resume and
 * posting it was derived from, so a recommendation is not a standalone object a user collects —
 * it dies with the run that produced it.
 *
 * <p>{@code detail} is a bounded {@code varchar(2000)} rather than a {@code @Lob}. That is a
 * product constraint expressed in the schema: advice a person can act on is two or three
 * sentences, and a column that permits four kilobytes invites the model to produce an essay
 * nobody reads.
 */
@Entity
@Table(
        name = "recommendations",
        indexes = {
                @Index(name = "ix_recommendations_analysis", columnList = "analysis_id"),
                @Index(name = "ix_recommendations_analysis_type", columnList = "analysis_id, type")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Recommendation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "analysis_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_recommendations_analysis")
    )
    private Analysis analysis;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private RecommendationType type;

    /** One line, imperative, specific. "Quantify the impact of the payments migration." */
    @Column(name = "title", nullable = false, length = 160)
    private String title;

    /** The how and the why, including the section or bullet it applies to. */
    @Column(name = "detail", nullable = false, length = 2000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority;

    /**
     * Position within its type, assigned when the analysis is stored.
     *
     * <p>Persisted rather than derived because the order is the analysis's opinion about what to
     * do first, and it has to survive a page reload. Nothing about a row's id or timestamp
     * carries that meaning.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Optional link for a {@link RecommendationType#LEARNING} item — documentation or a course.
     *
     * <p>Only ever populated from the AI response, so Phase 6 validates the scheme before
     * storing: a URL rendered as a link in the UI is the one field here that could turn a bad
     * model response into a user-facing risk.
     */
    @Column(name = "resource_url", length = 300)
    private String resourceUrl;
}
