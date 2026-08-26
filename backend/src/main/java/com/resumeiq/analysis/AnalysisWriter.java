package com.resumeiq.analysis;

import com.resumeiq.analysis.ai.AiAdvice;
import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.analysis.engine.KeywordVerdict;
import com.resumeiq.analysis.engine.ScoreCard;
import com.resumeiq.analysis.engine.ScoreNote;
import com.resumeiq.analysis.engine.SectionReview;
import com.resumeiq.analysis.engine.SkillVerdict;
import com.resumeiq.common.domain.Timestamps;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.jobdescription.JobDescription;
import com.resumeiq.recommendation.Priority;
import com.resumeiq.recommendation.Recommendation;
import com.resumeiq.recommendation.RecommendationType;
import com.resumeiq.resume.Resume;
import com.resumeiq.skill.Skill;
import com.resumeiq.skill.SkillRepository;
import com.resumeiq.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes a finished analysis to MySQL.
 *
 * <h2>Why this is its own bean</h2>
 *
 * <p>It could be a {@code @Transactional} method on {@link AnalysisService}, and that would be a bug.
 * Spring's transaction support is a proxy around the bean, so a method calling another method on
 * <em>its own</em> class goes straight to the implementation and the annotation is simply not there at
 * run time — the code reads as transactional, passes review, and writes without a transaction. This
 * project has already shipped that defect once, in {@code SkillCatalogSeeder}, and now has a check in
 * {@code tools/verify_sources.py} that looks for it.
 *
 * <p>The separation earns its keep twice over, because it is also what keeps the provider call outside
 * the write transaction. The service computes — a network round trip that can take a minute and fail —
 * and only then calls in here, so a slow model never holds a database transaction open across it.
 *
 * <h2>Every string is fitted to its column</h2>
 *
 * <p>All the prose on an analysis was written by a language model into columns with bounds. A model
 * that writes 161 characters into {@code recommendations.title} throws a {@code DataException} at
 * flush and turns a correct analysis into a 500, so every assignment below goes through
 * {@link Fitted}. That is repetitive on purpose: the alternative is remembering which of eleven
 * strings is the unbounded one.
 *
 * <h2>What is deliberately not stored</h2>
 *
 * <p>{@code analyses.raw_response} stays null. The column is there for debugging a provider, and
 * filling it would put a second verbatim copy of model output about somebody's resume in the database,
 * to be read by nobody: the parts of the response that matter are already stored as rows, and the
 * parts that did not survive validation are the parts we decided not to stand behind. The spec is
 * explicit that resume content is not to be logged or copied around, and a debugging convenience is
 * not a good enough reason to make an exception that never expires.
 */
