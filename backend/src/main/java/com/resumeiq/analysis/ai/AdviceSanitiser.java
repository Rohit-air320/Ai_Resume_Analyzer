package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.analysis.engine.KeywordVerdict;
import com.resumeiq.analysis.engine.SectionReview;
import com.resumeiq.analysis.engine.SkillVerdict;
import com.resumeiq.common.text.PlainText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks model-written advice against the computed findings and discards anything that does not hold up.
 *
 * <p>This is where the spec's honesty requirements stop being instructions in a prompt and become a
 * property of the system. A prompt asking a model not to invent a skill reduces how often it happens; a
 * filter that drops any skill slug absent from the computed gap list means it cannot reach the user at
 * all. Both are worth having, and only one of them can be tested.
 *
 * <h2>What gets dropped</h2>
 *
 * <ul>
 *   <li>A skill gap for a skill this posting never asked for, or one the resume already evidences.
 *       Either way the model has contradicted the findings.</li>
 *   <li>A project claiming to demonstrate a skill that is not a real gap. The project survives; the
 *       false claim is stripped from its skill list.</li>
 *   <li>A keyword suggestion for a term the posting did not use, or one the resume already uses, or one
 *       with no placement. The last of these is the keyword-stuffing case and it is dropped rather
 *       than repaired.</li>
 *   <li>A learning resource URL that is not a plausible https link. A fabricated link is worse than
 *       none, so the topic is kept and the link is removed.</li>
 *   <li>Duplicates, and anything past the per-list cap.</li>
 * </ul>
 *
 * <h2>Why truncation happens here</h2>
 *
 * <p>Every string is cut to the width of the column Phase 7 stores it in. Doing it at this boundary
 * rather than at the insert means a persistence failure cannot be caused by a verbose model — the
 * alternative is a {@code DataIntegrityViolationException} at the end of a successful analysis, which
 * is a 500 for the user and a lost result for a reason that has nothing to do with them.
 */
public final class AdviceSanitiser {

    /** {@code Recommendation.title}. */
    private static final int MAX_TITLE = 160;

    /** {@code Recommendation.detail}, and the ceiling for the overall feedback paragraph. */
    private static final int MAX_DETAIL = 2_000;

    /** {@code Recommendation.resourceUrl}. */
    private static final int MAX_URL = 300;

    /** {@code SectionAssessment.note}. */
    private static final int MAX_NOTE = 400;

    /**
     * Per-list caps.
     *
     * <p>Not a storage limit — a reading limit. Twenty improvements is a list nobody works through, and
     * a model given room for twenty will pad. Cutting at the cap keeps the ones it thought were most
     * important, since it was asked to lead with those.
     */
    private static final int MAX_IMPROVEMENTS = 8;
    private static final int MAX_GAPS = 10;
    private static final int MAX_PROJECTS = 6;
    private static final int MAX_LEARNING = 6;
    private static final int MAX_KEYWORDS = 10;

    private AdviceSanitiser() {
    }

    /**
     * Cleans one set of advice against the findings it was written from.
     *
     * @param advice what the writer produced
     * @param facts  the authority on what is true about these two documents
     * @return advice containing nothing that contradicts the findings
     */
    public static AiAdvice clean(AiAdvice advice, AnalysisFacts facts) {
        Set<String> gapSlugs = slugsOf(facts.skills().gaps());
        Set<String> demandedSlugs = slugsOf(facts.skills().demanded());
        Set<String> absentTerms = termsOf(facts.absentKeywords());
        Set<ResumeSection> knownSections = sectionsOf(facts.sections());

        return new AiAdvice(
                PlainText.truncate(nullToEmpty(advice.overallFeedback()), MAX_DETAIL),
                improvements(advice.improvements()),
                gaps(advice.skillGaps(), gapSlugs),
                projects(advice.recommendedProjects(), gapSlugs, demandedSlugs),
                learning(advice.learningRecommendations()),
                keywords(advice.suggestedKeywords(), absentTerms),
                sectionNotes(advice.sectionNotes(), knownSections),
                advice.modelScores(),
                advice.source());
    }

