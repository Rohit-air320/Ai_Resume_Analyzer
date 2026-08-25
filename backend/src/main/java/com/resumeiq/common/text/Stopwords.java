package com.resumeiq.common.text;

import java.util.Set;

/**
 * Words that are never worth suggesting.
 *
 * <p>Two lists in one, and the second is the interesting half.
 *
 * <p>The first is ordinary English function words. Without them the "important keywords" for every
 * posting on earth begin with "and", "the" and "with", which is the classic bag-of-words failure
 * and makes a feature look broken at a glance.
 *
 * <p>The second is job-posting boilerplate: "responsibilities", "candidate", "opportunity",
 * "passionate", "fast-paced", "team player". These are not noise in the statistical sense — they
 * are genuinely frequent, and frequency-based ranking loves them. They are noise in the sense that
 * matters: telling someone to add "passionate" and "fast-paced" to their resume is advice that
 * would make it worse, and it is exactly the advice a naive keyword extractor gives with total
 * confidence. Every entry here is a word this product refuses to recommend.
 *
 * <p>Deliberately absent: anything that is also a technology. "Go", "React", "Rust" and "Less" are
 * ordinary words too, and they are handled where that ambiguity belongs — the capitalisation rules
 * in {@code SkillIndex} — rather than by being silenced here.
 */
public final class Stopwords {

    private static final Set<String> ENGLISH = Set.of(
            "a", "about", "above", "across", "after", "again", "against", "all", "also", "am",
            "an", "and", "another", "any", "are", "around", "as", "at", "back", "be", "because",
            "been", "before", "being", "below", "best", "better", "between", "both", "but", "by",
            "can", "could", "did", "do", "does", "doing", "done", "down", "during", "each",
            "either", "else", "enough", "etc", "even", "every", "few", "for", "from", "further",
            "get", "give", "had", "has", "have", "having", "he", "her", "here", "hers", "him",
            "his", "how", "however", "if", "in", "include", "includes", "including", "into", "is",
            "it", "its", "itself", "just", "keep", "know", "like", "made", "make", "many", "may",
            "me", "might", "more", "most", "much", "must", "my", "need", "needs", "no", "nor",
            "not", "now", "of", "off", "on", "once", "one", "only", "or", "other", "others", "our",
            "ours", "out", "over", "own", "per", "same", "she", "should", "since", "so", "some",
            "such", "take", "than", "that", "the", "their", "theirs", "them", "then", "there",
            "these", "they", "this", "those", "through", "to", "too", "under", "until", "up",
            "upon", "us", "use", "used", "using", "very", "via", "want", "was", "we", "well",
            "were", "what", "when", "where", "whether", "which", "while", "who", "whom", "why",
            "will", "with", "within", "without", "would", "you", "your", "yours"
    );

    /**
     * Words that fill job postings and mean nothing on a resume.
     *
     * <p>Grouped roughly by what they are: the vocabulary of postings themselves, the vocabulary
     * of hiring, and the adjectives every company uses about itself. A resume that adopts this
     * language says nothing a reader can verify, which is the opposite of what the improvement
     * advice in this product is for.
     */
    private static final Set<String> POSTING_BOILERPLATE = Set.of(
            // The posting talking about itself
            "role", "roles", "job", "jobs", "position", "positions", "posting", "vacancy",
            "requirement", "requirements", "required", "responsibility", "responsibilities",
            "qualification", "qualifications", "description", "overview", "summary", "duties",
            "preferred", "essential", "minimum", "plus", "bonus", "ideally", "nice",
            // Hiring vocabulary
            "candidate", "candidates", "applicant", "applicants", "apply", "application",
            "applications", "hire", "hiring", "recruiter", "recruiting", "interview", "resume",
            "cv", "employer", "employment", "employee", "employees", "salary", "compensation",
            "benefits", "insurance", "holiday", "vacation", "remote", "hybrid", "onsite",
            "office", "location", "opportunity", "opportunities", "career", "careers", "join",
            "joining", "looking", "seeking", "offer", "offers",
            // The adjectives every company uses about itself
            "passionate", "passion", "dynamic", "exciting", "innovative", "cutting", "edge",
            "world", "class", "leading", "leader", "fast", "paced", "rockstar", "ninja", "guru",
            "hungry", "driven", "motivated", "enthusiastic", "talented", "amazing", "awesome",
            "great", "excellent", "strong", "solid", "proven", "track", "record", "ability",
            "able", "willing", "eager",
            // Words that describe work without describing any work
            "work", "working", "works", "team", "teams", "company", "companies", "business",
            "organisation", "organization", "environment", "culture", "people", "person",
            "colleagues", "stakeholder", "stakeholders", "customer", "customers", "client",
            "clients", "user", "users", "project", "projects", "task", "tasks", "day", "days",
            "week", "weeks", "month", "months", "year", "years", "time", "level", "part",
            "full", "based", "new", "help", "support", "ensure", "ensuring", "responsible",
            "experience", "experienced", "skill", "skills", "knowledge", "understanding",
            "familiarity", "familiar", "comfortable", "proficiency", "proficient", "expertise",
            "communication", "written", "verbal", "interpersonal", "detail",
            "oriented", "player", "self", "starter", "plusses"
    );

    private Stopwords() {
    }

    /**
     * True when a slugged word should never be offered as a keyword.
     *
     * @param key a single slugged word, as produced by {@link Slug}
     */
    public static boolean contains(String key) {
        return key == null || key.isBlank()
                || ENGLISH.contains(key) || POSTING_BOILERPLATE.contains(key);
    }

    /** How many words are silenced. Exposed so a test can notice the list being gutted. */
    public static int size() {
        return ENGLISH.size() + POSTING_BOILERPLATE.size();
    }
}
