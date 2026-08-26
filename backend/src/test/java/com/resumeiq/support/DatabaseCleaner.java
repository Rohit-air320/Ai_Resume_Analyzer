package com.resumeiq.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Empties every table a test can write to, in an order the foreign keys allow.
 *
 * <p>Integration tests here commit, and Spring caches an application context across every class that
 * asks for the same configuration — so the rows one class leaves behind are the rows the next class
 * starts with. Each class therefore clears up front rather than trusting a rollback that never
 * happens.
 *
 * <p>This exists because the per-class version of that list was wrong once and was going to be wrong
 * again. {@code ProfileApiTest} deleted only {@code refresh_tokens} and {@code users}, which is a
 * complete list of the tables it writes to and an incomplete list of the tables its neighbours in the
 * same cached context write to: the analyses left behind by the class before it still referenced those
 * users, and the delete failed on a foreign key. The order matters and cannot be worked out from the
 * one class you happen to be reading, so it is written down once, here.
 *
 * <p>The {@code skills} table is deliberately absent. It is seeded by an {@code ApplicationRunner}
 * that runs once per context, so emptying the catalogue would empty it for every test that follows and
 * the skill matches those tests depend on would quietly become misses — a failure that would look like
 * a scoring bug rather than a cleanup bug.
 */
public final class DatabaseCleaner {

    /**
     * Children first, then the documents, then tokens, then accounts. Every foreign key in the schema
     * points from a row further down this list to a row further up it, so deleting in this order never
     * orphans anything and never needs constraints switched off.
     */
    private static final List<String> TABLES = List.of(
            "recommendations",
            "analysis_skills",
            "analysis_keywords",
            "analysis_section_scores",
            "analysis_score_notes",
            "analyses",
            "job_descriptions",
            "resumes",
            "refresh_tokens",
            "users");

    private DatabaseCleaner() {
    }

    /** Deletes every row of test data, leaving the seeded reference tables in place. */
    public static void clear(JdbcTemplate jdbc) {
        for (String table : TABLES) {
            jdbc.update("delete from " + table);
        }
    }
}
