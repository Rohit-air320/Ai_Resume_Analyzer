package com.resumeiq.analysis;

/**
 * How well one required skill is evidenced in the resume.
 *
 * <p>Three values rather than a boolean, because "present or absent" is the wrong model for a
 * resume. A resume that mentions Docker once in a project bullet is not equivalent to one with
 * two years of Docker in a job description, and telling that person to "add Docker" is advice
 * they will rightly ignore.
 */
public enum SkillStatus {

    /** Clearly evidenced: named, and backed by experience or a project. */
    STRONG,

    /** Mentioned, but thinly — a bare keyword in a skills list, or a single passing reference. */
    PARTIAL,

    /** Required by the job description and not found in the resume at all. */
    MISSING
}
