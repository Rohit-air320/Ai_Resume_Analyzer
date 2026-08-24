package com.resumeiq.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One section's score and the note explaining it.
 *
 * <p>{@code @Embeddable} rather than an entity: a section score has no life of its own, is
 * never queried on its own, and dies with its analysis. Modelling it as an entity would add a
 * primary key, a repository and a cascade rule to describe a value.
 *
 * <p>The note is not optional in practice. A score of 54 on {@code EXPERIENCE} tells the user
 * nothing; "three of five roles describe duties rather than outcomes" tells them what to edit.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SectionAssessment {

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 30)
    private ResumeSection section;

    /** 0–100, on the same scale as every other score in the system. */
    @Column(name = "score", nullable = false)
    private int score;

    /** Why the section scored what it did, in one or two sentences. */
    @Column(name = "note", length = 400)
    private String note;
}
