package com.resumeiq.jobdescription.parse;

import com.resumeiq.common.text.Stopwords;
import com.resumeiq.common.text.TextTokenizer;
import com.resumeiq.common.text.TextTokenizer.Token;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ranks the terms a posting leans on, excluding the skills already reported.
 *
 * <p>Four decisions make the difference between a useful list and the word cloud that every
 * frequency counter produces.
 *
 * <h2>Detected skills are excluded, and so are their words</h2>
 *
 * <p>If the skills list already says "Spring Boot", then "Spring" and "Boot" as keywords add
 * nothing and make the API look like it is padding. The exclusion covers the slug and each word
 * inside it, so a two-word skill silences both halves.
 *
 * <h2>The perks section scores zero</h2>
 *
 * <p>Weighting comes from {@link PostingSection#keywordWeight()}, where benefits and company
 * boilerplate are worth nothing at all. That is what keeps "insurance", "equity" and the name of
 * the company's yoga provider out of a list headed "keywords to include".
 *
 * <h2>Pairs are counted, and they consume their halves</h2>
 *
 * <p>"Distributed systems", "unit testing" and "code review" mean something that neither of their
 * words means alone. A pair seen at least twice is kept as one term, and its count is then
 * subtracted from each of its words — so a word that only ever appeared inside a kept pair
 * disappears on its own, without a special case. Pairs seen once are dropped, because two words
 * next to each other once is usually a sentence rather than a term.
 *
 * <h2>The order is fully determined</h2>
 *
 * <p>Score, then occurrences, then alphabetically. The last tiebreak matters more than it looks:
 * without it, two terms with identical counts swap places between runs, which reaches the API and
 * makes the endpoint impossible to write an honest test against.
 */
public final class KeywordExtractor {

    /** A single word shorter than this is not a term anyone searches for. */
    private static final int MIN_WORD_LENGTH = 3;

    /** Inside a pair, a two-letter word is fine — "UX design", "QA process". */
    private static final int MIN_PAIRED_WORD_LENGTH = 2;

    /** A pair has to happen twice before it is treated as a phrase rather than a coincidence. */
    private static final int MIN_PAIR_OCCURRENCES = 2;

    private KeywordExtractor() {
    }

    /**
     * @param blocks  the posting, split into sections
     * @param skills  what {@link SkillMatcher} already found, so keywords do not repeat it
     * @param maxTerms how many to return, from configuration. A cap on advice: a checklist of two
     *                 hundred keywords is not something anyone can act on, and presenting one is
     *                 how a tool ends up encouraging keyword stuffing.
     */
    public static List<Keyword> extract(List<PostingBlock> blocks, List<DetectedSkill> skills,
                                        int maxTerms) {
        Set<String> excluded = excludedKeys(skills);
        Map<String, Term> singles = new LinkedHashMap<>();
        Map<String, Term> pairs = new LinkedHashMap<>();

        for (PostingBlock block : blocks) {
            if (block.section().keywordWeight() == 0) {
                // Perks and boilerplate. Not weak evidence — no evidence.
                continue;
            }
            for (String segment : TextTokenizer.segments(block.text())) {
                count(TextTokenizer.tokens(segment), block, excluded, singles, pairs);
            }
        }

        List<Keyword> keywords = new ArrayList<>();
        for (Map.Entry<String, Term> pair : pairs.entrySet()) {
            Term term = pair.getValue();
            if (term.occurrences < MIN_PAIR_OCCURRENCES) {
                continue;
            }
            keywords.add(term.toKeyword());
            for (String half : pair.getKey().split("-")) {
                Term single = singles.get(half);
                if (single != null) {
                    single.subtract(term);
                }
            }
        }
        for (Term term : singles.values()) {
            if (term.occurrences > 0) {
                keywords.add(term.toKeyword());
            }
        }

        keywords.sort(Comparator
                .comparingInt((Keyword keyword) -> -keyword.score())
                .thenComparingInt(keyword -> -keyword.occurrences())
                .thenComparing(keyword -> keyword.term().toLowerCase(Locale.ROOT)));
        return List.copyOf(keywords.subList(0, Math.min(maxTerms, keywords.size())));
    }

    /** Every skill slug, plus each word inside it. */
    private static Set<String> excludedKeys(List<DetectedSkill> skills) {
        Set<String> excluded = new HashSet<>();
        for (DetectedSkill skill : skills) {
            excluded.add(skill.slug());
            excluded.addAll(List.of(skill.slug().split("-")));
        }
        return excluded;
    }

    private static void count(List<Token> tokens, PostingBlock block, Set<String> excluded,
                              Map<String, Term> singles, Map<String, Term> pairs) {
        for (int position = 0; position < tokens.size(); position++) {
            Token token = tokens.get(position);
            if (!isCountable(token, excluded, MIN_WORD_LENGTH)) {
                continue;
            }
            singles.computeIfAbsent(token.key(), key -> new Term())
                    .add(block, token.original());

            if (position + 1 < tokens.size()) {
                Token next = tokens.get(position + 1);
                if (isCountable(token, excluded, MIN_PAIRED_WORD_LENGTH)
                        && isCountable(next, excluded, MIN_PAIRED_WORD_LENGTH)) {
                    pairs.computeIfAbsent(TextTokenizer.keyOf(tokens, position, 2),
                                    key -> new Term())
                            .add(block, TextTokenizer.displayOf(tokens, position, 2));
                }
            }
        }
    }

    /**
     * A word counts when it is not a stopword, not part of a skill already reported, long enough
     * to be a term, and not a bare number — "5" and "2026" are frequent and never advice.
     */
    private static boolean isCountable(Token token, Set<String> excluded, int minLength) {
        String key = token.key();
        return key.length() >= minLength
                && !Character.isDigit(key.charAt(0))
                && !excluded.contains(key)
                && !Stopwords.contains(key);
    }

    /**
     * One term's running tally. Mutable for the same reason {@code SkillMatcher.Match} is: a
     * posting has thousands of tokens and rebuilding a record per token is work for nothing.
     */
    private static final class Term {

        private int occurrences;
        private int score;
        private PostingSection strongestSection = PostingSection.OTHER;
        private int strongestWeight = -1;
        private String display = "";

        private void add(PostingBlock block, String written) {
            occurrences++;
            score += block.section().keywordWeight();
            if (block.section().keywordWeight() > strongestWeight) {
                strongestWeight = block.section().keywordWeight();
                strongestSection = block.section();
            }
            display = preferred(display, written);
        }

        /** Removes a pair's contribution from one of its words. */
        private void subtract(Term pair) {
            occurrences -= pair.occurrences;
            score -= pair.score;
        }

        private Keyword toKeyword() {
            return new Keyword(display, occurrences, score, strongestSection);
        }

        /**
         * Picks how to spell the term back to the user. More capitals wins, so a posting that
         * writes "Kubernetes" in one place and "kubernetes" in another is quoted the way it meant
         * — and a term is never presented in a casing the posting never used.
         */
        private static String preferred(String current, String candidate) {
            if (current.isEmpty()) {
                return candidate;
            }
            return capitals(candidate) > capitals(current) ? candidate : current;
        }

        private static int capitals(String text) {
            int capitals = 0;
            for (int i = 0; i < text.length(); i++) {
                if (Character.isUpperCase(text.charAt(i))) {
                    capitals++;
                }
            }
            return capitals;
        }
    }
}
