package com.resumeiq.analysis;

/**
 * The headline numbers on the dashboard, computed in one aggregate query.
 *
 * <p>The alternative — loading a user's analyses and averaging in Java — moves every row of the
 * table into the JVM to produce three numbers, and gets slower for exactly the users who use the
 * product most.
 *
 * <p>{@code averageScore} is a {@code Double} because {@code avg} over no rows is null, not
 * zero. A new account has no completed analyses, and rendering "0" would tell that person their
 * resume scored zero.
 */
public interface DashboardTotals {

    long getAnalysisCount();

    Double getAverageScore();

    Integer getBestScore();
}
