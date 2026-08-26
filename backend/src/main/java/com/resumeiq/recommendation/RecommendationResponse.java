package com.resumeiq.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * One recommendation on the cross-analysis feed.
 *
 * <p>The same advice that appears inside an analysis, listed across all of them and carrying the
 * context an analysis page does not have to state: which posting it came from, and when. That context
 * is the whole reason this endpoint exists. "Learn Docker" is advice; "learn Docker, from the Senior
 * Backend Engineer analysis you ran on Tuesday" is advice somebody can act on, and without the job
 * title the feed is a list of imperatives with no provenance.
 *
 * <p>Built from a projection rather than from {@link Recommendation} entities. A feed of thirty rows
 * mapped from entities would lazy-load thirty analyses and thirty postings to read two strings off
 * each — the textbook N+1, and the kind that only shows up in production where the feed is long.
 *
 * @param analysisId the analysis this came from, so the client can link to the full result
 */
@Schema(description = "A recommendation, with the analysis it came from")
public record RecommendationResponse(
        UUID analysisId,
        RecommendationType type,
        String title,
        String detail,
        Priority priority,
        String resourceUrl,
        String jobTitle,
        Instant createdAt
) {

    public static RecommendationResponse from(RecommendationFeedItem item) {
        return new RecommendationResponse(
                item.getAnalysisId(),
                item.getType(),
                item.getTitle(),
                item.getDetail(),
                item.getPriority(),
                item.getResourceUrl(),
                item.getJobTitle(),
                item.getCreatedAt());
    }
}
