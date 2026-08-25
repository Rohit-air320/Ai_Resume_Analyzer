package com.resumeiq.support;

import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.jobdescription.parse.JobPostingParser;
import com.resumeiq.jobdescription.parse.PostingInsight;
import com.resumeiq.skill.CatalogSkill;
import com.resumeiq.skill.SkillCategory;
import com.resumeiq.skill.SkillIndex;

import java.util.List;

/**
 * The two documents every Phase 6 test compares, and the catalogue they are read against.
 *
 * <p>Written once and shared because the interesting assertions in this phase are about differences
 * between inputs — a strong resume against a thin one, a posting that states years against one that
 * only implies seniority — and a test that builds its own resume inline is a test whose expected
 * scores nobody can compare with the test above it.
 *
 * <p>Everything here is a pure function over string literals. No database, no Spring context, no
 * network: {@link com.resumeiq.analysis.ResumeAnalyzer#analyseWith} takes its catalogue as an
 * argument precisely so this is possible, and it is why the tests for the whole analysis pipeline
 * run in milliseconds.
 */
public final class AnalysisFixtures {

    /**
     * The catalogue.
     *
     * <p>Ten skills rather than the real seed list. Small enough that an expected gap list can be
     * written out by hand and checked by eye, and containing exactly the shapes the scoring cares
     * about: skills across several categories, one two-word name, and skills deliberately left out
     * of each resume so there is always something to be missing.
     */
    public static final SkillIndex CATALOGUE = SkillIndex.of(List.of(
            new CatalogSkill("java", "Java", SkillCategory.LANGUAGE),
            new CatalogSkill("python", "Python", SkillCategory.LANGUAGE),
            new CatalogSkill("spring-boot", "Spring Boot", SkillCategory.FRAMEWORK),
            new CatalogSkill("react", "React", SkillCategory.FRAMEWORK),
            new CatalogSkill("mysql", "MySQL", SkillCategory.DATABASE),
            new CatalogSkill("docker", "Docker", SkillCategory.DEVOPS),
            new CatalogSkill("kubernetes", "Kubernetes", SkillCategory.DEVOPS),
            new CatalogSkill("amazon-web-services", "Amazon Web Services", SkillCategory.CLOUD),
            new CatalogSkill("junit", "JUnit", SkillCategory.TESTING),
            new CatalogSkill("git", "Git", SkillCategory.TOOLING)));

    /** How many keywords the fixture postings are parsed for. Matches the test posting limits. */
    public static final int MAX_KEYWORDS = 25;

    /** The role every fixture posting is for. */
    public static final String ROLE = "Backend Engineer";

    /**
     * A posting that states everything: a years figure, required skills and preferred skills.
     *
     * <p>Docker, Kubernetes and AWS are named here and absent from every resume fixture, which is
     * what gives the gap list, the keyword suggestions and the project recommendations something
     * real to work from.
     */
    public static final String POSTING = """
            Backend Engineer

            About the role
            Northwind runs logistics software for regional carriers.

            Requirements
            4+ years building backend services with Java and Spring Boot.
            Strong MySQL schema design and query tuning.
            Experience writing tests with JUnit.
            Comfortable with Git and code review.

            Nice to have
            Docker, Kubernetes and Amazon Web Services.
            Exposure to React for internal tooling.
            """;

    /**
     * The same posting with the years figure removed and the seniority left in the title.
     *
     * <p>This is the case that would have thrown a {@code NullPointerException}: a demand can be
     * stated — a level was read from the word "Senior" — and still carry no number.
     */
    public static final String POSTING_WITHOUT_YEARS = """
            Senior Backend Engineer

            Requirements
            Deep experience with Java and Spring Boot.
            Strong MySQL schema design.
            """;

    /** A posting with no technology in it at all, so nothing about skills can be measured. */
    public static final String POSTING_WITHOUT_SKILLS = """
            Operations Coordinator

            Requirements
            Excellent written communication and a calm manner under pressure.
            Comfortable owning a process end to end and improving it as you go.
            """;

    /**
     * A well-formed resume: contact details, bullets, numbers, and skills demonstrated in context.
     *
     * <p>Java, Spring Boot, MySQL, JUnit and Git appear inside roles and projects rather than only
     * in the skills list, which is the distinction the section reviewer and the skill comparison
     * both turn on. Docker, Kubernetes, AWS and React are absent on purpose.
     */
    public static final String STRONG_RESUME = """
            Priya Raman
            priya.raman@example.test | +91 98765 43210 | github.com/priyaraman

            Summary
            Backend engineer with 5 years building payment and logistics services.

            Skills
            Java, Spring Boot, MySQL, JUnit, Git, Python

            Experience
            Senior Engineer, Meridian Logistics (2021 - present)
            - Built 14 REST services in Java and Spring Boot serving 2.3 million requests a day.
            - Cut p99 latency by 41% by reworking 6 MySQL queries and adding covering indexes.
            - Raised JUnit coverage from 38% to 82% across the settlement module.

            Engineer, Castoreum Systems (2019 - 2021)
            - Shipped a reconciliation job in Python that closed 900 open items a month.
            - Reviewed roughly 30 pull requests a week using Git and trunk-based development.

            Projects
            Ledger Reconciler - a Java and MySQL service that matches 40,000 daily transactions.

            Education
            B.E. Computer Engineering, Pune University, 2019
            """;

    /**
     * A resume that measures badly in every way the engine can measure.
     *
     * <p>No email, no phone, no link, no bullets, no numbers, no projects, and skills asserted in a
     * list and never demonstrated. Useful for the neutral and floor branches, and for checking that
     * the advice for a weak resume is specific rather than a list of everything.
     */
    public static final String THIN_RESUME = """
            Rahul Mehta

            Skills
            Java, MySQL

            Experience
            Worked at a startup on some backend features and helped with the database.
            Also helped other teams when they needed something done quickly.

            Education
            B.Sc. Computer Science
            """;

    /**
     * A resume with no experience or projects section at all.
     *
     * <p>The case where keyword advice has nowhere honest to go: there is no section in which a
     * suggested term could be used truthfully, so the offline writer declines to suggest any.
     */
    public static final String SKILLS_ONLY_RESUME = """
            Anjali Nair
            anjali.nair@example.test

            Skills
            Java, MySQL, Git

            Education
            B.Tech Information Technology, 2024
            """;

    private AnalysisFixtures() {
    }

    /** Parses one of the posting fixtures against {@link #CATALOGUE}. */
    public static PostingInsight posting(String text) {
        return posting(text, ROLE);
    }

    /** Parses a posting under an explicit title, for the seniority-from-the-title cases. */
    public static PostingInsight posting(String text, String title) {
        return JobPostingParser.parseWith(text, title, CATALOGUE, MAX_KEYWORDS);
    }

    /** The full computed findings for a resume against {@link #POSTING}. */
    public static AnalysisFacts facts(String resumeText) {
        return facts(resumeText, POSTING, ROLE);
    }

    /** The full computed findings for any pairing. */
    public static AnalysisFacts facts(String resumeText, String postingText, String title) {
        return AnalysisFacts.from(resumeText, posting(postingText, title), title, CATALOGUE);
    }
}
