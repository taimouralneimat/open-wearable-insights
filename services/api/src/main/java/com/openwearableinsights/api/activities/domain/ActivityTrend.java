package com.openwearableinsights.api.activities.domain;

import java.util.List;

/**
 * The full step-trend response for one window (parity-matrix.md row 8):
 * the real per-bucket points (see {@link ActivityTrendPoint}) plus the
 * account's personal steps baseline, so each point can be visually compared
 * against a real reference value instead of just an avg/direction caption.
 *
 * <p><b>Current, not historical, baseline:</b> {@code baselineSteps} is
 * {@code BaselineService}'s current rolling baseline (computed from the
 * account's most recent measurement history), rendered as a single flat
 * reference line across the entire requested window. It is NOT
 * recomputed per-point as it would have looked at each past date —
 * {@code BaselineService} doesn't expose historical baselines, only the
 * current one. For a long window (e.g. 90 days), the earliest points are
 * therefore being compared against a baseline that reflects more recent
 * behavior, not their own contemporaneous baseline. This simplification is
 * disclosed in {@code limitations} rather than silently treated as
 * point-in-time-accurate — same "state it honestly" discipline as {@code
 * Vo2MaxService}'s window-choice disclosure and {@code
 * MonthlyPerformanceReportService}'s date-range-filtering caveat.
 *
 * @param points                     real trend points, oldest first
 * @param baselineSteps              the account's current personal daily-steps
 *                                    baseline, or {@code null} if the account
 *                                    doesn't have one yet — never a fabricated
 *                                    reference line
 * @param baselineWindowDescription  the window the baseline itself was
 *                                    computed over (e.g. "28-day rolling"),
 *                                    distinct from this trend's own {@code
 *                                    days} request parameter; {@code null}
 *                                    alongside a null {@code baselineSteps}
 * @param baselineConfidence         "low"/"medium"/"high" straight from
 *                                    {@code PersonalBaseline#confidence()} —
 *                                    a thin baseline is never shown as if it
 *                                    were solid
 * @param baselineSampleSize         how many real days of steps data fed the
 *                                    baseline
 * @param limitations                plain-language caveats about the
 *                                    baseline overlay, notably the
 *                                    current-vs-historical simplification
 *                                    above
 */
public record ActivityTrend(
        List<ActivityTrendPoint> points,
        Double baselineSteps,
        String baselineWindowDescription,
        String baselineConfidence,
        Integer baselineSampleSize,
        List<String> limitations
) {}
