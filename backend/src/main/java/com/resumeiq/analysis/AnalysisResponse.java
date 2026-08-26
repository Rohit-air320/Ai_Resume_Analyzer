package com.resumeiq.analysis;

import com.resumeiq.recommendation.Priority;
import com.resumeiq.recommendation.Recommendation;
import com.resumeiq.recommendation.RecommendationType;
import com.resumeiq.skill.Skill;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A finished analysis, as the client sees it.
 *
 * <h2>Built from the stored row and nothing else</h2>
 *
 * <p>Every field here comes off the {@link Analysis} aggregate. Nothing is recomputed, no skill
 * catalogue is consulted and no model is called, which is what makes {@code POST /api/analyses} and
 * {@code GET /api/analyses/{id}} return the same document — the create response is this record built
 * from the row that was just written, not a second rendering of the outcome that produced it. A client
 * can therefore treat the two endpoints interchangeably, and a bug in the mapping shows up in both
 * rather than in whichever one has fewer tests.
 *
 * <h2>The field names are the spec's field names</h2>
 *
 * <p>{@code detectedSkills}, {@code missingSkills}, {@code matchingKeywords}, {@code suggestedKeywords},
 * {@code sectionScores}, {@code improvements}, {@code recommendedProjects},
 * {@code learningRecommendations}. The same words name the keys the prompt asks the model for, the
 * columns they land in and the fields the UI reads, so a conversation about "missing skills" means one
 * thing from the prompt to the chart. Renaming a concept at a layer boundary is how a codebase ends up
 * with three words for one idea and a mapping class to translate between them.
 *
 * <h2>Every list is sorted here</h2>
 *
 * <p>This matters more than it looks. A {@code @OneToMany} without {@code @OrderBy} and an
 * {@code @ElementCollection} without {@code @OrderColumn} come back in whatever order the database
 * chose, so a response built straight from the collections would order its skills one way on create —
 * insertion order, still in memory — and another way after a re-read. Sorting on the way out makes the
 * document deterministic, which is the difference between an API that can be asserted on and one where
 * every test has to be order-insensitive.
 *
 * @param scoreBreakdown why each number is what it is, read from the stored notes rather than derived
 * @param provenance     which writer produced the prose, and how long the run took
 * @param failureReason  set only on a failed analysis, and safe to show — see {@link Analysis}
 */
