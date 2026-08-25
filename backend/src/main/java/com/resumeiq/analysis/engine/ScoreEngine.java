package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.jobdescription.parse.ExperienceDemand;
import com.resumeiq.jobdescription.parse.PostingInsight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the findings into the six scores.
 *
 * <p>Pure arithmetic over facts computed elsewhere. Every weight in this file is a judgement about
 * what matters on a resume, so they are written out as named constants and explained where they are
 * declared — a scoring function whose weights are anonymous literals is one nobody can argue with,
 * and these are exactly the numbers worth arguing about.
 *
 * <h2>Two questions, deliberately kept apart</h2>
 *
 * <p>{@code atsScore} asks whether the document can be read. {@code jobMatchScore} asks whether the
 * person fits the job. They are independent — a beautifully structured resume can be wrong for a
 * posting, and the right candidate can have a resume in a two-column template that parses into
 * nonsense — and collapsing them into one number would hide which of the two problems somebody
 * actually has. The overall score weights fit above readability, because fit is the thing a person
 * cannot fix by reformatting.
 *
 * <h2>When something cannot be measured</h2>
 *
 * <p>Several inputs are genuinely absent on real data: a posting that never states years, a resume
 * with no dates, a posting whose text names no catalogue skill. Each of those returns a neutral score
 * with a note that says so, rather than a zero. A zero is a finding, and reporting one for a
 * measurement that was not taken is the fastest way to make a score untrustworthy.
 */
public final class ScoreEngine {

    // ---- ATS budget: 100 points, every one of them something observable in the text -------------

    /** Contact block. An email is most of it, because without one the parser has no key. */
    private static final int ATS_EMAIL = 7;
    private static final int ATS_PHONE = 4;
    private static final int ATS_LINK = 4;

    /**
     * The sections an ATS looks for, weighted by how much its absence costs.
     *
     * <p>Experience above skills, because a parser that finds no work history has nothing to file
     * the candidate under. Summary and projects are worth less individually but are what an
     * early-career resume is mostly made of, which is why neither is zero.
     */
    private static final int ATS_EXPERIENCE = 10;
    private static final int ATS_SKILLS = 8;
    private static final int ATS_EDUCATION = 6;
    private static final int ATS_SUMMARY = 4;
    private static final int ATS_PROJECTS = 4;

    /** Written as bullets rather than paragraphs. */
    private static final int ATS_BULLETS = 12;

    /** Numbers that read as impact. The strongest single signal of a resume that describes outcomes. */
    private static final int ATS_QUANTIFIED = 16;

    /** Length in the range a reviewer actually reads. */
    private static final int ATS_LENGTH = 15;

    /** No table or multi-column wreckage in the extracted text. */
    private static final int ATS_LAYOUT = 10;

    // ---- Job-match weights ---------------------------------------------------------------------

    /** Skills are over half of fit: they are what a posting is mostly a list of. */
    private static final double MATCH_SKILLS = 0.55;

    /** Keywords are the language of the posting — real signal, weaker evidence. */
    private static final double MATCH_KEYWORDS = 0.25;

    /** Experience is the smallest weight because it is the crudest measurement of the three. */
    private static final double MATCH_EXPERIENCE = 0.20;

    /** Fit above readability, because readability is the one a person can fix in an afternoon. */
    private static final double OVERALL_MATCH = 0.60;
    private static final double OVERALL_ATS = 0.40;

    // ---- Neutral scores for things that could not be measured -----------------------------------

    /** The posting named no catalogue skill, so coverage is unknown rather than poor. */
    private static final int UNMEASURED_SKILLS = 55;

    /** The posting ranked no keywords — a very short or very unusual posting. */
    private static final int UNMEASURED_KEYWORDS = 60;

    /** The posting never said how many years it wants. Most postings do not. */
    private static final int UNSTATED_EXPERIENCE = 78;

    /** The resume gave no dates and no stated total, so its years are unknown. */
    private static final int UNKNOWN_EXPERIENCE = 62;

