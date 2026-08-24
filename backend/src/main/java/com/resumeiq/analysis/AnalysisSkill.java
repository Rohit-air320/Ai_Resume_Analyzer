package com.resumeiq.analysis;

import com.resumeiq.common.domain.BaseEntity;
import com.resumeiq.skill.Skill;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One skill's verdict inside one analysis: the join between {@link Analysis} and
 * {@link Skill}, with the three columns that make it useful.
 *
 * <p>This is the table that a document database would force into an embedded array, and the
 * clearest argument for the relational model here. "Which skills has this user been missing
 * most often across every analysis?" — the question the skill-gap page is built on — is a
 * {@code group by} over this table. Embedded in a document it would be an aggregation over
 * arrays of arrays.
 *
 * <p>{@code skill} is nullable, on purpose. The AI can name something the taxonomy has never
 * heard of, and throwing that away would be the worst possible outcome: the user would see a
 * gap list quietly missing the one requirement that matters. Unmatched mentions are kept with
 * {@code rawName} set, count towards the analysis, and are what tells us which skills to add to
 * the taxonomy next.
 */
@Entity
@Table(
        name = "analysis_skills",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_skills_analysis_raw",
                columnNames = {"analysis_id", "raw_name"}
        ),
        indexes = {
                @Index(name = "ix_analysis_skills_analysis", columnList = "analysis_id"),
                @Index(name = "ix_analysis_skills_skill_status", columnList = "skill_id, status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisSkill extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "analysis_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_analysis_skills_analysis")
    )
    private Analysis analysis;

    /**
     * The canonical skill, when the mention could be resolved. Null when it could not — see the
     * class comment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", foreignKey = @ForeignKey(name = "fk_analysis_skills_skill"))
    private Skill skill;

    /**
     * Exactly what the job description or the AI called this skill.
     *
     * <p>Kept even when {@link #skill} resolves, because it is the honest record of the input
     * and because showing the user "Spring-Boot" when the posting said "Spring-Boot" reads as
     * accurate, while silently rewriting it to "Spring Boot" reads as a bug.
     *
     * <p>The unique constraint is on {@code (analysis_id, raw_name)} rather than
     * {@code (analysis_id, skill_id)} deliberately: MySQL permits unlimited NULLs in a unique
     * index, so a constraint on the nullable skill id would enforce nothing at all for exactly
     * the unmatched rows most likely to be duplicated.
     */
    @Column(name = "raw_name", nullable = false, length = 80)
    private String rawName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SkillStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 20)
    private SkillImportance importance;

    /**
     * Where the resume evidences this skill, quoted or paraphrased.
     *
     * <p>This column is what keeps the advice truthful. A suggestion to strengthen a skill is
     * only legitimate if the resume already supports it somewhere, and holding the evidence
     * makes that checkable rather than a claim the model makes about itself.
     */
    @Column(name = "evidence", length = 400)
    private String evidence;

    /** True when this row represents a gap the user should act on. */
    public boolean isGap() {
        return status == SkillStatus.MISSING || status == SkillStatus.PARTIAL;
    }

    /** Display name: the taxonomy's spelling when known, otherwise the raw mention. */
    public String label() {
        return skill != null ? skill.getDisplayName() : rawName;
    }
}
