package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.common.text.PlainText;
import com.resumeiq.skill.CatalogSkill;
import com.resumeiq.skill.SkillIndex;
import com.resumeiq.skill.SkillScan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a resume's extracted text into the facts an analysis is scored from.
 *
 * <p>Deliberately the mirror image of the job-posting parser, and for the same reason: a claim like
 * "your resume does not mention Docker" has to be defensible, so it is computed here in Java from
 * the text, not asked of a model that might be confidently wrong. Everything this class returns can
 * be traced to a line of the document.
 *
 * <h2>What "a heading" means here</h2>
 *
 * <p>A resume is a layout flattened into a string, and the layout is where the section boundaries
 * were. What survives extraction is a set of weak signals, so a line is treated as a heading when it
 * is short, is not a sentence, and names a section {@link ResumeSectionVocabulary} knows. All three
 * conditions matter: without the length limit, a bullet reading "Improved skills across the team"
 * starts a new SKILLS section; without the vocabulary check, every short line is a heading.
 *
 * <p>Text before the first recognised heading is attributed to {@link ResumeSection#CONTACT}, since
 * that is what is at the top of essentially every resume — the name, the email, the links.
 *
 * <h2>What it refuses to guess</h2>
 *
 * <p>Years of experience are reported only when the resume says a number, or when dated roles allow
 * one to be derived. A resume with no dates yields {@code Optional.empty()} rather than zero,
 * because "0 years of experience" is a claim about a person and an absent date range is a fact about
 * a document.
 */
public final class ResumeReader {

    /** A heading is a short line. Long enough for "Professional Development", short enough to exclude prose. */
    private static final int MAX_HEADING_CHARACTERS = 48;

    /** And a short line: four words covers "Relevant Professional Work Experience". */
    private static final int MAX_HEADING_WORDS = 5;

    /** "5+ years", "3 years of experience", "over 4 years". */
    private static final Pattern STATED_YEARS = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)(?!\\s*old)", Pattern.CASE_INSENSITIVE);

    /** A four-digit year that could plausibly be an employment date rather than a version number. */
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(19[89]\\d|20[0-4]\\d)(?!\\d)");

    /** Contact-detail signals. Each is a yes/no fact about the document, not a guess. */
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]{2,}");

    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+\\d{1,3}[\\s-]?)?(?:\\(?\\d{3,5}\\)?[\\s-]?)\\d{3}[\\s-]?\\d{3,4}(?!\\d)");

    private static final Pattern LINK = Pattern.compile(
            "(?:https?://|www\\.)\\S+|(?:linkedin\\.com|github\\.com|gitlab\\.com)/\\S+",
            Pattern.CASE_INSENSITIVE);

    /** A bullet, in any of the shapes extraction leaves behind. */
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-*•●▪·o]|\\d+[.)])\\s+");

    /** A number a hiring manager can read as impact: "40%", "1.2M", "12 engineers", "$3,000". */
    private static final Pattern QUANTIFIED = Pattern.compile(
            "\\d+\\s*%|[$€£₹]\\s*\\d|(?<!\\d)\\d[\\d,.]*\\s*(?:k|m|bn|million|billion|"
                    + "lakh|crore|users?|customers?|requests?|records?|engineers?|people|hours?|days?|"
                    + "weeks?|months?|times|x)\\b", Pattern.CASE_INSENSITIVE);

    /** Layout wreckage: the characters a table or a multi-column CV leaves in extracted text. */
    private static final Pattern TABLE_ARTEFACT = Pattern.compile("\\|\\s*\\S|\\t{2,}| {6,}\\S");

    private ResumeReader() {
    }

    /**
     * Reads one resume.
     *
     * @param extractedText the text Phase 4 stored, or null
     * @param index         the skill catalogue — the same instance the posting is parsed with, so
     *                      that both sides of the comparison read phrases identically
     * @return what the document says. Never null; an empty resume produces an empty insight.
     */
    public static ResumeInsight read(String extractedText, SkillIndex index) {
        String text = PlainText.normalise(extractedText);
        if (text == null || text.isBlank()) {
            return ResumeInsight.empty();
        }

        List<Line> lines = split(text);
        Map<ResumeSection, StringBuilder> bySection = attribute(lines);
        Set<ResumeSection> sectionsFound = EnumSet.noneOf(ResumeSection.class);
        for (Map.Entry<ResumeSection, StringBuilder> entry : bySection.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sectionsFound.add(entry.getKey());
            }
        }

        return new ResumeInsight(
                skills(bySection, index),
                sectionsFound,
                years(text),
                new ResumeShape(
                        PlainText.countWords(text),
                        lines.size(),
                        count(lines, line -> BULLET.matcher(line.text()).find()),
                        count(lines, line -> QUANTIFIED.matcher(line.text()).find()),
                        EMAIL.matcher(text).find(),
                        PHONE.matcher(text).find(),
                        LINK.matcher(text).find(),
                        TABLE_ARTEFACT.matcher(text).find()),
                text);
    }

    /**
     * Splits into lines, marking which of them are section headings.
     *
     * <p>Blank lines are dropped, but only after they have done their job: extraction preserves them
     * and they are part of why a heading is recognisable.
     */
    private static List<Line> split(String text) {
        List<Line> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            lines.add(new Line(line, headingIn(line)));
        }
        return lines;
    }

    /**
     * The section this line announces, if it announces one.
     *
     * <p>A trailing colon, a length limit and a word limit are all cheap tests that a heading passes
     * and a sentence does not. The colon case is allowed to be longer than the word limit, because
     * "Technical Skills:" is unambiguously a heading however it is punctuated.
     */
    private static Optional<ResumeSection> headingIn(String line) {
        String candidate = line.endsWith(":") ? line.substring(0, line.length() - 1).strip() : line;
        boolean punctuated = line.endsWith(":");
        if (candidate.isEmpty() || candidate.length() > MAX_HEADING_CHARACTERS) {
            return Optional.empty();
        }
        if (!punctuated && PlainText.countWords(candidate) > MAX_HEADING_WORDS) {
            return Optional.empty();
        }
        // A line ending in a full stop is a sentence, whatever words it contains.
        if (candidate.endsWith(".")) {
            return Optional.empty();
        }
        return ResumeSectionVocabulary.classify(candidate);
    }

    /**
     * Gathers each line under the heading above it.
     *
     * <p>An {@link EnumMap} rather than a list of blocks: a resume can repeat a heading (two
     * "Projects" sections, or an "Experience" split by a page break) and the analysis wants all of
     * that text together. Keeping insertion-independent, enum-ordered keys also makes the section
     * assessments come out in a stable order.
     */
    private static Map<ResumeSection, StringBuilder> attribute(List<Line> lines) {
        Map<ResumeSection, StringBuilder> bySection = new EnumMap<>(ResumeSection.class);
        for (ResumeSection section : ResumeSection.values()) {
            bySection.put(section, new StringBuilder());
        }
        // Everything above the first heading is the contact block: the name, the email, the links.
        ResumeSection current = ResumeSection.CONTACT;
        for (Line line : lines) {
            if (line.heading().isPresent()) {
                current = line.heading().get();
                // The heading itself joins its section, so "Technical Skills: Java, SQL" still
                // contributes its list — extraction often folds a heading and its first line
                // together, and dropping the line would drop the skills with it.
                bySection.get(current).append(line.text()).append('\n');
                continue;
            }
            bySection.get(current).append(line.text()).append('\n');
        }
        return bySection;
    }

    /**
     * Every catalogue skill the resume claims, with where it was claimed and how often.
     *
     * <p>Section matters more than count. A skill named in the skills list and again in a project
     * description is evidenced; one that appears only in the skills list is asserted. That
     * distinction is what {@link ResumeSkill#isEvidenced()} carries into scoring, and it is the
     * honest way to tell someone their skills list is ahead of their experience.
     */
    private static List<ResumeSkill> skills(Map<ResumeSection, StringBuilder> bySection,
                                            SkillIndex index) {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (Map.Entry<ResumeSection, StringBuilder> entry : bySection.entrySet()) {
            String sectionText = entry.getValue().toString();
            if (sectionText.isBlank()) {
                continue;
            }
            for (CatalogSkill hit : SkillScan.hits(sectionText, index)) {
                tallies.computeIfAbsent(hit.slug(), slug -> new Tally(hit)).add(entry.getKey());
            }
        }
        List<ResumeSkill> skills = new ArrayList<>(tallies.size());
        for (Tally tally : tallies.values()) {
            skills.add(tally.toResumeSkill());
        }
        skills.sort((left, right) -> {
            int byMentions = Integer.compare(right.mentions(), left.mentions());
            return byMentions != 0 ? byMentions : left.displayName().compareTo(right.displayName());
        });
        return List.copyOf(skills);
    }

    /**
     * Years of experience, when the document supports a number.
     *
     * <p>Two routes, in order of trust. A stated figure wins, because "4 years of experience" is the
     * person's own claim. Failing that, the span between the earliest and latest plausible year is
     * used — which over-reports for someone whose education dates go back further than their work,
     * so it is only consulted when nothing was stated, and it is capped at a span that a resume can
     * credibly describe.
     *
     * @return empty when the resume gives nothing to work from. Not zero: absent dates are a fact
     *         about the document, whereas zero years is a claim about the person
     */
    private static Optional<Integer> years(String text) {
        Matcher stated = STATED_YEARS.matcher(text);
        int best = 0;
        while (stated.find()) {
            best = Math.max(best, Integer.parseInt(stated.group(1)));
        }
        if (best > 0 && best <= 50) {
            return Optional.of(best);
        }

        Matcher years = YEAR.matcher(text);
        int earliest = Integer.MAX_VALUE;
        int latest = Integer.MIN_VALUE;
        while (years.find()) {
            int year = Integer.parseInt(years.group(1));
            earliest = Math.min(earliest, year);
            latest = Math.max(latest, year);
        }
        if (earliest == Integer.MAX_VALUE || latest - earliest <= 0) {
            return Optional.empty();
        }
        return Optional.of(Math.min(latest - earliest, 50));
    }

    private static int count(List<Line> lines, Predicate<Line> test) {
        int found = 0;
        for (Line line : lines) {
            if (test.test(line)) {
                found++;
            }
        }
        return found;
    }

    /** One line of the document, and the section it announces if it announces one. */
    private record Line(String text, Optional<ResumeSection> heading) {
    }

    /**
     * One skill's running tally while the sections are walked.
     *
     * <p>Mutable, and private to this file, because accumulating into a record would rebuild it on
     * every mention and a resume has thousands of tokens.
     */
    private static final class Tally {

        private final CatalogSkill skill;
        private final Set<ResumeSection> sections = EnumSet.noneOf(ResumeSection.class);
        private int mentions;

        private Tally(CatalogSkill skill) {
            this.skill = skill;
        }

        private void add(ResumeSection section) {
            mentions++;
            sections.add(section);
        }

        private ResumeSkill toResumeSkill() {
            return new ResumeSkill(skill.slug(), skill.displayName(), skill.category(), mentions,
                    Set.copyOf(sections));
        }
    }
}
