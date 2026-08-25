package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores each resume section on its own.
 *
 * <p>The overall score answers "how am I doing"; this answers "where do I start", which is the more
 * useful question and the one a single number cannot address. A resume at 61 with a strong experience
 * section and no skills list needs an afternoon's work in one place, and a resume at 61 that is
 * uniformly thin needs a rewrite — same headline number, completely different advice.
 *
 * <h2>Every section is reviewed, present or not</h2>
 *
 * <p>Absent sections score low rather than being omitted. The chart keeps a fixed set of axes that
 * way, and more importantly the absence is itself the finding: on an early-career resume the single
 * most valuable suggestion is usually "add a projects section", and a chart that silently drops the
 * axis is the one place that advice would never appear.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <p>It does not judge whether the writing is good. Nothing here can tell a sharp bullet from a vague
 * one, so it measures what it can count — presence, structure, numbers, skill evidence — and leaves
 * the prose judgement to the advice layer, which is what a language model is actually good at. Mixing
 * the two would produce a score that looks measured and is not.
 */
public final class SectionReviewer {

    /** A section that is simply not there. Not zero: the rest of the resume still carries signal. */
    private static final int ABSENT = 20;

    /** Contact is the exception — with no contact block at all, nothing else matters to a parser. */
    private static final int ABSENT_CONTACT = 10;

    private SectionReviewer() {
    }

    /**
     * Reviews all eight sections.
     *
     * @param resume the parsed resume
     * @param skills the skill comparison, which is what makes the skills and experience reviews say
     *               something about this posting rather than about resumes in general
     * @return one review per {@link ResumeSection}, in enum order so the chart is stable
     */
    public static List<SectionReview> review(ResumeInsight resume,
                                             SkillComparison.Comparison skills) {
        List<SectionReview> reviews = new ArrayList<>(ResumeSection.values().length);
        reviews.add(contact(resume));
        reviews.add(summary(resume));
        reviews.add(skills(resume, skills));
        reviews.add(experience(resume, skills));
        reviews.add(projects(resume, skills));
        reviews.add(education(resume));
        reviews.add(certifications(resume));
        reviews.add(formatting(resume));
        return List.copyOf(reviews);
    }

    private static SectionReview contact(ResumeInsight resume) {
        ResumeShape shape = resume.shape();
        int signals = shape.contactSignals();
        if (signals == 0) {
            return new SectionReview(ResumeSection.CONTACT, ABSENT_CONTACT, false,
                    "No email, phone or profile link could be found. An ATS keys the entire "
                            + "application on the contact block, so this is the first thing to fix.");
        }
        // Email is most of this section: it is the field an ATS uses as the record key, and a resume
        // with a phone number and no email routinely parses into an unreachable record.
        int score = switch (signals) {
            case 3 -> 100;
            case 2 -> shape.hasEmail() ? 80 : 55;
            default -> shape.hasEmail() ? 60 : 35;
        };
        return new SectionReview(ResumeSection.CONTACT, score, true, describeContact(shape));
    }

    private static String describeContact(ResumeShape shape) {
        if (shape.contactSignals() == 3) {
            return "Email, phone and a profile link are all present and parseable.";
        }
        List<String> missing = new ArrayList<>();
        if (!shape.hasEmail()) {
            missing.add("an email address");
        }
        if (!shape.hasPhone()) {
            missing.add("a phone number");
        }
        if (!shape.hasLink()) {
            missing.add("a GitHub, portfolio or LinkedIn link");
        }
        return "Found " + shape.contactSignals() + " of 3 contact signals. Missing "
                + String.join(" and ", missing) + ".";
    }

    private static SectionReview summary(ResumeInsight resume) {
        if (!resume.has(ResumeSection.SUMMARY)) {
            return new SectionReview(ResumeSection.SUMMARY, ABSENT, false,
                    "No summary or headline. Two or three lines naming the role you are targeting "
                            + "and your strongest evidence for it is the cheapest way to frame "
                            + "everything a reviewer reads next.");
        }
        // A summary that names no technology is usually the adjective-heavy kind — "passionate,
        // detail-oriented, hard-working" — which reads as filler to a reviewer and matches nothing.
        long named = resume.skills().stream()
                .filter(skill -> skill.sections().contains(ResumeSection.SUMMARY))
                .count();
        if (named == 0) {
            return new SectionReview(ResumeSection.SUMMARY, 62, true,
                    "A summary is present but names no specific technology. Summaries built from "
                            + "adjectives rather than evidence are the ones reviewers skip.");
        }
        return new SectionReview(ResumeSection.SUMMARY, named >= 3 ? 95 : 82, true,
                "The summary names " + named + (named == 1 ? " technology" : " technologies")
                        + ", which is what makes it read as evidence rather than adjectives.");
    }

    private static SectionReview skills(ResumeInsight resume, SkillComparison.Comparison comparison) {
        if (!resume.has(ResumeSection.SKILLS)) {
            return new SectionReview(ResumeSection.SKILLS, ABSENT, false,
                    "No skills section under a heading an ATS recognises. Keyword search is the "
                            + "first filter most applications meet, and this is the section it reads.");
        }
        if (comparison.isUnmeasurable()) {
            return new SectionReview(ResumeSection.SKILLS, 70, true,
                    "A skills section is present. The posting named no catalogue skill, so how well "
                            + "it covers this particular job could not be measured.");
        }
        int demanded = comparison.demanded().size();
        int covered = comparison.strong().size() + comparison.partial().size();
        int score = ScoreCard.percent((double) covered / demanded);
        return new SectionReview(ResumeSection.SKILLS, score, true,
                "The resume names " + covered + " of the " + demanded
                        + " skills this posting asks for. " + comparison.gaps().size()
                        + " are absent entirely.");
    }

