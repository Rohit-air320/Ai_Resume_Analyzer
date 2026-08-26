package com.resumeiq.analysis;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Ask for an analysis of one resume against one posting.
 *
 * <p>Two identifiers and nothing else. Both are public ids of rows the caller already owns, which is
 * the whole request: no resume text, no posting text, no scores, no user id. A client that could send
 * the text would be a client that could send <em>different</em> text from what was uploaded, and then
 * the stored analysis would be about a document nobody can retrieve.
 *
 * <p>The posting is saved first, through {@code POST /api/job-descriptions}, which is what makes the
 * common case cheap: pasting the same description again returns the row that already exists, so
 * re-analysing an edited resume against Monday's job posting keeps the whole history on one posting.
 */
@Schema(description = "A request to score one resume against one saved job description")
public record CreateAnalysisRequest(

        @Schema(description = "Public id of a resume you have uploaded",
                example = "6f1c1c0a-3d3f-4a20-9f2a-1f6c9d1a0b11")
        @NotNull(message = "Choose a resume to analyse.")
        UUID resumeId,

        @Schema(description = "Public id of a job description you have saved",
                example = "8a2d3e4f-5b6c-4d7e-8f90-1a2b3c4d5e6f")
        @NotNull(message = "Choose a job description to analyse against.")
        UUID jobDescriptionId
) {
}
