package com.resumeiq.jobdescription.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the headings in a job posting and cuts it into {@link PostingBlock}s.
 *
 * <p>Everything downstream depends on this, because the section a skill was found in is the only
 * evidence a posting gives about how much it wants that skill. Get the split wrong and the
 * skill-gap page confidently tells someone to learn a technology that appeared once in a
 * sentence about the training budget.
 *
 * <h2>What counts as a heading</h2>
 *
 * <p>Heading detection over text people wrote by hand is guesswork, so the guesses are
 * deliberately conservative: <strong>a missed heading costs precision, an invented heading costs
 * content.</strong> When a body line is mistaken for a heading, the line's own words are consumed
 * as a label and the skills in it disappear — so every rule below is tuned to fail towards
 * "this is body text".
 *
 * <p>A line is a heading when all of these hold. Each one is there because of a line that broke
 * an earlier version:
 *
 * <ul>
 *   <li><strong>It does not start with a bullet.</strong> Bullets are content. "- Must have Java"
 *       otherwise reads as a "Must have" heading and loses the Java.</li>
 *   <li><strong>It contains no digit.</strong> Headings almost never do, and this single rule
 *       rescues "Minimum 5 years of Java", which is a requirement line that begins with a
 *       requirements keyword.</li>
 *   <li><strong>It is short</strong> — at most six words, relaxed to eight when the line ends in
 *       a colon or is written in capitals, both of which are strong signals on their own.</li>
 *   <li><strong>It does not end like a sentence</strong>, so no full stop, question or
 *       exclamation mark.</li>
 *   <li><strong>A known section phrase starts it</strong>, per {@link PostingSection#classify}.
 *       Prefix rather than contains, so "Experience with Kubernetes is a plus" is not read as a
 *       "plus" heading.</li>
 *   <li><strong>It is mostly that phrase.</strong> On an unmarked line — no colon, no capitals, no
 *       markdown — the label may run at most one word past the phrase that matched it. "Must have
 *       Java experience" begins with "must have"; without this rule it becomes a heading and the
 *       Java in it is gone.</li>
 * </ul>
 *
 * <h2>Labelled lines</h2>
 *
 * <p>"Skills: Java, Spring Boot, MySQL" is both a heading and its own content, and plenty of
 * postings are written entirely that way. The text after the colon therefore becomes the first
 * body line of the new block rather than being thrown away with the label — the case that made
 * an earlier version report a posting as having no skills at all.
 */
public final class SectionSplitter {

    /**
     * A bullet, a dash or a numbered-list marker, each requiring the whitespace that follows it.
     * The whitespace matters: {@code "* Java"} is a bullet, {@code "**Requirements**"} is not.
     */
    private static final Pattern BULLET_PREFIX =
            Pattern.compile("^(?:[•·*+\\-–—>]|\\d+[.)])\\s+");

    /** Markdown emphasis and heading marks, which decorate a heading rather than negating it. */
    private static final Pattern LEADING_MARKUP = Pattern.compile("^[#*_\\s]+");

    private static final Pattern TRAILING_MARKUP = Pattern.compile("[#*_\\s]+$");

    /** Curly quotes, so a keyword list can be written with ordinary apostrophes. */
    private static final Pattern CURLY_APOSTROPHE = Pattern.compile("[‘’]");

    private static final Pattern REPEATED_SPACE = Pattern.compile("\\s{2,}");

    private static final Pattern DIGIT = Pattern.compile("\\d");

    /** Longest a heading may be, in words, when nothing else marks it out as one. */
    private static final int MAX_HEADING_WORDS = 6;

    /** Longest a heading may be when it ends in a colon or is written in capitals. */
    private static final int MAX_MARKED_HEADING_WORDS = 8;

    /** A label longer than this is prose, whatever else it looks like. */
    private static final int MAX_HEADING_CHARACTERS = 70;

    private SectionSplitter() {
    }

    /**
     * Splits normalised posting text into blocks.
     *
     * <p>Always returns at least one block. A posting with no recognisable headings comes back as
     * a single {@link PostingSection#OTHER} block, which the parser then decides what to do
     * with — a decision that belongs there rather than here, because it depends on the posting as
     * a whole rather than on any one line.
     *
     * @param text posting text, already through {@link com.resumeiq.common.text.PlainText}
     */
    public static List<PostingBlock> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<PostingBlock> blocks = new ArrayList<>();
        PostingSection current = PostingSection.OTHER;
        String currentHeading = null;
        StringBuilder body = new StringBuilder();

        for (String line : text.split("\n")) {
            Optional<Heading> heading = headingIn(line);
            if (heading.isEmpty()) {
                body.append(line).append('\n');
                continue;
            }
            Heading found = heading.get();
            addBlock(blocks, current, currentHeading, body);
            current = found.section();
            currentHeading = found.label();
            body = new StringBuilder();
            if (!found.remainder().isBlank()) {
                // "Skills: Java, Spring" — the label opened a section and the rest of the line is
                // already its content.
                body.append(found.remainder()).append('\n');
            }
        }
        addBlock(blocks, current, currentHeading, body);

        return blocks.isEmpty()
                ? List.of(new PostingBlock(PostingSection.OTHER, null, text))
                : blocks;
    }

    private static void addBlock(List<PostingBlock> blocks, PostingSection section,
                                 String heading, StringBuilder body) {
        String content = body.toString().strip();
        if (content.isEmpty() && heading == null) {
            // Leading blank lines before the first heading: nothing to record.
            return;
        }
        blocks.add(new PostingBlock(section, heading, content));
    }

    /** A heading line, split into what it labels and whatever followed the colon. */
    private record Heading(PostingSection section, String label, String remainder) {
    }

    private static Optional<Heading> headingIn(String line) {
        String raw = line.strip();
        if (raw.isEmpty() || BULLET_PREFIX.matcher(raw).find()) {
            return Optional.empty();
        }

        String undecorated = TRAILING_MARKUP.matcher(
                LEADING_MARKUP.matcher(raw).replaceFirst("")).replaceFirst("");
        if (undecorated.isEmpty()) {
            return Optional.empty();
        }

        int colon = undecorated.indexOf(':');
        String beforeColon = colon >= 0 ? undecorated.substring(0, colon) : undecorated;
        String remainder = colon >= 0 ? undecorated.substring(colon + 1).strip() : "";
        // Assigned once, never reassigned: the two lambdas below read it, and a captured local has
        // to be effectively final.
        String label = TRAILING_MARKUP.matcher(beforeColon).replaceFirst("").strip();

        // A line the writer marked as a heading — with a colon, with capitals, or with markdown —
        // gets more leeway below than a bare line does, because each of those is a deliberate
        // signal and none of them happens by accident in the middle of a sentence.
        boolean marked = colon >= 0 || isAllCapitals(label) || !undecorated.equals(raw);
        if (!isPlausibleLabel(label, marked)) {
            return Optional.empty();
        }
        return PostingSection.match(normaliseForMatching(label))
                .filter(match -> marked || isMostlyLabel(label, match))
                .map(match -> new Heading(match.section(), label, remainder));
    }

    /**
     * Guards the case where a sentence merely starts like a heading.
     *
     * <p>"Must have Java experience" is four words that begin with the two-word requirements phrase
     * "must have". Read as a heading it becomes a label, the line is consumed, and the Java in it is
     * gone — the exact failure this class's opening comment is about. So an unmarked line has to be
     * close to the phrase that matched it: at most one word longer.
     *
     * <p>What that costs is small and worth stating. "Requirements and qualifications" on a bare
     * line is now missed, and its text stays with the block above it. That is the trade this class
     * is built on: a missed heading costs precision, an invented heading costs content. Add a colon
     * or capitals to the same line and it is recognised again.
     */
    private static boolean isMostlyLabel(String label, PostingSection.Match match) {
        return label.split("\\s+").length <= match.keywordWords() + 1;
    }

    private static boolean isPlausibleLabel(String label, boolean endedWithColon) {
        if (label.isEmpty() || label.length() > MAX_HEADING_CHARACTERS) {
            return false;
        }
        if (DIGIT.matcher(label).find()) {
            return false;
        }
        char last = label.charAt(label.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return false;
        }
        boolean marked = endedWithColon || isAllCapitals(label);
        int words = label.split("\\s+").length;
        return words <= (marked ? MAX_MARKED_HEADING_WORDS : MAX_HEADING_WORDS);
    }

    /**
     * True for "REQUIREMENTS" and false for "Requirements". A posting that shouts its headings is
     * telling us where they are, which earns a couple of extra words of leeway.
     */
    private static boolean isAllCapitals(String label) {
        boolean sawLetter = false;
        for (char character : label.toCharArray()) {
            if (Character.isLetter(character)) {
                sawLetter = true;
                if (Character.isLowerCase(character)) {
                    return false;
                }
            }
        }
        return sawLetter;
    }

    /** Lower case, ordinary apostrophes, single spaces — the form the keyword lists are in. */
    private static String normaliseForMatching(String label) {
        String folded = CURLY_APOSTROPHE.matcher(label).replaceAll("'");
        return REPEATED_SPACE.matcher(folded.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
