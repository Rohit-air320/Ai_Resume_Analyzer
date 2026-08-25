package com.resumeiq.jobdescription.parse;

import java.util.List;
import java.util.Optional;

/**
 * The parts of a job posting worth telling apart.
 *
 * <p>Job postings are not free-form. Almost every one is a sequence of headed blocks —
 * requirements, what you will do, nice to have, benefits, boilerplate about the company — and
 * that structure carries the meaning the analysis needs. A skill under "Requirements" is a
 * demand; the same skill under "Perks" is a training budget. Reading the whole posting as one
 * bag of words throws that away and produces confidently wrong advice.
 *
 * <p>Two things hang off each constant: the {@link SkillImportance} a skill inherits from being
 * found here, and the weight the keyword extractor gives it. Keeping both on the enum means the
 * whole weighting scheme is one screenful that can be read and argued with, instead of numbers
 * scattered across three classes.
 *
 * <p><strong>Declaration order is matching precedence</strong>, and it is not alphabetical or
 * arbitrary. {@link #PREFERRED} comes first because its words qualify words the other sections
 * also use: "Preferred qualifications" and "Minimum qualifications" share the noun, and only the
 * adjective says which one it is. {@link #OTHER} is last because it is the fallback and matches
 * nothing.
 */
public enum PostingSection {

    /**
     * Bonus material. Checked first: "preferred" and "nice to have" are the qualifiers that tell
     * an otherwise identical heading apart from a hard requirement.
     */
    PREFERRED(SkillImportance.PREFERRED, 2, List.of(
            "preferred", "nice to have", "nice-to-have", "good to have", "bonus",
            "desired", "desirable", "ideally", "we'd love", "would love",
            "extra credit", "pluses", "plus points", "not required but"
    )),

    /**
     * Compensation and perks. Weight zero, deliberately: a keyword from this section is never
     * advice. "Kubernetes" in a sentence about conference budgets is not a skill the posting is
     * asking for, and suggesting a candidate add it to their resume would be advising them to
     * claim something nobody asked about.
     */
    BENEFITS(SkillImportance.MENTIONED, 0, List.of(
            "benefits", "perks", "what we offer", "what you'll get", "what you get",
            "compensation", "salary", "pay range", "why join", "our offer",
            "we offer", "life at"
    )),

    /**
     * Company boilerplate, legal text and application instructions. Also weight zero. Kept
     * separate from {@link #BENEFITS} rather than folded into it because calling an
     * equal-opportunity statement a "benefit" would be a small lie in the source, and small lies
     * in names are what make code confusing a year later.
     */
    COMPANY(SkillImportance.MENTIONED, 0, List.of(
            "about us", "about the company", "who we are", "our mission", "our story",
            "our team", "equal opportunity", "eeo", "diversity", "how to apply",
            "application process", "interview process", "next steps", "location", "disclaimer"
    )),

    /** What the posting demands. The section that matters most, and the usual home of skills. */
    REQUIREMENTS(SkillImportance.REQUIRED, 3, List.of(
            "requirements", "required", "qualifications", "minimum",
            "must have", "must-have", "must haves", "essential", "basic qualifications",
            "what you need", "what you'll need", "what we're looking for",
            "what we are looking for", "who you are", "your profile", "skills",
            "technical skills", "experience required"
    )),

    /**
     * The day-to-day work. Skills here are {@link SkillImportance#REQUIRED} too, which is a
     * choice worth defending: a posting that says "you will build REST APIs with Spring Boot" is
     * asking for Spring Boot just as plainly as one that lists it under requirements. Plenty of
     * postings only ever name their stack here.
     */
    RESPONSIBILITIES(SkillImportance.REQUIRED, 2, List.of(
            "responsibilities", "responsibility", "what you'll do", "what you will do",
            "what you'll be doing", "the role", "your role", "role overview", "about the role",
            "about this role", "in this role", "the job", "duties", "day to day", "day-to-day",
            "your impact", "key tasks", "job description", "position summary", "overview"
    )),

