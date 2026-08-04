package com.openwearableinsights.api.strength.domain;

/**
 * One bucket of the "Strength Activity Time" trend (docs/product/
 * parity-matrix.md row 25) — total strength-training minutes in a period,
 * broken out by source so a precise Garmin-measured duration is never
 * silently blended with an estimated manually-logged one without
 * disclosure (this app's data-honesty convention — see
 * {@code DataQualityService}, {@code TrainingLoadSummary}).
 *
 * <p>Only periods with at least one contributing session/workout are
 * included — no zero-fill, matching {@code TrainingLoadTrendPoint}'s
 * active-periods-only convention.
 *
 * @param periodStart        ISO-8601 date the bucket starts on
 * @param periodEnd          ISO-8601 date the bucket ends on (inclusive)
 * @param garminMinutes      minutes from real Garmin-measured session
 *                           duration (sport = 'training' in the
 *                           {@code activities} table) — see
 *                           {@code StrengthTrainingService} for why this
 *                           match is a heuristic, not exact
 * @param manualMinutes      minutes from manually-logged
 *                           {@code strength_workouts} in this bucket
 *                           (user-reported duration where given, estimated
 *                           from set count otherwise — see
 *                           {@code manualMinutesEstimated})
 * @param manualMinutesEstimated {@code true} if any manually-logged workout
 *                           contributing to {@code manualMinutes} in this
 *                           bucket used the set-count estimate rather than
 *                           a user-reported duration
 * @param totalMinutes       {@code garminMinutes + manualMinutes}
 */
public record StrengthActivityTrendPoint(
        String periodStart,
        String periodEnd,
        double garminMinutes,
        double manualMinutes,
        boolean manualMinutesEstimated,
        double totalMinutes
) {}
