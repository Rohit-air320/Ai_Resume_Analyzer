package com.resumeiq.analysis;

import com.resumeiq.common.domain.PublicIdEntity;
import com.resumeiq.jobdescription.JobDescription;
import com.resumeiq.recommendation.Recommendation;
import com.resumeiq.resume.Resume;
import com.resumeiq.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One run of the analyzer: a resume measured against a job description.
 *
 * <p>This is the aggregate root of the results model. It owns its skill rows, recommendations,
 * keywords and section scores — they are created with it, read with it, and deleted with it,
 * which is what {@code cascade = ALL} plus {@code orphanRemoval} says out loud.
 *
 * <p>Two mapping decisions here are worth defending.
 *
 * <p><b>{@code user} is stored even though it is reachable through {@code resume.user}.</b> The
 * most frequent query in the app is "this user's analyses, newest first", and every list, count
 * and aggregate is filtered by owner. Denormalising the owner turns each of those into an index
 * scan on {@code (user_id, created_at)} instead of a join, and it lets ownership be checked in
 * the same {@code where} clause that fetches the row. The cost is an invariant to uphold —
 * {@code user}, {@code resume.user} and {@code jobDescription.user} must be the same person —
 * which Phase 7 enforces at the one point where an analysis is created.
 *
 * <p><b>The children are {@code Set}, not {@code List}.</b> Hibernate cannot fetch two list
 * collections in one query: it throws {@code MultipleBagFetchException}, because two bags in one
 * result set cannot be unpicked. The results page needs skills and recommendations together, so
 * they are sets and a single entity-graph query loads all of it — see
 * {@code AnalysisRepository.findDetailByPublicIdAndUserId}. Display order lives in the DTO, not
 * in the collection.
 */
@Entity
@Table(
        name = "analyses",
        indexes = {
                @Index(name = "ix_analyses_user_created", columnList = "user_id, created_at"),
                @Index(name = "ix_analyses_user_status", columnList = "user_id, status"),
                @Index(name = "ix_analyses_resume", columnList = "resume_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Analysis extends PublicIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_analyses_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, foreignKey = @ForeignKey(name = "fk_analyses_resume"))
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "job_description_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_analyses_job_description")
    )
    private JobDescription jobDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    /**
     * The headline number. Nullable, like every score: the row exists from the moment the run
     * starts, and a score of 0 would be a lie about an analysis that has not finished.
     */
    @Column(name = "overall_score")
    private Integer overallScore;

    /** How well an applicant tracking system can parse and read the resume. */
    @Column(name = "ats_score")
    private Integer atsScore;

    /** How closely the resume matches this specific posting. */
    @Column(name = "job_match_score")
    private Integer jobMatchScore;

    @Column(name = "skills_match_score")
    private Integer skillsMatchScore;

    @Column(name = "keyword_score")
    private Integer keywordScore;

    @Column(name = "experience_score")
    private Integer experienceScore;

    /**
     * The plain-language verdict shown at the top of the results page.
     *
     * <p>No band is stored alongside it. The band is a pure function of the score and the
     * thresholds in the spec, and the frontend already owns that function; a stored copy would
     * be a second source of truth that silently disagrees the day a threshold moves.
     */
    @Lob
    @Column(name = "overall_feedback")
    private String overallFeedback;

    /**
     * The provider's response exactly as it arrived.
     *
     * <p>Kept because the parse can be wrong in ways nobody anticipated. With the original
     * payload the analysis can be re-parsed and the bug reproduced offline; without it, the
     * only way to investigate is to pay for the call again and hope the model repeats itself.
     * It is a text column rather than MySQL's native {@code JSON} type so the same mapping works
     * against H2 in development — nothing queries inside it, so validated JSON buys nothing.
     */
    @Lob
    @Column(name = "raw_response")
    private String rawResponse;

    /** Which model produced this. Two analyses from different models are not comparable. */
    @Column(name = "ai_model", length = 100)
    private String aiModel;

    /** Prompt/parser version, so a scoring change is visible in the data it produced. */
    @Column(name = "analyzer_version", length = 20)
    private String analyzerVersion;

    @Column(name = "processing_ms")
    private Integer processingMs;

    /** Why the run failed, in words a user can act on. Never a stack trace or a provider dump. */
    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Skill-by-skill verdict.
     *
     * <p>Every collection on this entity is written through a helper and read through an
     * unmodifiable view, for two different reasons. Replacing a collection instance that
     * Hibernate manages with {@code orphanRemoval} throws "a collection with
     * cascade=all-delete-orphan was no longer referenced", so there is no setter. And adding to
     * a returned collection directly would set one side of the relationship without the other,
     * so the getter hands back a view rather than the live set.
     */
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    @Builder.Default
    private Set<AnalysisSkill> skills = new LinkedHashSet<>();

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    @Builder.Default
    private Set<Recommendation> recommendations = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "analysis_keywords",
            joinColumns = @JoinColumn(
                    name = "analysis_id",
                    foreignKey = @ForeignKey(name = "fk_analysis_keywords_analysis")
            ),
            indexes = @Index(name = "ix_analysis_keywords_analysis", columnList = "analysis_id")
    )
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    @Builder.Default
    private List<AnalysisKeyword> keywords = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "analysis_section_scores",
            joinColumns = @JoinColumn(
                    name = "analysis_id",
                    foreignKey = @ForeignKey(name = "fk_analysis_section_scores_analysis")
            ),
            indexes = @Index(name = "ix_analysis_section_scores_analysis", columnList = "analysis_id")
    )
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    @Builder.Default
    private List<SectionAssessment> sectionAssessments = new ArrayList<>();

    /**
     * Adds a skill verdict and sets the back-reference in the same call.
     *
     * <p>Setting only one side is the most common bidirectional-mapping bug in JPA: the child
     * looks attached in memory, then inserts with a null {@code analysis_id} — or not at all —
     * because the owning side of the foreign key was never populated.
     */
    public void addSkill(AnalysisSkill skill) {
        skills.add(skill);
        skill.setAnalysis(this);
    }

    public void addRecommendation(Recommendation recommendation) {
        recommendations.add(recommendation);
        recommendation.setAnalysis(this);
    }

    public void addKeyword(AnalysisKeyword keyword) {
        keywords.add(keyword);
    }

    public void addSectionAssessment(SectionAssessment assessment) {
        sectionAssessments.add(assessment);
    }

    public Set<AnalysisSkill> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    public Set<Recommendation> getRecommendations() {
        return Collections.unmodifiableSet(recommendations);
    }

    public List<AnalysisKeyword> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }

    public List<SectionAssessment> getSectionAssessments() {
        return Collections.unmodifiableList(sectionAssessments);
    }

    /** Records a successful run. Keeps the two facts that define "done" in one place. */
    public void markCompleted(Instant finishedAt) {
        this.status = AnalysisStatus.COMPLETED;
        this.completedAt = finishedAt;
        this.failureReason = null;
    }

    /** Records a failure. The message is shown to the user, so callers must sanitise it. */
    public void markFailed(String userSafeReason, Instant finishedAt) {
        this.status = AnalysisStatus.FAILED;
        this.completedAt = finishedAt;
        this.failureReason = userSafeReason;
    }
}