@Component
public class AnalysisWriter {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWriter.class);

    /** Column widths, named here so the fitting below reads as arithmetic against the schema. */
    private static final int MAX_SKILL_NAME = 80;
    private static final int MAX_EVIDENCE = 400;
    private static final int MAX_TERM = 120;
    private static final int MAX_PLACEMENT = 300;
    private static final int MAX_SECTION_NOTE = 400;
    private static final int MAX_TITLE = 160;
    private static final int MAX_DETAIL = 2000;
    private static final int MAX_URL = 300;
    private static final int MAX_MODEL_NAME = 100;
    private static final int MAX_VERSION = 20;

    /**
     * A bound on the stored feedback.
     *
     * <p>The column is a {@code LONGTEXT} and needs no limit, which is exactly why one is applied
     * here: "the database will take it" is how a runaway generation ends up as a megabyte of text
     * shipped to a browser on every page load.
     */
    private static final int MAX_FEEDBACK = 4_000;

    private final AnalysisRepository analyses;
    private final SkillRepository skills;
    private final String analyzerVersion;

    public AnalysisWriter(AnalysisRepository analyses, SkillRepository skills,
                          ResumeIqProperties properties) {
        this.analyses = analyses;
        this.skills = skills;
        this.analyzerVersion = Fitted.to(properties.app().version(), MAX_VERSION);
    }

    /**
     * Persists an outcome as one analysis and its children.
     *
     * <p>The three entities arrive detached, loaded by the caller in an earlier read-only transaction.
     * That is safe and worth knowing why: none of the three associations cascades, so Hibernate writes
     * the foreign keys from their identifiers and never tries to reattach them. It is also useful —
     * they are loaded objects rather than lazy proxies, so mapping the response afterwards reads the
     * resume label and the job title without another select.
     *
     * @param owner     the caller, already checked to own both documents
     * @param outcome   the computed facts and the validated advice
     * @param elapsedMs how long the whole run took, provider call included
     * @return the saved analysis, still managed, with every child attached
     */
    @Transactional
    public Analysis save(User owner, Resume resume, JobDescription posting,
                         AnalysisOutcome outcome, long elapsedMs) {

        AnalysisFacts facts = outcome.facts();
        AiAdvice advice = outcome.advice();
        ScoreCard scores = facts.scores();

        Analysis analysis = Analysis.builder()
                .user(owner)
                .resume(resume)
                .jobDescription(posting)
                .status(AnalysisStatus.PROCESSING)
                .overallScore(scores.overall())
                .atsScore(scores.ats())
                .jobMatchScore(scores.jobMatch())
                .skillsMatchScore(scores.skillsMatch())
                .keywordScore(scores.keyword())
                .experienceScore(scores.experience())
                .overallFeedback(Fitted.to(advice.overallFeedback(), MAX_FEEDBACK))
                .aiModel(Fitted.to(outcome.adviceSource(), MAX_MODEL_NAME))
                .analyzerVersion(analyzerVersion)
                .processingMs(millis(elapsedMs))
                .build();

        addScoreNotes(analysis, scores.notes());
        addSkills(analysis, facts, advice);
        addKeywords(analysis, facts, advice);
        addSections(analysis, facts, advice);
        addRecommendations(analysis, advice);

        analysis.markCompleted(Timestamps.now());
        Analysis saved = analyses.save(analysis);

        // Identifiers and counts. Neither document, nor the feedback, nor any suggestion is logged.
        log.info("Stored analysis {} for user {}: overall {}, {} skills, {} keywords, {} suggestions, "
                        + "advice by {} in {} ms",
                saved.getPublicId(), owner.getPublicId(), scores.overall(),
                saved.getSkills().size(), saved.getKeywords().size(),
                saved.getRecommendations().size(), outcome.adviceSource(), elapsedMs);
        return saved;
    }

    /**
     * Records a run that failed.
     *
     * <p>Nothing in the synchronous path reaches this — a provider failure ends at the offline writer
     * and still produces a complete analysis, and a bad request is refused before a row exists. It is
     * here for the two callers that do need it: the engine throwing something nobody anticipated,
     * where the user deserves a history entry rather than a request that vanished, and the queued
     * version of this endpoint, where by the time the run fails there is no request left to answer.
     *
     * @param reason shown to the user, so it must not carry a stack trace, a provider message or a
     *               fragment of either document
     */
    @Transactional
    public Analysis saveFailure(User owner, Resume resume, JobDescription posting, String reason) {
        Analysis analysis = Analysis.builder()
                .user(owner)
                .resume(resume)
                .jobDescription(posting)
                .status(AnalysisStatus.PROCESSING)
                .analyzerVersion(analyzerVersion)
                .build();
        analysis.markFailed(Fitted.to(reason, 300), Timestamps.now());

        Analysis saved = analyses.save(analysis);
        log.warn("Stored failed analysis {} for user {}: {}",
                saved.getPublicId(), owner.getPublicId(), reason);
        return saved;
    }

    private void addScoreNotes(Analysis analysis, List<ScoreNote> notes) {
        for (ScoreNote note : notes) {
            analysis.addScoreNote(new ScoreExplanation(
                    Fitted.required(note.label(), ScoreExplanation.MAX_LABEL),
                    note.earned(),
                    note.outOf(),
                    Fitted.to(note.comment(), ScoreExplanation.MAX_COMMENT)));
        }
    }

    /**
     * One row per skill, demanded and extra alike.
     *
     * <p>Extras — skills the resume shows that this posting never asked for — are stored because
     * "detected skills" is one of the things the product promises, and a resume's Kubernetes
     * experience is worth showing even when this particular job is silent about it. They carry
     * {@code NICE_TO_HAVE} because no posting ranked them, which is the honest reading of an
     * importance this posting never expressed.
     *
     * <p>The model's gap notes are appended to the engine's evidence rather than replacing it. The
     * engine's sentence is a fact about the two documents ("the posting asks for it under Required
     * skills"); the model's is advice about the gap. Both belong next to the skill, and the column's
     * meaning stays "what we can tell you about this skill for this resume".
     *
     * <p>Deduplicated by name before anything is added, because {@code analysis_skills} is unique on
     * {@code (analysis_id, raw_name)} and a duplicate would surface as an integrity violation on
     * flush — a 500 at the very end of a successful analysis, which is the worst place to find out.
     */
    private void addSkills(Analysis analysis, AnalysisFacts facts, AiAdvice advice) {
        List<SkillVerdict> verdicts = facts.skills().all();
        Map<String, Skill> catalogue = catalogueFor(verdicts);
        Map<String, String> gapNotes = advice.skillGaps().stream()
                .filter(note -> note.detail() != null && !note.detail().isBlank())
                .collect(Collectors.toMap(AiAdvice.GapNote::slug, AiAdvice.GapNote::detail,
                        (first, second) -> first));

        Set<String> seen = new HashSet<>();
        for (SkillVerdict verdict : verdicts) {
            String name = Fitted.required(verdict.displayName(), MAX_SKILL_NAME);
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            analysis.addSkill(AnalysisSkill.builder()
                    .skill(catalogue.get(verdict.slug()))
                    .rawName(name)
                    .status(verdict.status())
                    .importance(verdict.importance())
                    .evidence(evidenceFor(verdict, gapNotes.get(verdict.slug())))
                    .build());
        }
    }

    /**
     * The catalogue rows for these slugs, in one query.
     *
     * <p>A skill the catalogue does not have is stored with a null {@code skill_id} and its name in
     * {@code raw_name}, which is why that column exists. Refusing to store it would lose a real
     * finding to a gap in a seed file, and inventing a catalogue row from a posting would let anybody
     * with a text box write to a shared table.
     */
    private Map<String, Skill> catalogueFor(List<SkillVerdict> verdicts) {
        Set<String> slugs = verdicts.stream().map(SkillVerdict::slug).collect(Collectors.toSet());
        if (slugs.isEmpty()) {
            return Map.of();
        }
        return skills.findBySlugIn(slugs).stream()
                .collect(Collectors.toMap(Skill::getSlug, Function.identity(),
                        (first, second) -> first, HashMap::new));
    }

    private static String evidenceFor(SkillVerdict verdict, String gapNote) {
        String evidence = verdict.evidence() == null ? "" : verdict.evidence().strip();
        if (gapNote == null) {
            return Fitted.to(evidence, MAX_EVIDENCE);
        }
        return Fitted.to(evidence.isEmpty() ? gapNote : evidence + " " + gapNote.strip(),
                MAX_EVIDENCE);
    }

    /**
     * Matched, absent and suggested terms in one collection.
     *
     * <p>A term can legitimately appear twice with different kinds, and that is the design rather
     * than a leak: {@code ABSENT} is every term the posting leans on that the resume does not use,
     * and {@code SUGGESTED} is the smaller set of those the advice can name a truthful place for. The
     * gap between the two lists is the point — it is the difference between "this word is missing"
     * and "here is where this word honestly belongs", and collapsing them would either hide terms or
     * imply a placement nobody wrote.
     */
    private void addKeywords(Analysis analysis, AnalysisFacts facts, AiAdvice advice) {
        for (KeywordVerdict verdict : facts.matchedKeywords()) {
            analysis.addKeyword(new AnalysisKeyword(KeywordKind.MATCHED,
                    Fitted.required(verdict.term(), MAX_TERM), null));
        }
        for (KeywordVerdict verdict : facts.absentKeywords()) {
            analysis.addKeyword(new AnalysisKeyword(KeywordKind.ABSENT,
                    Fitted.required(verdict.term(), MAX_TERM), null));
        }
        for (AiAdvice.KeywordPlacement suggestion : advice.suggestedKeywords()) {
            analysis.addKeyword(new AnalysisKeyword(KeywordKind.SUGGESTED,
                    Fitted.required(suggestion.term(), MAX_TERM),
                    Fitted.to(suggestion.placement(), MAX_PLACEMENT)));
        }
    }

    /**
     * One assessment per resume section, with the better of the two notes.
     *
     * <p>The score is always the engine's — it is computed from whether the section exists, how long
     * it is and what it contains. The note prefers the model's, because "your summary names the role
     * but not the evidence" reads better than "summary present, 24 words", and falls back to the
     * engine's when the model said nothing about that section. The score and the sentence beside it
     * therefore come from different places, which is the whole arrangement in miniature.
     */
    private void addSections(Analysis analysis, AnalysisFacts facts, AiAdvice advice) {
        Map<ResumeSection, String> written = new LinkedHashMap<>();
        for (AiAdvice.SectionNote note : advice.sectionNotes()) {
            if (note.note() != null && !note.note().isBlank()) {
                written.putIfAbsent(note.section(), note.note());
            }
        }
        for (SectionReview review : facts.sections()) {
            String note = written.getOrDefault(review.section(), review.note());
            analysis.addSectionAssessment(new SectionAssessment(review.section(), review.score(),
                    Fitted.to(note, MAX_SECTION_NOTE)));
        }
    }

    /**
     * The advice, as rows on the recommendations table.
     *
     * <p>Four types from four lists, each numbered in the order the writer produced them so a client
     * can render "do this first" without re-sorting by priority — the order already encodes what the
     * advice thought mattered most.
     *
     * <p>Keyword suggestions are written here as well as onto {@code analysis_keywords}, and the
     * duplication is deliberate. The two tables answer two different questions: one renders a single
     * analysis, where matched, absent and suggested terms belong side by side, and the other is a
     * cross-analysis feed of everything the user has been advised to do. A feed that silently omitted
     * keyword advice would be wrong, and joining across the two shapes at query time to avoid storing
     * a hundred short rows would be optimising the wrong thing.
     */
    private void addRecommendations(Analysis analysis, AiAdvice advice) {
        List<Recommendation> rows = new ArrayList<>();
        int order = 0;

        for (AiAdvice.Improvement item : advice.improvements()) {
            rows.add(row(RecommendationType.IMPROVEMENT, item.title(), item.detail(),
                    item.priority(), null, order++));
        }
        order = 0;
        for (AiAdvice.ProjectIdea idea : advice.recommendedProjects()) {
            rows.add(row(RecommendationType.PROJECT, idea.title(), projectDetail(idea),
                    Priority.MEDIUM, null, order++));
        }
        order = 0;
        for (AiAdvice.LearningTopic topic : advice.learningRecommendations()) {
            rows.add(row(RecommendationType.LEARNING, topic.title(), topic.detail(),
                    topic.priority(), topic.resourceUrl(), order++));
        }
        order = 0;
        for (AiAdvice.KeywordPlacement keyword : advice.suggestedKeywords()) {
            rows.add(row(RecommendationType.KEYWORD, keyword.term(), keyword.placement(),
                    Priority.MEDIUM, null, order++));
        }

        rows.forEach(analysis::addRecommendation);
    }

    /**
     * The skills a project idea would demonstrate, folded into its detail.
     *
     * <p>A separate column would be the tidier schema and the wrong call: nothing queries a project
     * by skill, the list is two or three names, and an extra join table to render one sentence is
     * complexity with no reader.
     */
    private static String projectDetail(AiAdvice.ProjectIdea idea) {
        String detail = idea.detail() == null ? "" : idea.detail().strip();
        if (idea.skillSlugs().isEmpty()) {
            return detail;
        }
        String practises = "Practises: " + String.join(", ", idea.skillSlugs()) + ".";
        return detail.isEmpty() ? practises : detail + " " + practises;
    }

    /**
     * One recommendation row.
     *
     * <p>{@code detail} is {@code nullable = false}, and a model does sometimes return a title with
     * no detail. Storing a blank there rather than refusing the row is the right trade: a titled
     * suggestion with no elaboration is still advice, and the alternative is dropping it over a
     * missing sentence.
     */
    private static Recommendation row(RecommendationType type, String title, String detail,
                                      Priority priority, String url, int order) {
        return Recommendation.builder()
                .type(type)
                .title(Fitted.required(title, MAX_TITLE))
                .detail(Fitted.required(detail, MAX_DETAIL))
                .priority(priority == null ? Priority.MEDIUM : priority)
                .resourceUrl(Fitted.to(url, MAX_URL))
                .displayOrder(order)
                .build();
    }

    /**
     * Elapsed milliseconds as an {@code int}.
     *
     * <p>Clamped rather than cast. A cast of a long past {@code Integer.MAX_VALUE} wraps to a
     * negative number, and a negative duration in a column is the sort of value that survives for
     * years because nobody believes it.
     */
    private static Integer millis(long elapsedMs) {
        if (elapsedMs < 0) {
            return 0;
        }
        return (int) Math.min(elapsedMs, Integer.MAX_VALUE);
    }
}
