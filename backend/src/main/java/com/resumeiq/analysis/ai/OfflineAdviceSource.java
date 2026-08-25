package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.analysis.SkillImportance;
import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.analysis.engine.KeywordVerdict;
import com.resumeiq.analysis.engine.ResumeShape;
import com.resumeiq.analysis.engine.SectionReview;
import com.resumeiq.analysis.engine.SkillVerdict;
import com.resumeiq.recommendation.Priority;
import com.resumeiq.skill.SkillCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the advice in code, from the findings, with no model involved.
 *
 * <p>This class is the reason the offline mode is a mode. It runs when no API key is configured, and it
 * runs as the fallback whenever a provider call fails, so it had to be written as advice somebody would
 * actually act on rather than as a placeholder that says "AI unavailable". A user who clones this
 * project, adds no key and analyses a resume gets scores, gaps, project ideas, learning topics and
 * keyword advice — all of it specific to their two documents.
 *
 * <p>It is also the honest baseline for what the model adds. Everything here is derivable from the
 * findings by rule, which means the difference between this output and the model's output is exactly the
 * value the model contributed: reading the actual bullets, noticing that a particular sentence is vague,
 * phrasing a suggestion for this person's career rather than for a category of resume. Being able to see
 * that difference is worth more than hiding it behind a single code path.
 *
 * <h2>Its limits, stated plainly</h2>
 *
 * <p>It cannot read prose. It cannot tell a sharp bullet from a limp one, and it will never notice that
 * a summary contradicts the experience below it. So it advises on structure, coverage and evidence —
 * the things the engine measured — and does not pretend to editorial judgement it does not have.
 */
public class OfflineAdviceSource implements AdviceSource {

    /**
     * What this writer calls itself, and the prefix every source string it produces begins with.
     *
     * <p>Public because it is the answer to "did a model write this?". Both fallback sources — the plain
     * offline one and the one appended after a provider failed — start with this string, so a caller can
     * decide from the stored source alone, without a list of provider names that goes stale the moment a
     * second provider is added.
     */
    public static final String DESCRIPTION = "offline writer (no model called)";

    /** Below this a section review is worth raising as an improvement. */
    private static final int WEAK_SECTION = 70;

    /** How many improvements to write. Matches what the prompt asks a model for. */
    private static final int MAX_IMPROVEMENTS = 6;

    /**
     * Project shapes by skill category.
     *
     * <p>The categories, not the individual skills. A catalogue of per-skill project ideas would be
     * hundreds of entries that all go stale, where "for a DevOps gap, containerise something you have
     * already built and put it behind a pipeline" is true of every DevOps skill and stays true. The
     * result is a suggestion that is concrete about the shape of the work and honest about not knowing
     * the specifics.
     */
    private static final Map<SkillCategory, String> PROJECT_SHAPES = Map.of(
            SkillCategory.LANGUAGE,
            "Port one component of an existing project to %s and write up what changed. A rewrite of "
                    + "something you already understand shows the language rather than the tutorial.",
            SkillCategory.FRAMEWORK,
            "Build one small, complete thing with %s — an app with real routing, forms and error "
                    + "states. Complete matters more than large: a finished small app evidences the "
                    + "framework, a half-built large one does not.",
            SkillCategory.DATABASE,
            "Add %s to a project you already have, with a schema that needs at least one join and one "
                    + "query slow enough to need an index. Then write a line about the index — that "
                    + "sentence is what separates having used a database from understanding one.",
            SkillCategory.CLOUD,
            "Deploy an existing project to %s and keep it running. The interesting part is not the "
                    + "deploy, it is what you learn in the first week about cost, logs and what breaks.",
            SkillCategory.DEVOPS,
            "Containerise a project you have already built with %s, then add a pipeline that runs the "
                    + "tests on every push. This is the highest-value gap to close on a portfolio, "
                    + "because almost nobody does it and every team needs it.",
            SkillCategory.TESTING,
            "Add a %s suite to an existing project, aiming for the paths that would actually break. "
                    + "Then write down the bug it caught — a test suite with a story is evidence, a "
                    + "coverage number is not.",
            SkillCategory.TOOLING,
            "Use %s on your next project and record one thing it changed about how you work. Tooling "
                    + "is best evidenced by a specific before and after.",
            SkillCategory.DATA_AI,
            "Build something small end to end with %s on a dataset you care about, including the "
                    + "unglamorous parts: cleaning, evaluating, and saying what the result is not good "
                    + "enough for.",
            SkillCategory.MOBILE,
            "Ship one small %s app to a device and use it yourself for a fortnight. The bugs you find "
                    + "by using your own app are the ones worth talking about."
    );