    /**
     * The posting named a seniority but never a number of years.
     *
     * <p>Common: a title saying "Senior" with nothing in the body. There is a real demand here and
     * saying so matters, but turning "Senior" into a year count would be inventing the threshold and
     * then scoring against it, so the note quotes the posting's own word and the score stays neutral.
     */
    private static final int LEVEL_ONLY_EXPERIENCE = 70;

    private ScoreEngine() {
    }

    /**
     * Scores one resume against one posting.
     *
     * @param posting  what the job asks for
     * @param resume   what the resume shows
     * @param skills   the skill-by-skill comparison of the two
     * @param keywords the keyword comparison
     * @return all six scores with the notes behind them
     */
    public static ScoreCard score(PostingInsight posting, ResumeInsight resume,
                                  SkillComparison.Comparison skills, List<KeywordVerdict> keywords) {
        List<ScoreNote> notes = new ArrayList<>();
        int ats = ats(resume, notes);
        int skillsMatch = skills(skills, notes);
        int keyword = keywords(keywords, notes);
        int experience = experience(posting.experience(), resume, notes);

        int jobMatch = ScoreCard.clamp((int) Math.round(
                MATCH_SKILLS * skillsMatch + MATCH_KEYWORDS * keyword
                        + MATCH_EXPERIENCE * experience));
        notes.add(ScoreNote.of("Job match",
                "Weighted from skills (" + percentage(MATCH_SKILLS) + "), keywords ("
                        + percentage(MATCH_KEYWORDS) + ") and experience ("
                        + percentage(MATCH_EXPERIENCE) + ")."));

        int overall = ScoreCard.clamp((int) Math.round(OVERALL_MATCH * jobMatch + OVERALL_ATS * ats));
        notes.add(ScoreNote.of("Overall",
                percentage(OVERALL_MATCH) + " job match, " + percentage(OVERALL_ATS)
                        + " ATS readability. Fit is weighted higher because formatting is the easier "
                        + "of the two to fix."));

        return new ScoreCard(overall, ats, jobMatch, skillsMatch, keyword, experience,
                List.copyOf(notes));
    }

    /**
     * How well a machine can read the document.
     *
     * <p>Nothing here looks at the posting. That is the point: this score is a property of the
     * resume, so it stays the same across every job somebody applies to, and improving it improves
     * every application at once.
     */
    private static int ats(ResumeInsight resume, List<ScoreNote> notes) {
        ResumeShape shape = resume.shape();

        int contact = (shape.hasEmail() ? ATS_EMAIL : 0)
                + (shape.hasPhone() ? ATS_PHONE : 0)
                + (shape.hasLink() ? ATS_LINK : 0);
        notes.add(new ScoreNote("Contact details", contact, ATS_EMAIL + ATS_PHONE + ATS_LINK,
                describeContact(shape)));

        int sections = (resume.has(ResumeSection.EXPERIENCE) ? ATS_EXPERIENCE : 0)
                + (resume.has(ResumeSection.SKILLS) ? ATS_SKILLS : 0)
                + (resume.has(ResumeSection.EDUCATION) ? ATS_EDUCATION : 0)
                + (resume.has(ResumeSection.SUMMARY) ? ATS_SUMMARY : 0)
                + (resume.has(ResumeSection.PROJECTS) ? ATS_PROJECTS : 0);
        int sectionsAvailable = ATS_EXPERIENCE + ATS_SKILLS + ATS_EDUCATION + ATS_SUMMARY
                + ATS_PROJECTS;
        notes.add(new ScoreNote("Recognisable sections", sections, sectionsAvailable,
                describeSections(resume)));

        int bullets = bulletPoints(shape);
        notes.add(new ScoreNote("Bullet structure", bullets, ATS_BULLETS,
                shape.bulletCount() + " bulleted lines. Bullets parse cleanly and get read; "
                        + "paragraphs do neither."));

        int quantified = quantifiedPoints(shape);
        notes.add(new ScoreNote("Quantified impact", quantified, ATS_QUANTIFIED,
                shape.quantifiedLines() + " lines carry a number. Numbers are what turn a duty "
                        + "into an achievement."));

        int length = lengthPoints(shape);
        notes.add(new ScoreNote("Length", length, ATS_LENGTH,
                shape.wordCount() + " words. Around 350 to 900 is the range a reviewer reads."));

        int layout = shape.hasLayoutArtefacts() ? 0 : ATS_LAYOUT;
        notes.add(new ScoreNote("Clean layout", layout, ATS_LAYOUT, shape.hasLayoutArtefacts()
                ? "Tables, columns or wide spacing survived the text extraction, which is the most "
                        + "common reason a resume parses into nonsense."
                : "No table or multi-column artefacts in the extracted text."));

        return ScoreCard.clamp(contact + sections + bullets + quantified + length + layout);
    }

