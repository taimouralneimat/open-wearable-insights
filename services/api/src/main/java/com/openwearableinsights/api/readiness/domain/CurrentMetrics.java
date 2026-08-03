package com.openwearableinsights.api.readiness.domain;

import java.util.Optional;

/**
 * Raw current-day metric values (not deviations) for the readiness calculation.
 *
 * Used with {@link PersonalBaseline} to compute deviations internally,
 * replacing the Phase 1 approach of passing pre-computed deviations.
 *
 * @param hrvMs             current HRV in milliseconds
 * @param rhrBpm            current resting heart rate in bpm
 * @param sleepDurationHours last night sleep duration in hours
 * @param sleepNeedHours    estimated sleep need in hours
 * @param acuteLoad         acute training load (7-day)
 * @param chronicLoad       chronic training load (28-day)
 * @param stressScore       current stress score (0-100)
 * @param bodyBatteryLow    most recent day's Body Battery low value (0-100,
 *                          Garmin-exclusive — see garminconnect module).
 *                          A higher "low" means less depletion that day.
 * @param dataCompleteness  fraction of expected metrics present (0.0-1.0)
 */
public record CurrentMetrics(
        Optional<Double> hrvMs,
        Optional<Double> rhrBpm,
        Optional<Double> sleepDurationHours,
        Optional<Double> sleepNeedHours,
        Optional<Double> acuteLoad,
        Optional<Double> chronicLoad,
        Optional<Double> stressScore,
        Optional<Double> bodyBatteryLow,
        double dataCompleteness
) {
    public CurrentMetrics {
        if (dataCompleteness < 0.0 || dataCompleteness > 1.0) {
            throw new IllegalArgumentException("dataCompleteness must be 0.0-1.0");
        }
    }
}
