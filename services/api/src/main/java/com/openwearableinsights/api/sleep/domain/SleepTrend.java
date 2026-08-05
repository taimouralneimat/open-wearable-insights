package com.openwearableinsights.api.sleep.domain;

import java.util.List;

/**
 * The full sleep-trend response for one window (parity-matrix.md row 8):
 * the real per-bucket points (see {@link SleepTrendPoint}) plus the
 * account's personal sleep-duration baseline, so each night/bucket can be
 * visually compared against a real reference value instead of just an
 * avg/direction caption.
 *
 * <p><b>Current, not historical, baseline:</b> {@code baselineHours} is
 * {@code BaselineService}'s current rolling "sleep_duration" baseline —
 * the same figure {@link com.openwearableinsights.api.sleep.application.SleepInsightService}
 * already reuses for personal sleep need/debt/plan — rendered as a single
 * flat reference line across the entire requested window. It is NOT
 * recomputed per-point as it would have looked at each past date —
 * {@code BaselineService} doesn't expose historical baselines, only the
 * current one. For a long window (e.g. 90 nights), the earliest points are
 * therefore compared against a baseline reflecting more recent sleep, not
 * their own contemporaneous baseline. This simplification is disclosed in
 * {@code limitations} rather than silently treated as point-in-time-accurate
 * — same "state it honestly" discipline as {@code Vo2MaxService}'s
 * window-choice disclosure and {@code MonthlyPerformanceReportService}'s
 * date-range-filtering caveat.
 *
 * @param points                     real trend points, oldest first
 * @param baselineHours              the account's current personal sleep-
 *                                    duration baseline in hours, or {@code
 *                                    null} if the account doesn't have one
 *                                    yet — never a fabricated reference line
 * @param baselineWindowDescription  the window the baseline itself was
 *                                    computed over (e.g. "28-day rolling"),
 *                                    distinct from this trend's own {@code
 *                                    days} request parameter; {@code null}
 *                                    alongside a null {@code baselineHours}
 * @param baselineConfidence         "low"/"medium"/"high" straight from
 *                                    {@code PersonalBaseline#confidence()} —
 *                                    a thin baseline is never shown as if it
 *                                    were solid
 * @param baselineSampleSize         how many real nights of sleep data fed
 *                                    the baseline
 * @param limitations                plain-language caveats about the
 *                                    baseline overlay, notably the
 *                                    current-vs-historical simplification
 *                                    above
 */
public record SleepTrend(
        List<SleepTrendPoint> points,
        Double baselineHours,
        String baselineWindowDescription,
        String baselineConfidence,
        Integer baselineSampleSize,
        List<String> limitations
) {}