    private static int bulletPoints(ResumeShape shape) {
        int bullets = shape.bulletCount();
        if (bullets >= 8) {
            return ATS_BULLETS;
        }
        if (bullets >= 5) {
            return 9;
        }
        return bullets >= 2 ? 5 : 0;
    }

    private static int quantifiedPoints(ResumeShape shape) {
        int quantified = shape.quantifiedLines();
        if (quantified >= 4) {
            return ATS_QUANTIFIED;
        }
        if (quantified >= 2) {
            return 12;
        }
        return quantified == 1 ? 7 : 0;
    }

    /**
     * Length, scored as a band rather than a curve.
     *
     * <p>Both ends are real failures with different causes. Too short means there is not enough to
     * assess, which is common on a first resume. Too long means the reviewer stops before the
     * relevant part, which is common on a tenth year. The middle band is wide because the right
     * length depends on career stage and pretending otherwise would penalise both ends of it.
     */
    private static int lengthPoints(ResumeShape shape) {
        int words = shape.wordCount();
        if (words >= 350 && words <= 900) {
            return ATS_LENGTH;
        }
        if (words >= 250 && words <= 1_100) {
            return 11;
        }
        return words >= 150 && words <= 1_400 ? 6 : 2;
    }

    /**
     * Weighted coverage of the skills the posting named.
     *
     * <p>Weighted twice over: by how much the posting wanted each skill, and by how well the resume
     * evidences it. A resume that demonstrates every requirement scores 100; one that lists them all
     * without ever saying where they were used lands in the seventies, which is the honest answer —
     * that resume will pass a keyword filter and struggle with a human reviewer.
     */
    private static int skills(SkillComparison.Comparison comparison, List<ScoreNote> notes) {
        if (comparison.isUnmeasurable()) {
            notes.add(ScoreNote.of("Skills match", "The posting named no skill this catalogue "
                    + "recognises, so coverage could not be measured. This is a neutral score, not "
                    + "a finding about the resume."));
            return UNMEASURED_SKILLS;
        }
        double available = 0;
        double earned = 0;
        for (SkillVerdict verdict : comparison.demanded()) {
            available += verdict.weight();
            earned += verdict.weight() * verdict.credit();
        }
        int score = ScoreCard.percent(earned / available);
        notes.add(new ScoreNote("Skills match", score, ScoreCard.MAX,
                comparison.strong().size() + " demonstrated, " + comparison.partial().size()
                        + " listed only, " + comparison.gaps().size() + " missing, of "
                        + comparison.demanded().size() + " the posting names. Weighted, so a "
                        + "required skill counts three times a passing mention."));
        return score;
    }

    /**
     * Share of the posting's important terms the resume already uses, weighted by the posting's own
     * ranking.
     *
     * <p>Weighted rather than counted, so a term from the requirements section matters more than one
     * from the benefits — the parser already worked that out, and re-deciding it here would be a
     * second opinion nobody asked for.
     */
    private static int keywords(List<KeywordVerdict> keywords, List<ScoreNote> notes) {
        if (keywords.isEmpty()) {
            notes.add(ScoreNote.of("Keyword coverage", "The posting produced no ranked terms, "
                    + "which usually means it is very short. Neutral score."));
            return UNMEASURED_KEYWORDS;
        }
        double available = 0;
        double earned = 0;
        int matched = 0;
        for (KeywordVerdict verdict : keywords) {
            // A weight of zero would make a term invisible; every ranked term should count for
            // something, so the floor is one.
            int weight = Math.max(1, verdict.weight());
            available += weight;
            if (verdict.isMatched()) {
                earned += weight;
                matched++;
            }
        }
        int score = ScoreCard.percent(earned / available);
        notes.add(new ScoreNote("Keyword coverage", score, ScoreCard.MAX,
                matched + " of " + keywords.size() + " ranked terms already appear in the resume, "
                        + "weighted by where the posting used them."));
        return score;
    }

