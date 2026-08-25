package com.resumeiq.jobdescription.parse;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the backend understands about one posting, before any AI is involved.
 *
 * <p>This is the deliberate half of the analysis. Phase 6 sends a language model the resume and
 * the posting and asks for judgement, but the facts it is judging against are computed here, in
 * code, from a fixed skill catalogue — so "the posting requires Docker" is a claim this project can
 * defend line by line rather than something a model asserted. It also means the two parts fail
 * independently: with the AI provider down, the posting still yields its required skills, its
 * keywords and its seniority, which is most of what the user came for.
 *
 * <p>Nothing here is stored. It is recomputed from {@code rawText} whenever the posting is read,
 * which is the point — the skill catalogue grows, and a posting parsed today should benefit from a
 * skill added next month without a migration or a backfill.
 *
 * @param skills        catalogue skills the posting asks for, most important first
 * @param keywords      terms it leans on that are not catalogue skills, highest weighted first
 * @param experience    how much experience it wants, and the words that said so
 * @param wordCount     length of the posting, which is how the UI can say "this looks truncated"
 * @param sectionsFound which kinds of section were actually found, computed from the posting's own
 *                      headings. Never inferred: if this contains REQUIREMENTS then the posting
 *                      really had a requirements heading, so the UI can show "found under" labels
 *                      without ever inventing one.
 * @param structured    whether the posting had headings worth trusting. False means everything was
 *                      read as one requirements block — a reasonable default, but the reason the
 *                      importance labels deserve less confidence, and worth telling the user.
 */
public record PostingInsight(
        List<DetectedSkill> skills,
        List<Keyword> keywords,
        ExperienceDemand experience,
        int wordCount,
        Set<PostingSection> sectionsFound,
        boolean structured
) {

    /**
     * Defensive copies, because a record's components are only as immutable as what is passed in.
     *
     * <p>The section set becomes an {@link EnumSet}, not a {@code Set.copyOf} — an EnumSet iterates
     * in declaration order, and {@code Set.copyOf} iterates in whatever order its hashing produced.
     * That difference reaches the JSON, and a field whose array order changes between identical
     * requests is a field nobody can write a test against.
     */
    public PostingInsight {
        skills = List.copyOf(skills);
        keywords = List.copyOf(keywords);
        sectionsFound = sectionsFound.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(sectionsFound));
    }

    /** Nothing to read — an empty posting, or one that is somehow all boilerplate. */
    public static PostingInsight empty() {
        return new PostingInsight(List.of(), List.of(), ExperienceDemand.unknown(), 0,
                Set.of(), false);
    }

    /** The hard requirements. What a missing skill costs the user most to be missing. */
    public List<DetectedSkill> requiredSkills() {
        return skills.stream().filter(DetectedSkill::isRequired).toList();
    }

    /** The nice-to-haves, which are where the cheapest wins usually are. */
    public List<DetectedSkill> preferredSkills() {
        return skills.stream()
                .filter(skill -> skill.importance() == SkillImportance.PREFERRED)
                .toList();
    }

    /**
     * Skill slugs in the order they were detected.
     *
     * <p>A {@code LinkedHashSet} rather than a plain list: this is what Phase 6 intersects with the
     * resume's own skills to compute the gap, and set semantics are what that comparison wants
     * while the insertion order keeps importance ranking intact for display.
     */
    public Set<String> skillSlugs() {
        Set<String> slugs = new LinkedHashSet<>(skills.size());
        for (DetectedSkill skill : skills) {
            slugs.add(skill.slug());
        }
        return slugs;
    }

    /** Keyword terms only, for the prompt and for the UI's chip list. */
    public List<String> keywordTerms() {
        return keywords.stream().map(Keyword::term).toList();
    }
}
