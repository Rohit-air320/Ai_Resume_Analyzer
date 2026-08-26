package com.resumeiq.analysis;

import com.resumeiq.jobdescription.JobDescriptionRepository;
import com.resumeiq.resume.ResumeRepository;
import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Assembles the dashboard.
 *
 * <h2>Five queries, one transaction, no loops</h2>
 *
 * <p>Everything on the screen is an aggregate the database computed: three counts, one row of
 * {@code count/avg/max}, the trend projection, the newest few summaries and a grouped count of gaps.
 * Nothing here reads an analysis to add something up, which is the property that decides whether this
 * endpoint still works for a user with four hundred analyses. Adding a widget should cost a query.
 *
 * <p>They run inside one read-only transaction rather than five, so the numbers on the screen are all
 * true at the same instant. That matters less for a dashboard than for a ledger, but a page that says
 * "3 analyses" above a list of four is the kind of bug that costs an afternoon.
 *
 * <h2>Why it is in this package</h2>
 *
 * <p>{@code /api/dashboard} is shaped like a screen, not like a resource, and the screen is almost
 * entirely about analyses. Giving it its own package would mean either publishing the mappers on
 * {@link DashboardResponse} or duplicating them, and neither buys anything — the resume and posting
 * counts are two calls to repositories this package already depends on.
 */
@Service
public class DashboardService {

    /**
     * How many analyses the "recent" list shows.
     *
     * <p>Five, because it is a glance rather than a history. The history page exists and is one click
     * away, so a longer list here would be a worse version of it.
     */
    private static final int RECENT_LIMIT = 5;

    /**
     * How many recurring gaps the dashboard names.
     *
     * <p>Six. A gap list is only useful if it reads as a shortlist — "learn these next" — and twenty
     * entries is a backlog nobody starts.
     */
    private static final int GAP_LIMIT = 6;

    /**
     * How many points the trend chart plots.
     *
     * <p>Thirty, taken from the end of the series rather than from a paged query, and the direction is
     * the reason. The chart wants the <em>newest</em> thirty in ascending order; the query is ascending,
     * so a {@code Pageable} on it would return the oldest thirty — the wrong end. Reversing the query
     * and re-sorting in Java would fix the paging and cost the reader the obvious version. The rows are
     * four columns wide and only completed analyses qualify, so reading them all and slicing the tail is
     * cheaper than it looks and correct without a second sort.
     */
    private static final int TREND_LIMIT = 30;

    private final AnalysisRepository analyses;
    private final AnalysisSkillRepository analysisSkills;
    private final ResumeRepository resumes;
    private final JobDescriptionRepository postings;
    private final UserRepository users;

    public DashboardService(AnalysisRepository analyses,
                            AnalysisSkillRepository analysisSkills,
                            ResumeRepository resumes,
                            JobDescriptionRepository postings,
                            UserRepository users) {
        this.analyses = analyses;
        this.analysisSkills = analysisSkills;
        this.resumes = resumes;
        this.postings = postings;
        this.users = users;
    }

    /**
     * The whole dashboard for one account.
     *
     * <p>A new account gets a valid response rather than a 404: zero counts, null scores and empty
     * lists. That is deliberate — the empty state is a screen the client has to render anyway, and an
     * error would make it render an error instead of an invitation.
     */
    @Transactional(readOnly = true)
    public DashboardResponse of(AuthenticatedUser caller) {
        Long userId = caller.id();

        DashboardResponse.Counts counts = new DashboardResponse.Counts(
                analyses.countByUserId(userId),
                resumes.countByUserId(userId),
                postings.countByUserId(userId));

        List<DashboardResponse.TrendPoint> history = trend(userId);

        List<AnalysisSummaryResponse> recent =
                analyses.findSummariesForUser(userId, PageRequest.of(0, RECENT_LIMIT)).stream()
                        .map(AnalysisSummaryResponse::from)
                        .toList();

        List<DashboardResponse.SkillGap> gaps = analysisSkills
                .countGapsForUser(userId, SkillStatus.MISSING, PageRequest.of(0, GAP_LIMIT)).stream()
                .map(DashboardResponse.SkillGap::from)
                .toList();

        return new DashboardResponse(
                counts,
                DashboardResponse.Scores.from(analyses.findTotalsForUser(userId), history),
                history,
                recent,
                gaps,
                targetRoleOf(userId));
    }

    /** The last {@link #TREND_LIMIT} completed scores, oldest first, ready to plot. */
    private List<DashboardResponse.TrendPoint> trend(Long userId) {
        List<ScorePoint> points = analyses.findScoreHistoryForUser(userId);
        List<ScorePoint> tail = points.size() <= TREND_LIMIT
                ? points
                : points.subList(points.size() - TREND_LIMIT, points.size());
        return tail.stream().map(DashboardResponse.TrendPoint::from).toList();
    }

    /**
     * The role the user is aiming for, or null.
     *
     * <p>On the dashboard so the empty state can say something specific, and so a returning user sees
     * their own target rather than a generic heading. Read from the row instead of from the token: a
     * profile edit has to show up on the next page load, and a claim baked into a JWT would keep saying
     * the old thing until the token rotated.
     */
    private String targetRoleOf(Long userId) {
        return users.findById(userId).map(User::getTargetRole).orElse(null);
    }
}
