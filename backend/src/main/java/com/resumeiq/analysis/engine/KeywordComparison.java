package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.KeywordKind;
import com.resumeiq.common.text.TextTokenizer;
import com.resumeiq.common.text.TextTokenizer.Token;
import com.resumeiq.jobdescription.parse.Keyword;
import com.resumeiq.jobdescription.parse.PostingInsight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which of a posting's important terms the resume already uses.
 *
 * <p>Keywords are the part of this product most easily turned into something harmful, so the rules
 * are narrow on purpose. A term is reported as absent, with a suggestion of where it would honestly
 * belong; it is never reported as "add this word". The difference matters: a list of words to insert
 * is a keyword-stuffing instruction, and a resume that has been stuffed reads badly to the human who
 * makes the actual decision.
 *
 * <h2>Matching is phrase-aware and stem-tolerant</h2>
 *
 * <p>The posting's keywords are multi-word phrases as often as not ("code review", "distributed
 * systems"), so matching is done over the resume's token stream rather than with {@code contains} —
 * the same reason the skill scanner tokenises. A light stem comparison then absorbs the difference
 * between "test" and "testing", "deploy" and "deployment", which is the single most common false
 * gap: a resume saying "deployed services weekly" plainly covers a posting asking for "deployment".
 */
public final class KeywordComparison {

    /**
     * Suffixes stripped before comparing two words.
     *
     * <p>Longest first, so "-ing" is tried before "-g" would be relevant, and short words are left
     * alone: stemming "aws" or "api" achieves nothing and risks collisions.
     */
    private static final List<String> SUFFIXES = List.of("ations", "ation", "ements", "ement",
            "ings", "ing", "ities", "ity", "ers", "er", "ies", "ed", "es", "s");

    /** Below this length a word is not stemmed: too little left to be meaningfully compared. */
    private static final int MIN_STEM_LENGTH = 5;

    private KeywordComparison() {
    }

    /**
     * Judges every keyword the posting ranked.
     *
     * @param posting the parsed posting, whose keywords are already ranked and capped
     * @param resume  the parsed resume
     * @return one verdict per posting keyword, matched ones first, each in the posting's ranked order
     */
    public static List<KeywordVerdict> compare(PostingInsight posting, ResumeInsight resume) {
        Set<String> resumeStems = stems(resume.text());
        List<KeywordVerdict> verdicts = new ArrayList<>(posting.keywords().size());
        for (Keyword keyword : posting.keywords()) {
            boolean present = covers(resumeStems, keyword.term());
            verdicts.add(new KeywordVerdict(keyword.term(), keyword.occurrences(), keyword.score(),
                    present ? KeywordKind.MATCHED : KeywordKind.ABSENT));
        }
        verdicts.sort(Comparator
                .comparingInt((KeywordVerdict verdict) -> verdict.kind() == KeywordKind.MATCHED ? 0 : 1)
                .thenComparingInt(verdict -> -verdict.weight()));
        return List.copyOf(verdicts);
    }

    /**
     * True when the resume uses every word of the phrase.
     *
     * <p>Every word, not the phrase in order. "Reviewed code for six engineers" covers "code review"
     * and it would be pedantic to say otherwise — the claim being made is "your resume talks about
     * this", not "your resume contains this exact string". Requiring all the words is what keeps that
     * from becoming meaningless: a resume mentioning "code" and, elsewhere, "review" of designs is
     * the boundary case, and accepting it is the friendlier error to make when the consequence is
     * only whether a suggestion is offered.
     */
    private static boolean covers(Set<String> resumeStems, String term) {
        List<Token> tokens = TextTokenizer.tokens(term);
        if (tokens.isEmpty()) {
            return false;
        }
        for (Token token : tokens) {
            if (!resumeStems.contains(stem(token.key()))) {
                return false;
            }
        }
        return true;
    }

    /** Every distinct stem in a document. Built once per analysis rather than per keyword. */
    private static Set<String> stems(String text) {
        Set<String> found = new HashSet<>();
        for (String segment : TextTokenizer.segments(text)) {
            for (Token token : TextTokenizer.tokens(segment)) {
                found.add(stem(token.key()));
            }
        }
        return found;
    }

    /**
     * A deliberately crude stem: strip one known suffix, keep at least four characters.
     *
     * <p>Not a linguistic stemmer, and it does not need to be. It has one job — stop "testing" and
     * "tests" from being counted as different words — and a real stemmer would bring a dependency, a
     * language assumption and a set of surprising outputs for the sake of a comparison whose worst
     * failure is an unnecessary suggestion.
     */
    static String stem(String word) {
        if (word == null || word.length() < MIN_STEM_LENGTH) {
            return word == null ? "" : word;
        }
        for (String suffix : SUFFIXES) {
            if (word.endsWith(suffix) && word.length() - suffix.length() >= 4) {
                return word.substring(0, word.length() - suffix.length());
            }
        }
        return word;
    }
}