    private static SectionReview experience(ResumeInsight resume,
                                            SkillComparison.Comparison comparison) {
        ResumeShape shape = resume.shape();
        if (!resume.has(ResumeSection.EXPERIENCE)) {
            return new SectionReview(ResumeSection.EXPERIENCE, ABSENT, false,
                    "No experience section was found under a recognisable heading. If the work is "
                            + "there under a different heading, renaming it to \"Experience\" costs "
                            + "nothing and is what a parser looks for.");
        }
        long evidenced = demandsUnder(resume, comparison, ResumeSection.EXPERIENCE);
        // Presence is the floor; the rest is earned by how the section is written. Bullets and
        // numbers are counted across the document rather than within the section, which the note
        // says plainly — the reader deserves to know which thing was measured.
        int score = 55
                + (shape.isBulleted() ? 15 : 0)
                + Math.min(20, 7 * shape.quantifiedLines())
                + (evidenced > 0 ? 10 : 0);
        return new SectionReview(ResumeSection.EXPERIENCE, score, true,
                "Across the resume there are " + shape.bulletCount() + " bulleted lines and "
                        + shape.quantifiedLines() + " that carry a number, and " + evidenced
                        + " of the posting's skills appear under experience rather than only in a "
                        + "list. Numbers under real roles are the strongest thing this section can "
                        + "show.");
    }

    private static SectionReview projects(ResumeInsight resume,
                                          SkillComparison.Comparison comparison) {
        if (!resume.has(ResumeSection.PROJECTS)) {
            return new SectionReview(ResumeSection.PROJECTS, ABSENT, false,
                    "No projects section. This is where a resume can demonstrate a skill it has no "
                            + "paid experience in, which makes it the fastest route to closing a "
                            + "skill gap honestly.");
        }
        long evidenced = demandsUnder(resume, comparison, ResumeSection.PROJECTS);
        int score = evidenced == 0 ? 68 : Math.min(100, 72 + 9 * (int) evidenced);
        return new SectionReview(ResumeSection.PROJECTS, score, true, evidenced == 0
                ? "A projects section is present but none of the posting's skills appear in it. A "
                        + "project that uses one of the missing skills is the most credible way to "
                        + "claim it."
                : evidenced + " of the posting's skills are demonstrated in a project, which is "
                        + "evidence rather than assertion.");
    }

    /**
     * How many of the posting's demands the resume answers inside one particular section.
     *
     * <p>Goes back to the resume's own skill records rather than reading
     * {@link SkillVerdict#foundUnder()}, which names the heading the <em>posting</em> used. Mixing up
     * the two sides of the comparison is an easy mistake to make and a hard one to see: the code would
     * run, the numbers would look plausible, and they would be answering a different question.
     */
    private static long demandsUnder(ResumeInsight resume, SkillComparison.Comparison comparison,
                                     ResumeSection section) {
        return comparison.demanded().stream()
                .map(verdict -> resume.find(verdict.slug()))
                .filter(found -> found.isPresent() && found.get().sections().contains(section))
                .count();
    }

    private static SectionReview education(ResumeInsight resume) {
        boolean present = resume.has(ResumeSection.EDUCATION);
        return new SectionReview(ResumeSection.EDUCATION, present ? 90 : ABSENT, present,
                present
                        ? "An education section was found. Keep it short and late unless you are a "
                                + "recent graduate, in which case it earns its place near the top."
                        : "No education section. Even a single line with the institution and the "
                                + "year answers a question a reviewer will otherwise wonder about.");
    }

    private static SectionReview certifications(ResumeInsight resume) {
        boolean present = resume.has(ResumeSection.CERTIFICATIONS);
        // The only section whose absence is not really a fault. Plenty of strong resumes have none,
        // so this scores neutral rather than low — and the note must not read as an instruction to
        // go and get one.
        return new SectionReview(ResumeSection.CERTIFICATIONS, present ? 92 : 70, present,
                present
                        ? "Certifications are listed, which is direct evidence for the skills they "
                                + "cover."
                        : "No certifications section, which is entirely normal and not a fault. If "
                                + "you hold any, listing them is worthwhile; there is no reason to "
                                + "acquire one for the sake of this resume.");
    }

    private static SectionReview formatting(ResumeInsight resume) {
        ResumeShape shape = resume.shape();
        int score = 100;
        List<String> problems = new ArrayList<>();
        if (shape.hasLayoutArtefacts()) {
            score -= 40;
            problems.add("tables, columns or wide runs of spacing survived text extraction, which is "
                    + "the most common reason a resume parses into nonsense");
        }
        if (!shape.isBulleted()) {
            score -= 25;
            problems.add("the resume reads as paragraphs rather than bullets");
        }
        if (shape.wordCount() > 1_100) {
            score -= 20;
            problems.add("at " + shape.wordCount() + " words it is long enough that a reviewer will "
                    + "stop before the end");
        } else if (shape.wordCount() < 250 && shape.wordCount() > 0) {
            score -= 20;
            problems.add("at " + shape.wordCount() + " words there is not enough here to assess");
        }
        // FORMATTING is always "present": it is a property of the document, not a section of it.
        return new SectionReview(ResumeSection.FORMATTING, score, true, problems.isEmpty()
                ? "The extracted text is clean: single column, bulleted, and a length a reviewer "
                        + "will read to the end of."
                : "Formatting problems found — " + String.join("; ", problems) + ".");
    }
}
