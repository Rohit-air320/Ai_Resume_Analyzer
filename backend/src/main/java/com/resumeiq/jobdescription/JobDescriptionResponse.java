package com.resumeiq.jobdescription;

import java.time.Instant;
import java.util.UUID;

/**
 * One job description, as a client sees it.
 *
 * <p>Absent, as everywhere in this API: the row id, the owning user, and the content hash. The hash
 * in particular is internal — it is a de-duplication key, and a client that could see it could
 * probe whether a posting already exists on another account.
 *
 * <p>{@code text} is present on the single-posting endpoint and absent from lists, in the same way
 * and for the same reason as {@code ResumeResponse.textPreview}: the list is built from a projection
 * with no accessor for the {@code LONGTEXT} column, so listing fifty postings cannot read fifty
 * postings' worth of text even by accident, and Jackson omits the null.
 *
 * <p>Unlike a resume, the full text <em>is</em> returned rather than an excerpt. The difference is
 * real and worth being clear about. A resume's original file is a binary this API never serves,
 * because serving user-uploaded bytes from the API's own origin is how a crafted PDF becomes stored
 * XSS. A job description is plain text the user pasted in themselves, and handing it back is the
 * feature — reading an analysis from six weeks ago is worth very little if you cannot see the
 * posting it was measured against.
 *
 * @param id        public identifier, the only one a client ever sees
 * @param title     role title, as the user typed it
 * @param company   company, when they gave one
 * @param text      the posting, on the single-posting endpoint and on create; never in a list
 * @param insight   what the parser read out of it — skills, keywords, seniority. Computed on every
 *                  read rather than stored, so a posting saved last month benefits from a skill
 *                  added to the catalogue last week
 * @param createdAt when it was first saved. On a re-paste this is the original date, not today's,
 *                  because it is the same posting and pretending otherwise would reorder the list
 *                  under the user for no reason
 */
public record JobDescriptionResponse(
        UUID id,
        String title,
        String company,
        String text,
        PostingInsightResponse insight,
        Instant createdAt
) {

    /** For list responses, from the projection that cannot see the text column. */
    public static JobDescriptionResponse from(JobDescriptionSummary summary) {
        return new JobDescriptionResponse(
                summary.getPublicId(),
                summary.getTitle(),
                summary.getCompany(),
                null,
                null,
                summary.getCreatedAt());
    }

    /** For create and single-posting responses, with the text and the parse. */
    public static JobDescriptionResponse from(JobDescription posting, PostingInsightResponse insight) {
        return new JobDescriptionResponse(
                posting.getPublicId(),
                posting.getTitle(),
                posting.getCompany(),
                posting.getRawText(),
                insight,
                posting.getCreatedAt());
    }
}