    /** Used when a gap's category has no shape above. */
    private static final String GENERIC_PROJECT_SHAPE =
            "Build something small that genuinely requires %s, then describe the decision it forced you "
                    + "to make. A project with one hard decision in it is worth more than three that "
                    + "followed a tutorial.";

    @Override
    public AiAdvice adviseOn(AnalysisFacts facts, String postingText) {
        return new AiAdvice(
                overallFeedback(facts),
                improvements(facts),
                gaps(facts),
                projects(facts),
                learning(facts),
                keywords(facts),
                sectionNotes(facts),
                Map.of(),
                describe());
    }

    @Override
    public String describe() {
        return DESCRIPTION;
    }

    /**
     * The headline paragraph.
     *
     * <p>Leads with the largest single opportunity rather than with the score, and deliberately does not
     * name a band. The band is a pure function of the score, the frontend owns that function, and a
     * second copy of the thresholds here would be a source of truth that disagrees the day one moves.
     */
    private String overallFeedback(AnalysisFacts facts) {
        StringBuilder text = new StringBuilder();
        if (facts.isThin()) {
            return "There is not enough in one or both documents to compare them properly. The scores "
                    + "below are computed from what could be read, so treat them as provisional — a "
                    + "fuller resume or the complete job posting would change them materially.";
        }

        List<SkillVerdict> criticalGaps = facts.skills().criticalGaps();
        List<SkillVerdict> partial = facts.skills().partial();
        SectionReview weakest = facts.weakestSections().get(0);

        if (!criticalGaps.isEmpty()) {
            text.append("The biggest thing standing between this resume and this role is ")
                    .append(criticalGaps.get(0).displayName());
            if (criticalGaps.size() > 1) {
                text.append(", along with ").append(criticalGaps.size() - 1)
                        .append(criticalGaps.size() == 2 ? " other stated requirement"
                                : " other stated requirements");
            }
            text.append(" — the posting asks for it and the resume does not mention it. ");
        } else if (!partial.isEmpty()) {
            text.append("You have every skill this posting names, but ").append(partial.size())
                    .append(partial.size() == 1 ? " of them appears" : " of them appear")
                    .append(" only in a list. Moving ").append(partial.get(0).displayName())
                    .append(" into a sentence about work you actually did is the cheapest real "
                            + "improvement available here. ");
        } else {
            text.append("On skills this is a strong match: every requirement the posting names is "
                    + "demonstrated somewhere in the resume. ");
        }

        if (weakest.score() < WEAK_SECTION) {
            text.append("Structurally, the ").append(sectionWord(weakest.section()))
                    .append(" section is the weakest part of the document. ");
        }

        text.append("The section notes below say what was measured in each part, and the improvements "
                + "are ordered by how much they would change.");
        return text.toString();
    }

