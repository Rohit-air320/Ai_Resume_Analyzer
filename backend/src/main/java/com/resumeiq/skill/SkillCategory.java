package com.resumeiq.skill;

/**
 * How a skill is grouped in the skill-gap view.
 *
 * <p>The categories are the ones a hiring conversation actually uses, and they exist to make
 * a gap list readable: five missing cloud skills is a different problem from five missing
 * languages, and the radar chart in Phase 9 plots one axis per category.
 */
public enum SkillCategory {

    /** Java, Python, TypeScript, SQL as a language. */
    LANGUAGE,

    /** Spring Boot, React, Django — things you build inside. */
    FRAMEWORK,

    /** MySQL, PostgreSQL, MongoDB, Redis. */
    DATABASE,

    /** AWS, GCP, Azure and their managed services. */
    CLOUD,

    /** Docker, Kubernetes, CI/CD, Terraform, observability. */
    DEVOPS,

    /** JUnit, Jest, Playwright, and testing practice generally. */
    TESTING,

    /** Git, Maven, Postman, IDEs — the daily toolchain. */
    TOOLING,

    /** Pandas, TensorFlow, LLM APIs, data pipelines. */
    DATA_AI,

    /** Android, iOS, React Native, Flutter. */
    MOBILE,

    /**
     * REST design, system design, OOP, data structures. Things a resume claims and an
     * interview probes, which are not libraries but are absolutely job requirements.
     */
    CONCEPT,

    /**
     * Communication, ownership, mentoring. Detected far less reliably than the rest, which is
     * why the analysis treats a missing soft skill as advice rather than as a hard gap.
     */
    SOFT_SKILL
}
