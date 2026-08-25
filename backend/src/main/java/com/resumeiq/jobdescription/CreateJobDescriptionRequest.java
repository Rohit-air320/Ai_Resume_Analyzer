package com.resumeiq.jobdescription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/job-descriptions}.
 *
 * <p>A posting is pasted, not uploaded. There is no file, no parser to fool and no format to sniff,
 * so the only thing that can be wrong with it is its length — which is why validation here is
 * shorter than anywhere else in the API.
 *
 * <p>The bound below is a hard ceiling, not the product's limit. The real limits are configurable
 * ({@code MIN_POSTING_CHARACTERS}, {@code MAX_POSTING_CHARACTERS}) and applied in
 * {@link JobDescriptionService}, where the message can quote the configured number back to the
 * person. This annotation exists underneath that as a structural guard: Bean Validation runs before
 * any of our code, so a hundred-megabyte body is refused without ever reaching a service, and the
 * guard survives someone setting the configured maximum to something reckless. Two checks, two
 * different jobs — the outer one protects the server, the inner one talks to the user.
 *
 * @param title   role title. Required, because it is how the person recognises this posting in a
 *                list six weeks later — "Backend Engineer at Acme", not a truncated paragraph
 * @param company optional; postings pasted from an email often have no company name in the text
 * @param text    the posting itself
 */
public record CreateJobDescriptionRequest(

        @NotBlank(message = "Job title is required")
        @Size(max = 160, message = "Job title must be at most 160 characters")
        String title,

        @Size(max = 160, message = "Company must be at most 160 characters")
        String company,

        @NotBlank(message = "Paste the job description")
        @Size(max = 60_000, message = "That job description is too long to process")
        String text
) {
}
