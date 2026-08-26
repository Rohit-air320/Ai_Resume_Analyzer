package com.resumeiq.recommendation;

import java.time.Instant;
import java.util.UUID;

/**
 * A recommendation joined to the analysis and posting it belongs to.
 *
 * <p>A Spring Data interface projection, so the query selects nine columns rather than hydrating three
 * entity graphs. The alternative — reading the entities and walking {@code recommendation.getAnalysis()
 * .getJobDescription().getTitle()} — is two extra selects per row, and there is no annotation that
 * fixes it once the collection is already loaded.
 *
 * <p>Note what is absent: no user id, no resume text, no posting text and no analysis feedback. A
 * projection is also a way of proving that a query cannot leak a column it does not name.
 */
public interface RecommendationFeedItem {

    UUID getAnalysisId();

    RecommendationType getType();

    String getTitle();

    String getDetail();

    Priority getPriority();

    String getResourceUrl();

    /** The posting's title, which is what gives a recommendation its context on the feed. */
    String getJobTitle();

    Instant getCreatedAt();
}
