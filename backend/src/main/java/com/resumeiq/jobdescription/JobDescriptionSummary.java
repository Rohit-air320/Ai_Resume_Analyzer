package com.resumeiq.jobdescription;

import java.time.Instant;
import java.util.UUID;

/**
 * Job description without its text, for lists and for the header of an analysis.
 *
 * <p>Same reasoning as the resume projection: a history page needs the title, the company and
 * the date, and has no business pulling several kilobytes of posting text per row to render
 * them.
 */
public interface JobDescriptionSummary {

    UUID getPublicId();

    String getTitle();

    String getCompany();

    Instant getCreatedAt();
}
