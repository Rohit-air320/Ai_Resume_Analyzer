package com.resumeiq.skill;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The skill catalogue arranged for matching against text.
 *
 * <p>Matching a term is a map lookup, not a search. Every skill contributes its own slug and each
 * of its aliases as a key, and because a slug is exactly what you get by joining slugged words
 * with hyphens, a caller can build the key for a run of words and ask this one question. That is
 * what makes longest-match affordable: for a five-word window there are five lookups, not a walk
 * over a few hundred skills.
 *
 * <h2>The two credibility rules</h2>
 *
 * <p>A catalogue of real skill names contains entries that are also ordinary text, and a lookup
 * that ignores that produces advice which destroys trust in the whole feature. Two rules, both
 * about how the word was <em>written</em> rather than what it says:
 *
 * <ul>
 *   <li><strong>One-letter skills must be that capital letter alone.</strong> "C" and "R" are real
 *       languages. Without this, "R&amp;D" reports R as a required skill and "(c) 2026" reports C.
 *       "Experience with R and Python" still matches, because there the word is exactly "R".</li>
 *   <li><strong>Skills spelled like common English words must be capitalised.</strong> "Go", "Rust",
 *       "Swift", "React" and "Less" are all technologies and all ordinary words. A posting that
 *       says "we react quickly to incidents" is not asking for React, and one that says "go-getter"
 *       is not asking for Go. Postings capitalise technology names; prose does not.</li>
 * </ul>
 */
public final class SkillIndex {

    /**
     * Slugs that are also ordinary English words, and therefore only count when capitalised.
     *
     * <p>Hand-maintained on purpose. The alternative — an English dictionary — would reject
     * "Java" (a place), "Ruby" (a stone) and "Python" (a snake), which is every second backend
     * posting. This list is short, specific, and each entry is a word a job posting genuinely uses
     * in its non-technical sense.
     */
    private static final Set<String> NEEDS_CAPITAL = Set.of(
            "ant", "dart", "elm", "express", "flow", "go", "grunt", "gulp", "hive", "less",
            "make", "next", "pig", "processing", "react", "ruby", "rust", "scratch", "spark",
            "swift", "unity"
    );

    /**
     * Ceiling on how many words one term may span. The longest real entries are things like
     * "Amazon Web Services" and "Google Cloud Platform"; the cap stops a pathological catalogue
     * entry from making every position in the text an expensive scan.
     */
    public static final int MAX_TERM_WORDS = 6;

    private final Map<String, CatalogSkill> bySlug;
    private final int maxTermWords;

    private SkillIndex(Map<String, CatalogSkill> bySlug, int maxTermWords) {
        this.bySlug = bySlug;
        this.maxTermWords = maxTermWords;
    }

    /**
     * Builds an index from loaded entities, aliases included.
     *
     * <p>The caller must have fetched the aliases — {@code SkillRepository.findAllWithAliases()}
     * exists for exactly this. Reading them from a detached entity throws, and it throws in
     * production rather than in the test that used a two-skill list.
     */
    public static SkillIndex fromEntities(Collection<Skill> skills) {
        Map<String, CatalogSkill> bySlug = new HashMap<>();
        int longest = 1;
        for (Skill skill : skills) {
            CatalogSkill entry = CatalogSkill.of(skill);
            longest = Math.max(longest, put(bySlug, skill.getSlug(), entry));
            for (String alias : skill.getAliases()) {
                longest = Math.max(longest, put(bySlug, alias, entry));
            }
        }
        return new SkillIndex(Map.copyOf(bySlug), Math.min(longest, MAX_TERM_WORDS));
    }

    /** Builds an index with no aliases. Enough for most tests, and for a fixed list. */
    public static SkillIndex of(Collection<CatalogSkill> skills) {
        Map<String, CatalogSkill> bySlug = new HashMap<>();
        int longest = 1;
        for (CatalogSkill skill : skills) {
            longest = Math.max(longest, put(bySlug, skill.slug(), skill));
        }
        return new SkillIndex(Map.copyOf(bySlug), Math.min(longest, MAX_TERM_WORDS));
    }

    /** An index that matches nothing. What an un-seeded database produces. */
    public static SkillIndex empty() {
        return new SkillIndex(Map.of(), 1);
    }

    /**
     * @return how many words the longest key spans, so a caller knows the widest window worth
     *         trying. Never below one, never above {@link #MAX_TERM_WORDS}.
     */
    public int maxTermWords() {
        return maxTermWords;
    }

    /** How many keys resolve to a skill, slugs and aliases together. */
    public int size() {
        return bySlug.size();
    }

    /**
     * Looks up a term.
     *
     * @param key           slugged term, as produced by joining token keys with hyphens
     * @param firstOriginal the first source word of the term, as the author wrote it. Needed for
     *                      the capitalisation rules described on this class — which is why this
     *                      method takes it rather than being a plain map lookup.
     * @return the skill, or empty when nothing matched or the writing was not credible
     */
    public Optional<CatalogSkill> find(String key, String firstOriginal) {
        CatalogSkill skill = bySlug.get(key);
        if (skill == null || !isCrediblyWritten(key, firstOriginal)) {
            return Optional.empty();
        }
        return Optional.of(skill);
    }

    private static boolean isCrediblyWritten(String key, String original) {
        if (original == null || original.isEmpty()) {
            return false;
        }
        if (key.length() == 1) {
            return original.equals(key.toUpperCase(Locale.ROOT));
        }
        if (NEEDS_CAPITAL.contains(key)) {
            return Character.isUpperCase(original.charAt(0));
        }
        return true;
    }

    /**
     * Adds one key. A key already claimed by another skill is left alone rather than overwritten,
     * so resolution does not depend on the order rows came back in — the alias uniqueness
     * constraint means this should be unreachable, and "should be unreachable" is not a reason to
     * make the outcome depend on row order.
     *
     * @return how many words the key spans
     */
    private static int put(Map<String, CatalogSkill> bySlug, String key, CatalogSkill skill) {
        if (key == null || key.isEmpty()) {
            return 1;
        }
        bySlug.putIfAbsent(key, skill);
        return key.split("-").length;
    }
}
