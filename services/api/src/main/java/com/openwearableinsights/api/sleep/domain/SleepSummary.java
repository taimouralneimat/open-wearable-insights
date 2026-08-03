package com.openwearableinsights.api.sleep.domain;

import java.util.List;

/**
 * A single night's sleep summary, computed from real sleep-stage
 * measurements when available.
 *
 * @param totalHours   time in bed — deep + rem + light + awake. Historically
 *                     the only duration figure this app exposed, and still
 *                     what sleep debt/plan and the readiness score's
 *                     "sleep_duration" baseline use — see {@code asleepHours}
 *                     for the distinct true-sleep figure.
 * @param asleepHours  deep + rem + light only, excluding awake-in-bed time —
 *                     the actual time spent asleep, not just in bed. Display
 *                     only for now; debt/plan/readiness intentionally still
 *                     use totalHours (time in bed) to avoid changing
 *                     already-shipped, baseline-sensitive calculations.
 * @param sleepNeedHours the account's personal rolling-baseline sleep need
 *                       (same figure the readiness score uses — see
 *                       readiness.application.BaselineService's
 *                       "sleep_duration" baseline), null until enough
 *                       history exists to compute one
 * @param confidence   honest assessment of how reliable this summary is,
 *                     based on reading density — never inflated
 * @param limitations  known caveats about this specific computation
 */
public record SleepSummary(
        double totalHours,
        double asleepHours,
        double deepHours,
        double remHours,
        double lightHours,
        double awakeHours,
        int sleepScore,
        Double sleepNeedHours,
        List<SleepStagePoint> stages,
        String confidence,
        List<String> limitations
) {
    public record SleepStagePoint(String time, String stage) {}
}
