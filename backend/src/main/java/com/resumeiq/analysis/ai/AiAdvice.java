package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.recommendation.Priority;

import java.util.List;
import java.util.Map;

/**
 * The written half of an analysis: everything that is words rather than numbers.
 *
 * <p>Produced by an {@link AdviceSource}. There are two of those — one that calls a model and one that
 * writes from the findings in code — and they return this same type, so nothing downstream knows or
 * cares which one ran. That is what makes the offline path a real mode rather than a degraded stub:
 * Phase 7 persists this record identically either way, and the API response has the same shape.
 *
 * <p>Every list here is advice. None of it is a fact. The facts were computed before this record
 * existed, and {@link AdviceSanitiser} has already dropped anything in here that tried to introduce a
 * new one — a skill the posting never asked for, a gap that is not a gap, a score that disagrees with
 * the arithmetic.
 *
 * @param overallFeedback       the spec's "overall actionable feedback": a short paragraph a person
 *                              can act on, naming the single most valuable next change
 * @param improvements          concrete edits to make to the resume
 * @param skillGaps             a sentence about each real gap — why this posting cares and what
 *                              closing it honestly would look like. Keyed by slug, never free-text
 *                              skill names, so it can only speak about computed gaps.
 * @param recommendedProjects   things to build that would evidence a missing skill truthfully
 * @param learningRecommendations topics to learn, highest-value first
 * @param suggestedKeywords     terms from the posting and where they would legitimately fit. Each one
 *                              carries a placement, because a term without a place to put it is a
 *                              keyword-stuffing instruction.
 * @param sectionNotes          a remark per section, to sit beside the computed section score
 * @param modelScores           what the model thought the scores were, when a model ran. A logged
 *                              second opinion and nothing more — see {@link #modelScores()}.
 * @param source                which writer produced this, for the log line and the analysis record
 */
public record AiAdvice(
        String overallFeedback,
        List<Improvement> improvements,
        List<GapNote> skillGaps,
        List<ProjectIdea> recommendedProjects,
        List<LearningTopic> learningRecommendations,
        List<KeywordPlacement> suggestedKeywords,
        List<SectionNote> sectionNotes,
        Map<String, Integer> modelScores,
        String source
) {

    public AiAdvice {
        improvements = List.copyOf(improvements);
        skillGaps = List.copyOf(skillGaps);
        recommendedProjects = List.copyOf(recommendedProjects);
        learningRecommendations = List.copyOf(learningRecommendations);
        suggestedKeywords = List.copyOf(suggestedKeywords);
        sectionNotes = List.copyOf(sectionNotes);
        modelScores = Map.copyOf(modelScores);
    }

    /** Nothing written. Used as the starting point when a response parsed to an empty object. */
    public static AiAdvice empty(String source) {
        return new AiAdvice("", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), source);
    }

    /** Records which writer produced this, once the source is known. */
    public AiAdvice from(String newSource) {
        return new AiAdvice(overallFeedback, improvements, skillGaps, recommendedProjects,
                learningRecommendations, suggestedKeywords, sectionNotes, modelScores, newSource);
    }

    /** True when there is nothing here worth persisting. */
    public boolean isEmpty() {
        return (overallFeedback == null || overallFeedback.isBlank())
                && improvements.isEmpty() && skillGaps.isEmpty() && recommendedProjects.isEmpty()
                && learningRecommendations.isEmpty() && suggestedKeywords.isEmpty();
    }

    /** How many pieces of advice this carries, for the log line. */
    public int itemCount() {
        return improvements.size() + skillGaps.size() + recommendedProjects.size()
                + learningRecommendations.size() + suggestedKeywords.size();
    }

    /**
     * One edit to make to the resume.
     *
     * @param title    what to do, imperative and specific — "Quantify the outcome of the migration
     *                 bullet", not "improve your experience section"
     * @param detail   how to do it, and why it is worth doing
     * @param priority urgency, which the UI shows as a badge
     * @param section  which section it applies to, so the UI can file it. Null when it is about the
     *                 resume as a whole.
     */
    public record Improvement(String title, String detail, Priority priority, ResumeSection section) {
    }

    /**
     * A sentence about one computed gap.
     *
     * @param slug     the catalogue slug of the missing skill. A slug rather than a name because this
     *                 has to be matched against the computed gap list, and a model asked for a name
     *                 will eventually write "Amazon Web Services (AWS)" for a gap recorded as "aws".
     * @param detail   why this posting cares, and what closing it honestly looks like
     * @param priority how urgent, which should track the skill's importance in the posting
     */
    public record GapNote(String slug, String detail, Priority priority) {
    }

    /**
     * Something to build.
     *
     * @param title      the project, concrete enough to start — "A rate-limited URL shortener with
     *                   Redis-backed counters"
     * @param detail     what it would demonstrate and roughly what it involves
     * @param skillSlugs which gaps it would close. Validated against the computed gaps, so a project
     *                   cannot claim to evidence a skill the posting never asked for.
     */
    public record ProjectIdea(String title, String detail, List<String> skillSlugs) {

        public ProjectIdea {
            skillSlugs = List.copyOf(skillSlugs);
        }
    }

    /**
     * Something to learn.
     *
     * @param title       the topic
     * @param detail      why it matters for this posting and what "learned enough" looks like
     * @param resourceUrl an optional link. Kept optional on purpose: a made-up URL is worse than no
     *                    URL, and the sanitiser drops anything that is not a plausible https link.
     * @param priority    urgency
     */
    public record LearningTopic(String title, String detail, String resourceUrl, Priority priority) {
    }

    /**
     * A term from the posting, and the honest place for it.
     *
     * @param term      the term, which must be one the posting actually used
     * @param placement where it would truthfully belong in this resume. Required. This field is the
     *                  entire difference between keyword advice and keyword stuffing: with a placement
     *                  the advice is "you did this work, describe it in their words", and without one
     *                  it is a list of words to paste in.
     */
    public record KeywordPlacement(String term, String placement) {
    }

    /**
     * A remark about one section, shown beside its computed score.
     *
     * @param section the section
     * @param note    the remark. The score is not taken from the model; this is the sentence next to it.
     */
    public record SectionNote(ResumeSection section, String note) {
    }
}
