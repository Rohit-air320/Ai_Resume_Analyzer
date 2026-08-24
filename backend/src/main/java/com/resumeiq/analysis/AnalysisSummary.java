package com.resumeiq.analysis;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the analysis history list.
 *
 * <p>Flat on purpose: the resume label and the job title are pulled across the joins into this
 * shape by an explicit query, rather than exposing nested projections that would each trigger
 * their own lazy load while the list renders. Neither the resume text, the posting text, nor the
 * raw AI payload can appear here.
 */
public interface AnalysisSummary {

    UUID getPublicId();

    AnalysisStatus getStatus();

    Integer getOverallScore();

    Integer getAtsScore();

    Integer getJobMatchScore();

    String getJobTitle();

    String getCompany();

    String getResumeLabel();

    Instant getCreatedAt();

    Instant getCompletedAt();
}
