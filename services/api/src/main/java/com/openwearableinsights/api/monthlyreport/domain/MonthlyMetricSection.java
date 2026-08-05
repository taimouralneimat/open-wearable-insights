package com.openwearableinsights.api.monthlyreport.domain;

import java.util.List;

/**
 * One dimension's real breakdown within a {@link MonthlyPerformanceReport} --
 * strain (training load), sleep, or recovery (readiness score). All three
 * dimensions share this exact shape so the report reads as one consistent
 * structure rather than three bespoke ones.
 *
 * @param name           plain-language dimension name (e.g. "Strain
 *                       (training load)")
 * @param averageValue   the real average value across the days in the month
 *                       that have data, or {@code null} if this dimension
 *                       had zero real data points this month -- never a
 *                       fabricated average
 * @param unit           unit of {@code averageValue}
 * @param daysWithData   how many real days in the month contributed to this
 *                       average (never zero-filled -- see {@code
 *                       MonthlyPerformanceReportService})
 * @param daysInMonth    total calendar days in the reported month
 * @param trendDirection {@code IMPROVING}/{@code DECLINING}/{@code STEADY}
 *                       from a first-half-vs-second-half-of-the-month
 *                       comparison of real data (same honest-trend-direction
 *                       convention as {@code sleep_page.dart}'s
 *                       {@code _TrendSummaryRow} on the Flutter side), or
 *                       {@code UNKNOWN} when there isn't enough real data
 *                       this month to read one -- never a claim of
 *                       statistical significance this app doesn't have
 * @param trendDetail    plain-language statement of the real first-half vs
 *                       second-half averages behind {@code trendDirection}
 * @param confidence     "none", "low", or "medium" -- this section's own
 *                       honest confidence, based on how much of the month's
 *                       days actually have real data; never "high"
 * @param limitations    honest, user-facing caveats specific to this
 *                       dimension
 */
public record MonthlyMetricSection(
        String name,
        Double averageValue,
        String unit,
        int daysWithData,
        int daysInMonth,
        TrendDirection trendDirection,
        String trendDetail,
        String confidence,
        List<String> limitations
) {
    /**
     * For recovery and sleep, higher is favorable, so IMPROVING/DECLINING
     * read naturally as "getting better/worse". For strain (training load),
     * there is no inherent "better" direction -- more or less training load
     * is not itself good or bad, so IMPROVING/DECLINING there describes the
     * trend line's direction only, not a value judgment. See the strain
     * section's own {@code limitations} entry, which states this explicitly
     * rather than leaving it implied.
     */
    public enum TrendDirection {
        IMPROVING, DECLINING, STEADY, UNKNOWN
    }
}
