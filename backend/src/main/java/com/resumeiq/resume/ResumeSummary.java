package com.resumeiq.resume;

import java.time.Instant;
import java.util.UUID;

/**
 * The safe shape of a resume for lists.
 *
 * <p>A closed interface projection: Spring Data generates a query selecting exactly these
 * columns, so {@code extracted_text} is not merely omitted from the response — it is never
 * read out of the database at all. Two things follow. The resume list cannot leak resume
 * contents even if someone later returns this object straight from a controller, and listing
 * twenty resumes stops meaning twenty megabytes of text through the JDBC driver.
 */
public interface ResumeSummary {

    UUID getPublicId();

    String getLabel();

    String getOriginalFilename();

    String getContentType();

    long getFileSizeBytes();

    Integer getPageCount();

    Integer getWordCount();

    ResumeStatus getStatus();

    String getExtractionError();

    Instant getCreatedAt();
}