    /**
     * Improvements, drawn from the things the engine actually measured.
     *
     * <p>Ordered by what would move the score most, which is also roughly the order of what a reviewer
     * notices: missing contact details, then absent sections, then unquantified work, then presentation.
     */
    private List<AiAdvice.Improvement> improvements(AnalysisFacts facts) {
        List<AiAdvice.Improvement> items = new ArrayList<>();
        ResumeShape shape = facts.resume().shape();

        if (!shape.hasEmail()) {
            items.add(new AiAdvice.Improvement(
                    "Add an email address to the top of the resume",
                    "No email could be found. An applicant-tracking system keys the whole application "
                            + "on this field, so a resume without one can be parsed, scored and then "
                            + "filed under nobody.",
                    Priority.HIGH, ResumeSection.CONTACT));
        }
        if (!shape.hasLink()) {
            items.add(new AiAdvice.Improvement(
                    "Add a GitHub or portfolio link",
                    "There is no link in the contact block. For a technical role this is the one line "
                            + "that lets a reviewer verify everything else on the page, which makes it "
                            + "worth more than any single bullet.",
                    Priority.MEDIUM, ResumeSection.CONTACT));
        }
        if (!facts.resume().has(ResumeSection.PROJECTS) && !facts.skills().gaps().isEmpty()) {
            items.add(new AiAdvice.Improvement(
                    "Add a projects section",
                    "There is no projects section, and this posting asks for "
                            + facts.skills().gaps().size() + " things the resume does not evidence. A "
                            + "project is the only honest way to claim a skill you have not been paid "
                            + "to use, so this section is where those gaps get closed.",
                    Priority.HIGH, ResumeSection.PROJECTS));
        }
        if (shape.quantifiedLines() < 3) {
            items.add(new AiAdvice.Improvement(
                    "Put numbers on your strongest bullets",
                    "Only " + shape.quantifiedLines() + " lines carry a number. Pick the three "
                            + "achievements you would mention in an interview and add the figure: how "
                            + "many users, how much faster, how many hours saved, how large the "
                            + "dataset. A number turns a duty into an achievement, and it is the "
                            + "single most common thing missing from a technically strong resume.",
                    Priority.HIGH, ResumeSection.EXPERIENCE));
        }
        if (!shape.isBulleted()) {
            items.add(new AiAdvice.Improvement(
                    "Rewrite the experience section as bullets",
                    "The resume reads as paragraphs. Bullets survive text extraction intact and get "
                            + "read by a human skimming for thirty seconds; paragraphs manage neither.",
                    Priority.MEDIUM, ResumeSection.FORMATTING));
        }
        if (shape.hasLayoutArtefacts()) {
            items.add(new AiAdvice.Improvement(
                    "Move to a single-column layout",
                    "Tables, columns or wide runs of spacing survived the text extraction, which means "
                            + "a parser is very likely reading this resume out of order. This is the "
                            + "most common reason a strong resume is rejected without being seen, and "
                            + "the fix is a layout change rather than a content one.",
                    Priority.HIGH, ResumeSection.FORMATTING));
        }
        for (SkillVerdict verdict : facts.skills().partial()) {
            if (items.size() >= MAX_IMPROVEMENTS) {
                break;
            }
            items.add(new AiAdvice.Improvement(
                    "Show where you used " + verdict.displayName(),
                    verdict.displayName() + " is in your skills list but does not appear in any role "
                            + "or project, and this posting asks for it. One sentence naming what you "
                            + "built with it is worth more than its place in the list — a reviewer "
                            + "discounts a list and believes a sentence.",
                    verdict.importance() == SkillImportance.CRITICAL ? Priority.HIGH : Priority.MEDIUM,
                    ResumeSection.EXPERIENCE));
        }
        if (!facts.resume().has(ResumeSection.SUMMARY) && items.size() < MAX_IMPROVEMENTS) {
            items.add(new AiAdvice.Improvement(
                    "Add a two-line summary naming the role you want",
                    "There is no summary. Two lines saying which role you are targeting and your "
                            + "strongest piece of evidence for it frames everything a reviewer reads "
                            + "next, and it is the only part of the resume you can tailor per "
                            + "application in under a minute.",
                    Priority.MEDIUM, ResumeSection.SUMMARY));
        }
        return items.size() > MAX_IMPROVEMENTS ? items.subList(0, MAX_IMPROVEMENTS) : items;
    }

    private List<AiAdvice.GapNote> gaps(AnalysisFacts facts) {
        List<AiAdvice.GapNote> notes = new ArrayList<>();
        for (SkillVerdict gap : facts.skills().gaps()) {
            notes.add(new AiAdvice.GapNote(gap.slug(), gapDetail(gap), priorityFor(gap)));
        }
        return notes;
    }

    private String gapDetail(SkillVerdict gap) {
        String where = gap.foundUnder() == null
                ? "The posting mentions it"
                : "The posting asks for it under \"" + gap.foundUnder() + "\"";
        return switch (gap.importance()) {
            case CRITICAL -> where + ", as a stated requirement, and it does not appear anywhere in "
                    + "your resume. If you have used " + gap.displayName() + " and simply have not "
                    + "written it down, that is the fastest fix on this page. If you have not, a small "
                    + "project is the honest route.";
            case IMPORTANT -> where + " as a preference rather than a requirement. Preferred skills "
                    + "are usually where the cheapest wins are: fewer applicants have them, so "
                    + "evidencing " + gap.displayName() + " separates you from people who match the "
                    + "requirements exactly.";
            case NICE_TO_HAVE -> where + " in passing. Worth knowing about, not worth reshaping your "
                    + "resume for — and certainly not worth claiming.";
        };
    }

