package com.resumeiq.analysis;

/**
 * The resume sections the analysis scores individually.
 *
 * <p>A closed enum rather than free-text section names, because the section breakdown is a
 * chart: if the AI is allowed to invent a section name, the chart gains an axis on some runs
 * and loses it on others, and two analyses of the same resume stop being comparable. Phase 6's
 * prompt sends exactly this list and the response is rejected if it answers with anything else.
 */
public enum ResumeSection {

    /** Name, email, phone, location, links. Missing detail here is an instant ATS problem. */
    CONTACT,

    /** Headline or professional summary. */
    SUMMARY,

    /** The skills list itself — coverage and how it is organised. */
    SKILLS,

    /** Work history: scope, seniority signals, and whether impact is quantified. */
    EXPERIENCE,

    /** Projects, which carry most of the weight on an early-career resume. */
    PROJECTS,

    /** Degrees, institutions, dates. */
    EDUCATION,

    /** Certifications and licences. */
    CERTIFICATIONS,

    /**
     * Layout, ordering and parseability rather than content: tables, columns, images and
     * unusual headings are what actually break an ATS parser.
     */
    FORMATTING
}
