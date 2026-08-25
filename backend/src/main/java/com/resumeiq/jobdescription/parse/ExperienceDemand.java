package com.resumeiq.jobdescription.parse;

import com.resumeiq.user.ExperienceLevel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How much experience the posting asks for, and what it said to make us think so.
 *
 * <p>This is the one number in a posting that a candidate cannot acquire before Friday, which is
 * why it is read separately from the skills. Knowing the posting wants six years lets Phase 6
 * calibrate its advice against the level on the user's profile: a student reading a senior posting
 * needs to be told that plainly, not handed a list of keywords to add.
 *
 * <p>The reading is deliberately conservative in one direction. Where several year counts appear —
 * postings routinely want "3+ years" in requirements and "5+ years" in nice-to-haves — the
 * <em>smallest</em> one from a section that demands something wins, because that is the bar a
 * candidate actually has to clear. Being told a job needs three years when it says five is an
 * error a person recovers from; being told it needs five when it says three costs them an
 * application they would have got.
 *
 * @param minYears  fewest years the posting asks for, or null when it never says
 * @param maxYears  top of a stated range ("3-5 years"), or null when it is open-ended
 * @param level     the seniority band, from the years and from the title. Reuses the profile's own
 *                  {@link ExperienceLevel} rather than inventing a parallel scale, so Phase 6 can
 *                  compare "the posting wants SENIOR" with "the user says JUNIOR" directly.
 * @param evidence  the words this was read from — "5+ years", "Senior" — or null when nothing was
 *                  found. Shown in the UI, because a claim about seniority that cannot point at
 *                  the text is one the user has no way to check.
 */
