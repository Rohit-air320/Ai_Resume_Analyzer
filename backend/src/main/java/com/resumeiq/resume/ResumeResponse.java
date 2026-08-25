package com.resumeiq.resume;

import java.time.Instant;
import java.util.UUID;

/**
 * One resume, as a client sees it.
 *
 * <p>Notice what is absent: the internal row id, the owning user, and the storage key.
 * The key in particular would be an invitation — a client that knows the name of a file
 * on the server will eventually try to ask for it. Outside the database a resume is known
 * only by its public UUID.
 *
 * <p>{@code textPreview} is populated only when a single resume is fetched, never when the
 * list is. That is not squeamishness about a field: the list is built from a projection
 * that has no accessor for the text column at all, so listing twenty resumes cannot read
 * twenty {@code LONGTEXT} values even by accident. Jackson is configured to omit nulls, so
 * the key simply is not there in list responses.
 *
 * @param id             public identifier, the only one a client ever sees
 * @param label          display name, defaulted from the filename at upload
 * @param originalFilename what the uploader called it. Display only — no path is ever built from this
 * @param contentType    the type we determined from the file's own bytes, not the one the request claimed
 * @param fileSizeBytes  size on disk
 * @param pageCount      pages, where the format reports them
 * @param wordCount      words of extracted text, which is the honest measure of how much we read
 * @param status         where this resume is in the pipeline
 * @param extractionError why the text could not be read, when it could not. Written for the user
 * @param analysable     whether this resume can be used in an analysis
 * @param textPreview    opening excerpt, on the single-resume endpoint only, so the owner can
 *                       confirm we read the document they meant
 * @param createdAt      upload time
 */
public record ResumeResponse(
        UUID id,
        String label,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        Integer pageCount,
        Integer wordCount,
        ResumeStatus status,
        String extractionError,
        boolean analysable,
        String textPreview,
        Instant createdAt
) {

    /** How much of the resume the owner is shown back, in characters. */
    public static final int PREVIEW_LENGTH = 320;

    /** For list responses, from the projection that cannot see the text column. */
    public static ResumeResponse from(ResumeSummary summary) {
        return new ResumeResponse(
                summary.getPublicId(),
                summary.getLabel(),
                summary.getOriginalFilename(),
                summary.getContentType(),
                summary.getFileSizeBytes(),
                summary.getPageCount(),
                summary.getWordCount(),
                summary.getStatus(),
                summary.getExtractionError(),
                analysable(summary.getStatus(), summary.getWordCount()),
                null,
                summary.getCreatedAt());
    }

    /** For the upload response, where there is no preview to give yet beyond what was read. */
    public static ResumeResponse from(Resume resume) {
        return from(resume, null);
    }

    /** For the single-resume endpoint, with an excerpt of what we read. */
    public static ResumeResponse from(Resume resume, String textPreview) {
        return new ResumeResponse(
                resume.getPublicId(),
                resume.getLabel(),
                resume.getOriginalFilename(),
                resume.getContentType(),
                resume.getFileSizeBytes(),
                resume.getPageCount(),
                resume.getWordCount(),
                resume.getStatus(),
                resume.getExtractionError(),
                resume.isAnalysable(),
                textPreview,
                resume.getCreatedAt());
    }

    /**
     * The projection deliberately cannot read the text column, so "is there text" is
     * answered from the word count instead — which is the same question asked of data we
     * already have.
     */
    private static boolean analysable(ResumeStatus status, Integer wordCount) {
        return status == ResumeStatus.TEXT_EXTRACTED && wordCount != null && wordCount > 0;
    }
}
