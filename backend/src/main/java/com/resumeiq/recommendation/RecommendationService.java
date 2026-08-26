package com.resumeiq.recommendation;

import com.resumeiq.security.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The recommendations feed.
 *
 * <p>Advice from every analysis the account has run, newest first, optionally narrowed to one type.
 * The point of the endpoint is the thing a single analysis cannot show: the same suggestion arriving
 * from four different postings is a much stronger signal than the same suggestion arriving once, and it
 * only becomes visible when the advice is read across analyses rather than inside one.
 *
 * <p>There is no user column on {@code recommendations} — a recommendation belongs to an analysis, and
 * the analysis belongs to a user — so ownership is a join, in the query, on both paths. It is never a
 * filter applied to results after the fact.
 */
@Service
public class RecommendationService {

    /**
     * How much of the feed is returned.
     *
     * <p>A cap, not a page. Every analysis writes roughly twenty rows here, so the table grows about
     * twenty times faster than the analysis table and is the first place an unbounded query would hurt.
     * A hundred is more than the screen shows and small enough that the worst case is a page of JSON
     * rather than a page of history.
     */
    private static final int FEED_LIMIT = 100;

    private final RecommendationRepository recommendations;

    public RecommendationService(RecommendationRepository recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * The caller's advice, newest first.
     *
     * <p>Two queries behind one method because the filtered form has to stay an indexed equality; see
     * {@link RecommendationRepository#findFeedForUserByType}. The branch is here rather than in the
     * controller so the endpoint has one entry point and the choice of query stays a data-access
     * detail.
     *
     * @param type null for everything, or one type to narrow to
     */
    @Transactional(readOnly = true)
    public List<RecommendationResponse> list(AuthenticatedUser caller, RecommendationType type) {
        Pageable page = PageRequest.of(0, FEED_LIMIT);
        List<RecommendationFeedItem> items = type == null
                ? recommendations.findFeedForUser(caller.id(), page)
                : recommendations.findFeedForUserByType(caller.id(), type, page);
        return items.stream().map(RecommendationResponse::from).toList();
    }
}