    /**
     * How the resume's years compare to what the posting asked for.
     *
     * <p>Being over the stated range is not penalised. A posting asking for three years and a
     * candidate with eight is a fit as far as this measurement goes; whether the role is too junior
     * is a judgement about a career, not a score about a document.
     *
     * <p>Three of the four branches here return a neutral score, which is the honest shape of this
     * measurement: a posting has to state a number and a resume has to be datable before there is
     * anything to compare, and plenty of real pairs fail one of those.
     */
    private static int experience(ExperienceDemand demand, ResumeInsight resume,
                                 List<ScoreNote> notes) {
        if (!demand.isStated()) {
            notes.add(ScoreNote.of("Experience", "The posting did not state how many years it "
                    + "wants, so there is nothing to compare against. Neutral score — and note that "
                    + "an unstated requirement is not the same as no requirement."));
            return UNSTATED_EXPERIENCE;
        }
        // isStated() is true whenever a seniority level was read, and a level can come from the
        // title alone. So a stated demand does not guarantee a number.
        if (demand.minYears() == null) {
            notes.add(ScoreNote.of("Experience", "The posting signals seniority through \""
                    + demand.evidence() + "\" but never states a number of years, so there is no "
                    + "threshold to measure against. Neutral score."));
            return LEVEL_ONLY_EXPERIENCE;
        }
        if (resume.years().isEmpty()) {
            notes.add(ScoreNote.of("Experience", "The posting asks for " + demand.evidence()
                    + ", but no total and no dated roles could be read from the resume. Neutral "
                    + "score: adding dates to each role would make this measurable."));
            return UNKNOWN_EXPERIENCE;
        }
        int wanted = demand.minYears();
        int has = resume.years().get();
        if (has >= wanted) {
            notes.add(new ScoreNote("Experience", ScoreCard.MAX, ScoreCard.MAX,
                    "The resume reads as about " + has + " years against " + demand.evidence()
                            + " asked for."));
            return ScoreCard.MAX;
        }
        int gap = wanted - has;
        int score = switch (gap) {
            case 1 -> 84;
            case 2 -> 70;
            default -> Math.max(30, 70 - 12 * (gap - 2));
        };
        notes.add(new ScoreNote("Experience", score, ScoreCard.MAX,
                "The resume reads as about " + has + " years against " + demand.evidence()
                        + " asked for — " + gap + (gap == 1 ? " year" : " years") + " short. "
                        + "Stated ranges are usually a preference rather than a filter."));
        return score;
    }

    private static String describeContact(ResumeShape shape) {
        if (shape.contactSignals() == 3) {
            return "Email, phone and a profile link are all present.";
        }
        List<String> missing = new ArrayList<>();
        if (!shape.hasEmail()) {
            missing.add("email");
        }
        if (!shape.hasPhone()) {
            missing.add("phone");
        }
        if (!shape.hasLink()) {
            missing.add("a profile or portfolio link");
        }
        return "Missing " + String.join(", ", missing)
                + ". An ATS keys the whole application on the contact block.";
    }

    private static String describeSections(ResumeInsight resume) {
        List<String> missing = new ArrayList<>();
        for (ResumeSection section : List.of(ResumeSection.SUMMARY, ResumeSection.SKILLS,
                ResumeSection.EXPERIENCE, ResumeSection.PROJECTS, ResumeSection.EDUCATION)) {
            if (!resume.has(section)) {
                missing.add(section.name().charAt(0)
                        + section.name().substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return missing.isEmpty()
                ? "Every section an ATS looks for was found under a heading it recognises."
                : "No heading found for: " + String.join(", ", missing)
                        + ". A section an ATS cannot find is a section it files under nothing.";
    }

    /** "55%" from 0.55, for the notes. */
    private static String percentage(double weight) {
        return Math.round(weight * 100) + "%";
    }
}