@Schema(description = "A completed analysis: scores, findings and advice")
public record AnalysisResponse(
        UUID id,
        AnalysisStatus status,
        Target target,
        Integer overallScore,
        Integer atsScore,
        Integer jobMatchScore,
        Integer skillsMatchScore,
        Integer keywordScore,
        Integer experienceScore,
        List<ScoreReason> scoreBreakdown,
        String overallFeedback,
        List<SkillFinding> detectedSkills,
        List<SkillFinding> missingSkills,
        List<String> matchingKeywords,
        List<String> missingKeywords,
        List<KeywordSuggestion> suggestedKeywords,
        List<SectionScore> sectionScores,
        List<Advice> improvements,
        List<Advice> recommendedProjects,
        List<Advice> learningRecommendations,
        Provenance provenance,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {

    /**
     * Maps a stored analysis.
     *
     * <p>Must be called with the persistence session open: the resume and the posting are lazy
     * associations, and the two labels they contribute are the only reason this record needs them.
     * That is the same contract {@code UserProfileResponse.from} works under, and the alternative —
     * passing five loose strings in — moves the lazy-loading decision to every call site instead of
     * keeping it in one documented place.
     */
    public static AnalysisResponse from(Analysis analysis) {
        return new AnalysisResponse(
                analysis.getPublicId(),
                analysis.getStatus(),
                Target.from(analysis),
                analysis.getOverallScore(),
                analysis.getAtsScore(),
                analysis.getJobMatchScore(),
                analysis.getSkillsMatchScore(),
                analysis.getKeywordScore(),
                analysis.getExperienceScore(),
                reasons(analysis),
                analysis.getOverallFeedback(),
                skills(analysis, false),
                skills(analysis, true),
                keywords(analysis, KeywordKind.MATCHED),
                keywords(analysis, KeywordKind.ABSENT),
                suggestions(analysis),
                sections(analysis),
                advice(analysis, RecommendationType.IMPROVEMENT),
                advice(analysis, RecommendationType.PROJECT),
                advice(analysis, RecommendationType.LEARNING),
                Provenance.from(analysis),
                analysis.getFailureReason(),
                analysis.getCreatedAt(),
                analysis.getCompletedAt());
    }

    private static List<ScoreReason> reasons(Analysis analysis) {
        // Stored order is not guaranteed, and unlike the other collections there is no natural key to
        // sort by — a breakdown reads as a sequence, "required skills, then preferred, then density".
        // Sorting scored notes before context notes recovers most of that intent and is stable.
        return analysis.getScoreNotes().stream()
                .sorted(Comparator.comparing(ScoreExplanation::isScored).reversed()
                        .thenComparing(ScoreExplanation::getLabel))
                .map(note -> new ScoreReason(note.getLabel(), note.getEarned(), note.getOutOf(),
                        note.getComment()))
                .toList();
    }

    /**
     * Skills, split by whether they are a gap.
     *
     * <p>One table, one filter, two lists. {@code detectedSkills} is what the resume demonstrates —
     * including skills the posting never asked for, because "you also have these" is worth showing —
     * and {@code missingSkills} is the skill-gap analysis. Ordered by importance so a critical gap is
     * never the ninth row of a list.
     */
    private static List<SkillFinding> skills(Analysis analysis, boolean gaps) {
        return analysis.getSkills().stream()
                .filter(skill -> (skill.getStatus() == SkillStatus.MISSING) == gaps)
                .sorted(Comparator.comparing(AnalysisSkill::getImportance)
                        .thenComparing(AnalysisSkill::label))
                .map(SkillFinding::from)
                .toList();
    }

    private static List<String> keywords(Analysis analysis, KeywordKind kind) {
        return analysis.getKeywords().stream()
                .filter(keyword -> keyword.getKind() == kind)
                .map(AnalysisKeyword::getTerm)
                .sorted()
                .toList();
    }

    private static List<KeywordSuggestion> suggestions(Analysis analysis) {
        return analysis.getKeywords().stream()
                .filter(keyword -> keyword.getKind() == KeywordKind.SUGGESTED)
                .sorted(Comparator.comparing(AnalysisKeyword::getTerm))
                .map(keyword -> new KeywordSuggestion(keyword.getTerm(), keyword.getPlacement()))
                .toList();
    }

    /** Sections in document order — the order somebody reads a resume in, not score order. */
    private static List<SectionScore> sections(Analysis analysis) {
        return analysis.getSectionAssessments().stream()
                .sorted(Comparator.comparing(SectionAssessment::getSection))
                .map(section -> new SectionScore(section.getSection(), section.getScore(),
                        section.getNote()))
                .toList();
    }

    private static List<Advice> advice(Analysis analysis, RecommendationType type) {
        return analysis.getRecommendations().stream()
                .filter(recommendation -> recommendation.getType() == type)
                .sorted(Comparator.comparingInt(Recommendation::getDisplayOrder))
                .map(Advice::from)
                .toList();
    }

    /**
     * What was analysed.
     *
     * <p>Identifiers as well as labels, so the UI can link back to the resume and the posting without
     * a second lookup — and so "analyse this resume again against the same job" is one request the
     * client can build from a response it already has.
     */
    @Schema(description = "The resume and posting this analysis compared")
    public record Target(
            UUID resumeId,
            String resumeLabel,
            UUID jobDescriptionId,
            String jobTitle,
            String company
    ) {
        static Target from(Analysis analysis) {
            return new Target(
                    analysis.getResume().getPublicId(),
                    analysis.getResume().getLabel(),
                    analysis.getJobDescription().getPublicId(),
                    analysis.getJobDescription().getTitle(),
                    analysis.getJobDescription().getCompany());
        }
    }

    /**
     * One line of the arithmetic.
     *
     * <p>{@code outOf} is zero for a note that carries context rather than points, which the UI reads
     * as "show the comment, not a fraction".
     */
    @Schema(description = "Why one component scored what it did")
    public record ScoreReason(String label, int earned, int outOf, String comment) {
    }

    /**
     * A skill and the verdict on it.
     *
     * @param slug       the catalogue slug, or null for a skill read out of the posting that the
     *                   catalogue does not know yet — the UI needs it to link to a skill page, and
     *                   null is the honest answer rather than a slug invented on the way out
     * @param note       why this verdict: the evidence for a skill the resume shows, or what the
     *                   posting asked for in the case of a gap
     */
    @Schema(description = "A skill, its verdict, and why")
    public record SkillFinding(
            String name,
            String slug,
            SkillStatus status,
            SkillImportance importance,
            String note
    ) {
        static SkillFinding from(AnalysisSkill skill) {
            Skill catalogued = skill.getSkill();
            return new SkillFinding(
                    skill.label(),
                    catalogued == null ? null : catalogued.getSlug(),
                    skill.getStatus(),
                    skill.getImportance(),
                    skill.getEvidence());
        }
    }

    /**
     * A term to add, and where.
     *
     * <p>The placement is not decoration and is never empty: a term with no honest answer to "where
     * would this go" is keyword stuffing, and it is dropped before it reaches a column.
     */
    @Schema(description = "A keyword worth adding, with the place it belongs")
    public record KeywordSuggestion(String term, String placement) {
    }

    @Schema(description = "How one resume section scored")
    public record SectionScore(ResumeSection section, int score, String note) {
    }

    /** An improvement, a project idea or a learning topic. Same shape, three lists. */
    @Schema(description = "One piece of advice")
    public record Advice(String title, String detail, Priority priority, String resourceUrl) {
        static Advice from(Recommendation recommendation) {
            return new Advice(recommendation.getTitle(), recommendation.getDetail(),
                    recommendation.getPriority(), recommendation.getResourceUrl());
        }
    }

    /**
     * Where the words came from.
     *
     * <p>Surfaced deliberately. A user reading advice should know whether a model read their bullet
     * points or whether the suggestions were derived from the structural findings, because the two
     * deserve different amounts of trust — and because an analysis run while the provider was down is
     * still a useful analysis rather than a failure to hide.
     *
     * @param writtenBy    the model name, or a description of the offline writer. Never a key
     * @param modelWritten false when the offline writer produced the prose
     * @param processingMs how long the run took, which is the number to watch when a provider slows
     */
    @Schema(description = "Which writer produced the advice")
    public record Provenance(
            String writtenBy,
            boolean modelWritten,
            String analyzerVersion,
            Integer processingMs
    ) {
        static Provenance from(Analysis analysis) {
            String source = analysis.getAiModel();
            return new Provenance(source, AnalysisOutcome.isModelWritten(source),
                    analysis.getAnalyzerVersion(), analysis.getProcessingMs());
        }
    }
}