    /**
     * Everything before the first recognised heading, and anything under a heading that matched
     * nothing. Usually the opening paragraph — which often names the stack, so it is read rather
     * than discarded.
     */
    OTHER(SkillImportance.MENTIONED, 1, List.of());

    /**
     * Words that mean "optional" wherever they appear in a heading, not only at the start.
     *
     * <p>Prefix matching alone gets "Skills we'd love to see" wrong: it starts with "skills", so
     * it reads as a hard requirement, when the heading plainly says otherwise. Any heading
     * containing one of these is downgraded to {@link #PREFERRED} whatever else it matched.
     *
     * <p>In practice a heading that long is written with a colon, in capitals or in bold, and
     * {@link SectionSplitter} only recognises the long ones when they are marked that way — a bare
     * line of five words that happens to start with "skills" is more often a sentence.
     */
    private static final List<String> OPTIONAL_MARKERS = List.of(
            "nice to have", "nice-to-have", "preferred", "bonus", "would love", "we'd love",
            "good to have", "desirable", "optional", "a plus"
    );

    private final SkillImportance importance;
    private final int keywordWeight;
    private final List<String> headingKeywords;

    PostingSection(SkillImportance importance, int keywordWeight, List<String> headingKeywords) {
        this.importance = importance;
        this.keywordWeight = keywordWeight;
        this.headingKeywords = headingKeywords;
    }

    /** What a skill found in this section means. */
    public SkillImportance importance() {
        return importance;
    }

    /**
     * Multiplier applied to a keyword's frequency here. Zero means "never suggest this",
     * which is the point of having the number on the section at all.
     */
    public int keywordWeight() {
        return keywordWeight;
    }

    /** The heading phrases that identify this section, as prefixes of a normalised heading. */
    public List<String> headingKeywords() {
        return headingKeywords;
    }

    /** True when this section's text is asking the candidate for something. */
    public boolean isDemanding() {
        return this == REQUIREMENTS || this == RESPONSIBILITIES;
    }

    /**
     * Classifies an already-normalised heading line — lower case, no trailing colon, no bullet,
     * apostrophes folded — or returns empty when it is not a heading this understands.
     *
     * @param heading normalised heading text
     * @return the section, or empty when nothing matched
     */
    public static Optional<PostingSection> classify(String heading) {
        return match(heading).map(Match::section);
    }

    /**
     * As {@link #classify}, and also says which phrase did the identifying.
     *
     * <p>The caller needs the phrase, not only the answer. A line that matched on two words out of
     * seven is a sentence that happens to start like a heading — "Must have Java experience" begins
     * with "must have" — and treating it as a heading consumes the line and loses the Java in it.
     * {@link SectionSplitter} uses the length of the matched phrase to tell those apart, which it
     * cannot do if all it gets back is the section.
     *
     * @param heading normalised heading text
     * @return the section and the phrase, or empty when nothing matched
     */
    public static Optional<Match> match(String heading) {
        if (heading == null || heading.isBlank()) {
            return Optional.empty();
        }
        for (PostingSection section : values()) {
            for (String keyword : section.headingKeywords) {
                if (heading.startsWith(keyword)) {
                    return Optional.of(new Match(downgradeIfOptional(section, heading), keyword));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A heading that was recognised, and the phrase that recognised it.
     *
     * @param section what the heading labels
     * @param keyword the phrase from {@link #headingKeywords()} that the heading started with
     */
    public record Match(PostingSection section, String keyword) {

        /** How many words the identifying phrase spans. */
        public int keywordWords() {
            return keyword.isBlank() ? 0 : keyword.split("\\s+").length;
        }
    }

    private static PostingSection downgradeIfOptional(PostingSection matched, String heading) {
        if (matched.keywordWeight == 0) {
            // Benefits and boilerplate are already at the bottom; "a plus" inside a perks
            // heading should not promote it to a section skills are read from.
            return matched;
        }
        for (String marker : OPTIONAL_MARKERS) {
            if (heading.contains(marker)) {
                return PREFERRED;
            }
        }
        return matched;
    }
}
