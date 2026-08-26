package com.resumeiq.analysis;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Everything the dashboard shows, in one request.
 *
 * <p>One endpoint rather than six, and that is a deliberate trade. The dashboard needs counts, an
 * average, a trend line, the newest few analyses and the gaps that keep recurring; served separately
 * that is six round trips before the page settles, six loading states to design and six ways for the
 * page to be half-rendered. Served together it is one request, one spinner and one cache entry, at the
 * cost of an endpoint that is shaped like a screen instead of like a resource. For a dashboard that is
 * the right way round: the screen is the thing being requested.
 *
 * <p>What keeps that honest is that nothing here is computed in Java. Every number is an aggregate the
 * database produced — {@code count}, {@code avg}, {@code max}, a grouped count of gaps — so adding a
 * widget costs a query rather than a pass over every analysis the user has ever run.
 *
 * @param targetRole the role from the user's profile, so the empty state can say something useful
 */
@Schema(description = "Counts, trend and highlights for the dashboard")
public record DashboardResponse(
        Counts counts,
        Scores scores,
        List<TrendPoint> scoreHistory,
        List<AnalysisSummaryResponse> recentAnalyses,
        List<SkillGap> topSkillGaps,
        String targetRole
) {

    /**
     * How much the account holds.
     *
     * <p>Resumes and postings are counted as well as analyses because the empty state depends on
     * which of them is missing: somebody with a resume and no posting needs a different prompt from
     * somebody with neither, and the client can only tell those apart if it is told.
     */
    @Schema(description = "What this account holds")
    public record Counts(long analyses, long resumes, long jobDescriptions) {
    }

    /**
     * The headline numbers.
     *
     * <p>All three are nullable, and that is not laziness: an account with no completed analysis has
     * no average and no best score, and returning zero would draw a chart showing a score of zero
     * where the truth is "nothing scored yet". Null renders as an empty state; zero renders as failure.
     *
     * @param average rounded to a whole number, because a resume score of 71.4 implies a precision the
     *                engine does not have
     * @param latest  the most recent completed score, which is the one the trend line ends on
     */
    @Schema(description = "Average, best and latest overall score")
    public record Scores(Integer average, Integer best, Integer latest) {

        static Scores from(DashboardTotals totals, List<TrendPoint> history) {
            Double average = totals == null ? null : totals.getAverageScore();
            Integer best = totals == null ? null : totals.getBestScore();
            Integer latest = history.isEmpty() ? null : history.get(history.size() - 1).overall();
            return new Scores(
                    average == null ? null : (int) Math.round(average),
                    best,
                    latest);
        }
    }

    /**
     * One point on the score history chart.
     *
     * <p>Three series rather than six, matching the history table: overall, ATS and job match. Six
     * lines on one chart is a chart nobody reads.
     */
    @Schema(description = "One dated point on the trend chart")
    public record TrendPoint(Instant recordedAt, Integer overall, Integer ats, Integer jobMatch) {

        static TrendPoint from(ScorePoint point) {
            return new TrendPoint(point.getRecordedAt(), point.getOverallScore(),
                    point.getAtsScore(), point.getJobMatchScore());
        }
    }

    /**
     * A skill that keeps coming up missing.
     *
     * <p>The most useful thing on the dashboard, and the one thing here that no single analysis can
     * tell you: one posting asking for Docker is a job requirement, and five postings asking for
     * Docker is a decision about what to learn next.
     *
     * @param occurrences how many of this user's analyses flagged it
     */
    @Schema(description = "A recurring gap across this account's analyses")
    public record SkillGap(String skill, long occurrences) {

        static SkillGap from(SkillGapCount count) {
            return new SkillGap(count.getLabel(), count.getOccurrences());
        }
    }
}