    private static List<AiAdvice.Improvement> improvements(List<AiAdvice.Improvement> items) {
        List<AiAdvice.Improvement> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAdvice.Improvement item : items) {
            String title = PlainText.truncate(item.title().strip(), MAX_TITLE);
            if (title.isBlank() || !seen.add(key(title)) || kept.size() >= MAX_IMPROVEMENTS) {
                continue;
            }
            kept.add(new AiAdvice.Improvement(title,
                    PlainText.truncate(nullToEmpty(item.detail()), MAX_DETAIL),
                    item.priority(), item.section()));
        }
        return kept;
    }

    /**
     * Keeps only notes about skills the findings actually record as gaps.
     *
     * <p>The single most important filter in this class. A model that writes "you are missing Kubernetes"
     * about a posting that never mentioned Kubernetes has invented a requirement, and a user who
     * believes it spends a weekend on the wrong thing.
     */
    private static List<AiAdvice.GapNote> gaps(List<AiAdvice.GapNote> items, Set<String> gapSlugs) {
        List<AiAdvice.GapNote> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAdvice.GapNote item : items) {
            String slug = key(item.slug());
            if (!gapSlugs.contains(slug) || !seen.add(slug) || kept.size() >= MAX_GAPS) {
                continue;
            }
            kept.add(new AiAdvice.GapNote(slug,
                    PlainText.truncate(nullToEmpty(item.detail()), MAX_DETAIL), item.priority()));
        }
        return kept;
    }

    /**
     * Keeps the projects, strips false claims from their skill lists.
     *
     * <p>A project is an idea and does not need validating. What it says it demonstrates does: a skill
     * list is a claim about this posting, so anything in it that the posting never asked for comes out.
     * Gaps are listed first, because closing a gap is the reason to build the thing.
     */
    private static List<AiAdvice.ProjectIdea> projects(List<AiAdvice.ProjectIdea> items,
                                                       Set<String> gapSlugs,
                                                       Set<String> demandedSlugs) {
        List<AiAdvice.ProjectIdea> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAdvice.ProjectIdea item : items) {
            String title = PlainText.truncate(item.title().strip(), MAX_TITLE);
            if (title.isBlank() || !seen.add(key(title)) || kept.size() >= MAX_PROJECTS) {
                continue;
            }
            Set<String> gaps = new LinkedHashSet<>();
            Set<String> others = new LinkedHashSet<>();
            for (String claimed : item.skillSlugs()) {
                String slug = key(claimed);
                if (gapSlugs.contains(slug)) {
                    gaps.add(slug);
                } else if (demandedSlugs.contains(slug)) {
                    others.add(slug);
                }
            }
            gaps.addAll(others);
            kept.add(new AiAdvice.ProjectIdea(title,
                    PlainText.truncate(nullToEmpty(item.detail()), MAX_DETAIL), List.copyOf(gaps)));
        }
        return kept;
    }

    private static List<AiAdvice.LearningTopic> learning(List<AiAdvice.LearningTopic> items) {
        List<AiAdvice.LearningTopic> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAdvice.LearningTopic item : items) {
            String title = PlainText.truncate(item.title().strip(), MAX_TITLE);
            if (title.isBlank() || !seen.add(key(title)) || kept.size() >= MAX_LEARNING) {
                continue;
            }
            kept.add(new AiAdvice.LearningTopic(title,
                    PlainText.truncate(nullToEmpty(item.detail()), MAX_DETAIL),
                    safeUrl(item.resourceUrl()), item.priority()));
        }
        return kept;
    }

    /**
     * Keeps only terms the posting used and the resume does not, each with a placement.
     *
     * <p>The rule the spec is most emphatic about, enforced by construction. A term the resume already
     * uses is not a suggestion, a term the posting never used is an invention, and a term with no
     * placement is an instruction to paste a word in — so all three are dropped, and what survives is
     * always "you did this; here is where to say it in their words".
     */
    private static List<AiAdvice.KeywordPlacement> keywords(List<AiAdvice.KeywordPlacement> items,
                                                            Set<String> absentTerms) {
        List<AiAdvice.KeywordPlacement> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAdvice.KeywordPlacement item : items) {
            String term = item.term() == null ? "" : item.term().strip();
            String placement = item.placement() == null ? "" : item.placement().strip();
            if (term.isBlank() || placement.isBlank() || !absentTerms.contains(key(term))) {
                continue;
            }
            if (!seen.add(key(term)) || kept.size() >= MAX_KEYWORDS) {
                continue;
            }
            kept.add(new AiAdvice.KeywordPlacement(PlainText.truncate(term, MAX_TITLE),
                    PlainText.truncate(placement, MAX_URL)));
        }
        return kept;
    }

    /** One note per section, and only for sections the analysis actually reviewed. */
    private static List<AiAdvice.SectionNote> sectionNotes(List<AiAdvice.SectionNote> items,
                                                           Set<ResumeSection> known) {
        Map<ResumeSection, AiAdvice.SectionNote> kept = new LinkedHashMap<>();
        for (AiAdvice.SectionNote item : items) {
            if (item.section() == null || !known.contains(item.section())
                    || item.note() == null || item.note().isBlank()) {
                continue;
            }
            kept.putIfAbsent(item.section(), new AiAdvice.SectionNote(item.section(),
                    PlainText.truncate(item.note().strip(), MAX_NOTE)));
        }
        return List.copyOf(kept.values());
    }

    /**
     * An https URL, or null.
     *
     * <p>Not validation of whether the page exists — that would mean a network call per suggestion, from
     * inside an analysis, to a URL a model chose. This checks the shape and the scheme, drops http and
     * anything with a space in it, and leaves the rest. The narrower point is that a null link is a
     * clean outcome here: the topic is still good advice without it.
     */
    private static String safeUrl(String candidate) {
        if (candidate == null) {
            return null;
        }
        String url = candidate.strip();
        if (!url.startsWith("https://") || url.length() > MAX_URL || url.contains(" ")
                || url.length() < "https://a.b".length()) {
            return null;
        }
        return url;
    }

    private static Set<String> slugsOf(List<SkillVerdict> verdicts) {
        Set<String> slugs = new HashSet<>();
        for (SkillVerdict verdict : verdicts) {
            slugs.add(key(verdict.slug()));
        }
        return slugs;
    }

    private static Set<String> termsOf(List<KeywordVerdict> verdicts) {
        Set<String> terms = new HashSet<>();
        for (KeywordVerdict verdict : verdicts) {
            terms.add(key(verdict.term()));
        }
        return terms;
    }

    private static Set<ResumeSection> sectionsOf(List<SectionReview> reviews) {
        Set<ResumeSection> sections = new HashSet<>();
        for (SectionReview review : reviews) {
            sections.add(review.section());
        }
        return sections;
    }

    /** Comparison key: case and surrounding space are never meaningful in any of these matches. */
    private static String key(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