public record ExperienceDemand(Integer minYears, Integer maxYears, ExperienceLevel level,
                               String evidence) {

    /**
     * Years of experience, in most of the ways a posting writes them.
     *
     * <p>The leading phrase is optional and captured only so the evidence string reads like the
     * posting ("at least 7 years" rather than "7 years"). The range is optional too, which is what
     * lets one pattern cover "5+ years", "3-5 years", "3 to 5 years" and "2 yrs".
     */
    private static final Pattern YEARS = Pattern.compile(
            "(?:at least|minimum(?: of)?|min\\.?|over|more than|upwards of)?\\s*"
                    + "(\\d{1,2})\\s*(?:\\+|plus)?\\s*"
                    + "(?:(?:-|–|—|to)\\s*(\\d{1,2})\\s*)?"
                    + "(?:\\+\\s*)?(?:years?|yrs?)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Seniority words in a job title, strongest first.
     *
     * <p>Ordered because titles combine them: "Senior Staff Engineer" is a staff role, and
     * checking the strongest word first is simpler than trying to compose them. A
     * {@code LinkedHashMap} literal would be noise, so this is a list of pairs kept in order by
     * {@link Map#entry}.
     */
    private static final List<Map.Entry<Pattern, ExperienceLevel>> TITLE_LEVELS = List.of(
            Map.entry(word("chief|cto|vp|vice president|head of|director"), ExperienceLevel.LEAD),
            Map.entry(word("principal|staff|lead|manager"), ExperienceLevel.LEAD),
            Map.entry(word("senior|sr\\.?|architect"), ExperienceLevel.SENIOR),
            Map.entry(word("mid|mid-level|intermediate"), ExperienceLevel.MID),
            Map.entry(word("junior|jr\\.?|associate"), ExperienceLevel.JUNIOR),
            Map.entry(word("intern|internship|trainee|graduate|entry|entry-level|fresher|apprentice"),
                    ExperienceLevel.ENTRY)
    );

    /** Beyond this, the number is not a career length — it is a founding date or a typo. */
    private static final int IMPLAUSIBLE_YEARS = 40;

    /** Nothing found. Not the same as "no experience needed", and the UI must not say it is. */
    public static ExperienceDemand unknown() {
        return new ExperienceDemand(null, null, null, null);
    }

    /** True when the posting said something about experience. */
    public boolean isStated() {
        return level != null;
    }

    /**
     * Reads the demand from a posting.
     *
     * @param blocks the posting, split into sections
     * @param title  the role title, which is often the only place seniority appears at all
     */
    public static ExperienceDemand detect(List<PostingBlock> blocks, String title) {
        Years years = fewestYears(blocks);
        Titled titled = fromTitle(title);

        ExperienceLevel fromYears = years == null ? null : bandFor(years.min());
        ExperienceLevel level = stronger(fromYears, titled == null ? null : titled.level());
        if (level == null) {
            return unknown();
        }
        // The evidence has to be the thing that decided the answer, or it is not evidence. Years
        // win ties: "5+ years" is a fact about the posting where "Senior" is a job-title
        // convention, and the two disagree often enough that showing the weaker one would look
        // like a mistake.
        String evidence = level == fromYears ? years.evidence() : titled.evidence();
        return new ExperienceDemand(
                years == null ? null : years.min(),
                years == null ? null : years.max(),
                level,
                evidence);
    }

    /** The smallest stated minimum, preferring sections that are asking for something. */
    private static Years fewestYears(List<PostingBlock> blocks) {
        Years demanded = null;
        Years anywhere = null;
        for (PostingBlock block : blocks) {
            if (block.section().keywordWeight() == 0) {
                // "20 years of combined industry experience" belongs to the company, not the job.
                continue;
            }
            Years found = fewestYearsIn(block.text());
            if (found == null) {
                continue;
            }
            if (block.section().isDemanding()) {
                demanded = smaller(demanded, found);
            }
            anywhere = smaller(anywhere, found);
        }
        return demanded != null ? demanded : anywhere;
    }

    private static Years fewestYearsIn(String text) {
        Years fewest = null;
        Matcher matcher = YEARS.matcher(text);
        while (matcher.find()) {
            int min = Integer.parseInt(matcher.group(1));
            if (min <= 0 || min > IMPLAUSIBLE_YEARS) {
                continue;
            }
            Integer max = matcher.group(2) == null ? null : Integer.parseInt(matcher.group(2));
            if (max != null && (max < min || max > IMPLAUSIBLE_YEARS)) {
                max = null;
            }
            fewest = smaller(fewest, new Years(min, max, matcher.group().strip()));
        }
        return fewest;
    }

    private static Years smaller(Years current, Years candidate) {
        if (current == null) {
            return candidate;
        }
        return candidate != null && candidate.min() < current.min() ? candidate : current;
    }

    private static Titled fromTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String lowered = title.toLowerCase(Locale.ROOT);
        for (Map.Entry<Pattern, ExperienceLevel> candidate : TITLE_LEVELS) {
            Matcher matcher = candidate.getKey().matcher(lowered);
            if (matcher.find()) {
                return new Titled(candidate.getValue(), matcher.group().strip());
            }
        }
        return null;
    }

    /**
     * Years to band: 1-2 junior, 3-5 mid, 6 and up senior.
     *
     * <p>Two absences are deliberate. There is no ENTRY case, because a posting that states a
     * number of years is by definition not an entry-level posting — ENTRY here can only come from
     * a title saying "intern" or "graduate". And there is no LEAD case however large the number
     * gets, because {@link ExperienceLevel#LEAD} is about leading people and twelve years of
     * writing code is not that. Only a title can say LEAD.
     */
    private static ExperienceLevel bandFor(int minYears) {
        if (minYears <= 2) {
            return ExperienceLevel.JUNIOR;
        }
        if (minYears <= 5) {
            return ExperienceLevel.MID;
        }
        return ExperienceLevel.SENIOR;
    }

    /** The more senior of two readings; enum order is seniority order. */
    private static ExperienceLevel stronger(ExperienceLevel first, ExperienceLevel second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static Pattern word(String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    private record Years(int min, Integer max, String evidence) {
    }

    private record Titled(ExperienceLevel level, String evidence) {
    }
}
