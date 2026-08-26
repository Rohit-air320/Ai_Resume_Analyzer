package com.resumeiq.analysis;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of analysis history.
 *
 * <p>Built from the {@link AnalysisSummary} projection, so listing fifty analyses does not load fifty
 * {@code LONGTEXT} feedback columns, fifty skill collections and fifty recommendation collections to
 * render a table that shows three numbers and a job title.
 *
 * <p>Three scores rather than six. The history table exists to answer "am I getting better", and the
 * two component scores that move independently of the overall — ATS, which is about the document, and
 * job match, which is about the fit — are the two worth a column. The rest are on the detail page,
 * one click away.
 *
 * @param id           the analysis
 * @param status       COMPLETED for anything with scores
 * @param overallScore null until the analysis completes
 * @param jobTitle     the posting's title, so a row is identifiable without opening it
 * @param resumeLabel  which version of the resume this was
 */
@Schema(description = "An analysis as it appears in a list")
public record AnalysisSummaryResponse(
        UUID id,
        AnalysisStatus status,
        Integer overallScore,
        Integer atsScore,
        Integer jobMatchScore,
        String jobTitle,
        String company,
        String resumeLabel,
        Instant createdAt,
        Instant completedAt
) {

    public static AnalysisSummaryResponse from(AnalysisSummary summary) {
        return new AnalysisSummaryResponse(
                summary.getPublicId(),
                summary.getStatus(),
                summary.getOverallScore(),
                summary.getAtsScore(),
                summary.getJobMatchScore(),
                summary.getJobTitle(),
                summary.getCompany(),
                summary.getResumeLabel(),
                summary.getCreatedAt(),
                summary.getCompletedAt());
    }
}
