package com.openwearableinsights.api.readiness.domain;

import java.util.Optional;

/**
 * Inputs to the deterministic readiness algorithm v0.1.
 *
 * <p>All values are deviations from a personal rolling baseline.
 * Missing values are represented by empty Optionals (never null).
 *
 * @param hrvDeviationMs        HRV deviation from baseline (ms); positive = above baseline
 * @param rhrDeviationBpm       Resting HR deviation from baseline (bpm); negative = below baseline (good)
 * @param sleepDurationHours   Last night's sleep duration (hours)
 * @param sleepNeedHours       Estimated sleep need (hours)
 * @param acuteLoad            Acute training load (7-day)
 * @param chronicLoad          Chronic training load (28-day)
 * @param stressScore          Average stress over recent window (0-100)
 * @param bodyBatteryLowDeviation Deviation of today's Body Battery low from
 *                             its personal baseline; positive = less
 *                             depleted than usual (good). Garmin-exclusive.
 * @param dataCompleteness     Fraction of expected metrics present (0.0-1.0)
 * @param baselineDays         Number of days in the baseline period
 */
public record ReadinessInputs(
        Optional<Double> hrvDeviationMs,
        Optional<Double> rhrDeviationBpm,
        Optional<Double> sleepDurationHours,
        Optional<Double> sleepNeedHours,
        Optional<Double> acuteLoad,
        Optional<Double> chronicLoad,
        Optional<Double> stressScore,
        Optional<Double> bodyBatteryLowDeviation,
        double dataCompleteness,
        int baselineDays
) {
    public ReadinessInputs {
        if (dataCompleteness < 0.0 || dataCompleteness > 1.0) {
            throw new IllegalArgumentException("dataCompleteness must be 0.0-1.0");
        }
    }

    /**
     * Convenience constructor for callers that don't have Body Battery data
     * (e.g. no Garmin Connect sync) — matches every call site that existed
     * before that metric was added, so they don't all need updating just to
     * pass an explicit {@code Optional.empty()}.
     */
    public ReadinessInputs(
            Optional<Double> hrvDeviationMs,
            Optional<Double> rhrDeviationBpm,
            Optional<Double> sleepDurationHours,
            Optional<Double> sleepNeedHours,
            Optional<Double> acuteLoad,
            Optional<Double> chronicLoad,
            Optional<Double> stressScore,
            double dataCompleteness,
            int baselineDays
    ) {
        this(hrvDeviationMs, rhrDeviationBpm, sleepDurationHours, sleepNeedHours,
                acuteLoad, chronicLoad, stressScore, Optional.empty(), dataCompleteness, baselineDays);
    }
}