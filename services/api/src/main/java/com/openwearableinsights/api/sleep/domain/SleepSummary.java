package com.openwearableinsights.api.sleep.domain;

import java.util.List;

/**
 * A single night's sleep summary, computed from real sleep-stage
 * measurements when available.
 *
 * @param confidence   honest assessment of how reliable this summary is,
 *                     based on reading density — never inflated
 * @param limitations  known caveats about this specific computation
 */
public record SleepSummary(
        double totalHours,
        double deepHours,
        double remHours,
        double lightHours,
        double awakeHours,
        int sleepScore,
        List<SleepStagePoint> stages,
        String confidence,
        List<String> limitations
) {
    public record SleepStagePoint(String time, String stage) {}
}
