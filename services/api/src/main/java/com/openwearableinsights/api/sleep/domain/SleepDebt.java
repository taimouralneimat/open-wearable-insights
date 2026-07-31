package com.openwearableinsights.api.sleep.domain;

import java.util.List;

/**
 * Accumulated sleep debt/surplus over a recent window — the "sleep
 * debt / sleep-need estimate" scope item from docs/analytics/sleep-methodology.md.
 *
 * <p>{@code neededHoursPerNight} is the personal rolling-baseline estimate
 * (see readiness.application.BaselineService's "sleep_duration" baseline —
 * reused here rather than computed a second, divergent way, so this figure
 * always agrees with what the readiness score itself uses). Null when no
 * baseline exists yet (too little history) — never a fabricated default.
 *
 * @param accumulatedHours positive = debt (slept less than needed on net),
 *                         negative = surplus. Sum of (need - actual) across
 *                         nights with real data in the window; nights with
 *                         no data are skipped, not assumed to be zero.
 */
public record SleepDebt(
        Double neededHoursPerNight,
        Double accumulatedHours,
        int nightsConsidered,
        int windowDays,
        String confidence,
        List<String> limitations
) {}
