package com.resumeiq.analysis;

import java.time.Instant;

/**
 * One point on the score-history chart in Phase 9.
 *
 * <p>Two columns, ordered oldest first, which is all a line chart needs. Reading full analyses
 * to draw a sparkline would pull every text column in the table across the wire.
 */
public interface ScorePoint {

    Instant getRecordedAt();

    Integer getOverallScore();

    Integer getAtsScore();

    Integer getJobMatchScore();
}
