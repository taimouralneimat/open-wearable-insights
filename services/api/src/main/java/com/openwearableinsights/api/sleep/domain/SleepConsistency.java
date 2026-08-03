package com.openwearableinsights.api.sleep.domain;

import java.util.List;

/**
 * How consistent bed/wake times have been over a recent window — a
 * genuinely missing metric (docs/product/parity-matrix.md row 3), distinct
 * from sleep duration/debt (this is about *when*, not *how much*).
 *
 * <p>{@code consistencyScore} (0-100, higher = more consistent) is a
 * simple, documented, not-empirically-tuned mapping from the average of
 * bedtime and wake-time standard deviation (in minutes) to a 0-100 scale:
 * 100 at 0 minutes deviation, 0 at 100+ minutes deviation, linear between.
 *
 * @param nightsConsidered how many real nights fed this computation
 * @param confidence       "none" below the minimum night count, otherwise "low"/"medium"
 * @param limitations      always notes this isn't a medical measure and the
 *                         averaging approach's accuracy limits
 */
public record SleepConsistency(
        Integer consistencyScore,
        String avgBedtime,
        String avgWakeTime,
        Double bedtimeVarianceMinutes,
        Double wakeTimeVarianceMinutes,
        int nightsConsidered,
        String confidence,
        List<String> limitations
) {}