    private List<AiAdvice.ProjectIdea> projects(AnalysisFacts facts) {
        List<AiAdvice.ProjectIdea> ideas = new ArrayList<>();
        for (SkillVerdict gap : facts.skills().gaps()) {
            if (ideas.size() >= 4) {
                break;
            }
            if (gap.importance() == SkillImportance.NICE_TO_HAVE) {
                // Building something to chase a skill mentioned once in passing is bad advice.
                continue;
            }
            String shape = PROJECT_SHAPES.getOrDefault(gap.category(), GENERIC_PROJECT_SHAPE);
            ideas.add(new AiAdvice.ProjectIdea(
                    "A project that evidences " + gap.displayName(),
                    String.format(shape, gap.displayName()),
                    List.of(gap.slug())));
        }
        return ideas;
    }

    private List<AiAdvice.LearningTopic> learning(AnalysisFacts facts) {
        List<AiAdvice.LearningTopic> topics = new ArrayList<>();
        for (SkillVerdict gap : facts.skills().gaps()) {
            if (topics.size() >= 5) {
                break;
            }
            if (gap.importance() == SkillImportance.NICE_TO_HAVE) {
                continue;
            }
            topics.add(new AiAdvice.LearningTopic(
                    "Learn " + gap.displayName(),
                    "This posting asks for " + gap.displayName() + " and your resume does not "
                            + "evidence it. Aim for the point where you could explain a decision you "
                            + "made using it — that is the level an interview actually probes, and it "
                            + "is well short of mastery.",
                    // No URL. A link this class invented would be a fabrication, and the sanitiser
                    // would rightly drop it.
                    null,
                    priorityFor(gap)));
        }
        return topics;
    }

    /**
     * Keyword advice, always with a placement.
     *
     * <p>The placement is chosen from sections the resume actually has, which is what keeps this from
     * becoming a list of words to paste in. Where the resume has neither experience nor projects there
     * is nowhere honest to put a term, so no keyword advice is produced at all — the right answer in
     * that case is the structural improvement, not a vocabulary exercise.
     */
    private List<AiAdvice.KeywordPlacement> keywords(AnalysisFacts facts) {
        ResumeSection target = facts.resume().has(ResumeSection.EXPERIENCE)
                ? ResumeSection.EXPERIENCE
                : facts.resume().has(ResumeSection.PROJECTS) ? ResumeSection.PROJECTS : null;
        if (target == null) {
            return List.of();
        }
        List<AiAdvice.KeywordPlacement> placements = new ArrayList<>();
        for (KeywordVerdict absent : facts.absentKeywords()) {
            if (placements.size() >= 6) {
                break;
            }
            placements.add(new AiAdvice.KeywordPlacement(absent.term(),
                    "If your " + sectionWord(target) + " section already describes work of this kind, "
                            + "use the posting's wording for it — \"" + absent.term() + "\". If it "
                            + "does not, leave the term out."));
        }
        return placements;
    }

    /** The engine's own section notes, passed through — they are already written for a person. */
    private List<AiAdvice.SectionNote> sectionNotes(AnalysisFacts facts) {
        List<AiAdvice.SectionNote> notes = new ArrayList<>(facts.sections().size());
        for (SectionReview review : facts.sections()) {
            notes.add(new AiAdvice.SectionNote(review.section(), review.note()));
        }
        return notes;
    }

    private Priority priorityFor(SkillVerdict gap) {
        return switch (gap.importance()) {
            case CRITICAL -> Priority.HIGH;
            case IMPORTANT -> Priority.MEDIUM;
            case NICE_TO_HAVE -> Priority.LOW;
        };
    }

    /** "experience" from EXPERIENCE, for use mid-sentence. */
    private String sectionWord(ResumeSection section) {
        return section.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
