package com.openwearableinsights.api.journal.domain;

import java.util.List;

/**
 * An exploratory correlation between a logged journal behavior and a
 * Garmin-exclusive signal (Body Battery low, Garmin's own Training
 * Readiness score) — the same group-mean-comparison methodology as
 * {@link BehaviorCorrelation}, kept as a separate type rather than
 * generalizing that one, since {@code signalName} varies here (readiness
 * score is the only outcome {@link BehaviorCorrelation} ever compares
 * against) and the Flutter contract for the existing correlations feature
 * shouldn't need to change to add this.
 *
 * <p>Same discipline as {@link BehaviorCorrelation}: minimum sample size
 * per group, confidence capped at "medium", always an explicit
 * correlation-not-causation caution.
 *
 * @param signalName              which Garmin signal this compares against
 *                                (e.g. "Body Battery low", "Garmin Training Readiness")
 * @param loggedDayCount          distinct days this behavior was logged
 *                                (that also have a value for this signal)
 * @param notLoggedDayCount       distinct days with a signal value but no
 *                                entry for this behavior
 * @param avgSignalWhenLogged     mean signal value on logged days
 * @param avgSignalWhenNotLogged  mean signal value on non-logged days
 * @param difference              avgSignalWhenLogged - avgSignalWhenNotLogged
 */
public record GarminSignalCorrelation(
        String signalName,
        String category,
        String behavior,
        int loggedDayCount,
        int notLoggedDayCount,
        double avgSignalWhenLogged,
        double avgSignalWhenNotLogged,
        double difference,
        String confidence,
        List<String> limitations
) {}
