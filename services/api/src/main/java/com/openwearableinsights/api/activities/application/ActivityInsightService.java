package com.openwearableinsights.api.activities.application;

import com.openwearableinsights.api.activities.domain.ActivitySummary;
import com.openwearableinsights.api.activities.domain.ActivityTrend;
import com.openwearableinsights.api.activities.domain.ActivityTrendPoint;
import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes real activity summaries from measurement data.
 *
 * <p>Steps are always tracked (FIT and Garmin Connect both provide them).
 * Calories and active minutes are only real when a Garmin Connect sync has
 * run (see garminconnect module) — FIT import alone doesn't produce them.
 * Active zone minutes (heart-rate-zone-weighted, distinct from raw active
 * minutes) genuinely isn't tracked by either source yet; reported as
 * unavailable rather than fabricated, per the same honesty discipline as
 * the readiness score's missing-data handling.
 *
 * <p>Like SleepInsightService, "summary" means the most recent day with
 * real data rather than strictly today — more useful once data import lags
 * a day behind, and consistent with how the sleep view already works.
 */
@Service
public class ActivityInsightService {

    private static final Logger log = LoggerFactory.getLogger(ActivityInsightService.class);

    private static final String ACTIVE_ZONE_MINUTES_LIMITATION =
            "Active zone minutes (heart-rate-zone-weighted) isn't tracked yet — " +
            "active minutes above is moderate + vigorous intensity minutes, not zone-weighted.";

    // parity-matrix.md row 8: the baseline line drawn across the trend chart
    // is BaselineService's CURRENT rolling baseline, not a per-point
    // historical one — BaselineService only exposes "now," so this is
    // disclosed rather than letting a flat reference line silently imply
    // point-in-time accuracy across the whole window. Same "state the
    // simplification honestly" discipline as Vo2MaxService's window-choice
    // disclosure and MonthlyPerformanceReportService's date-range caveat.
    private static final String BASELINE_CURRENT_NOT_HISTORICAL_LIMITATION =
            "The baseline line is your CURRENT rolling average steps/day, shown as one " +
            "fixed reference across the whole chart — it is not recomputed for each past " +
            "point, so it does not reflect what your baseline looked like earlier in this window.";

    private final JdbcTemplate jdbcTemplate;
    private final BaselineService baselineService;

    public ActivityInsightService(JdbcTemplate jdbcTemplate, BaselineService baselineService) {
        this.jdbcTemplate = jdbcTemplate;
        this.baselineService = baselineService;
    }

    public ActivitySummary computeLatestSummary(Long accountId) {
        Optional<LocalDate> latestDate = findMostRecentStepsDate(accountId);
        if (latestDate.isEmpty()) {
            return new ActivitySummary(0, null, null, null, Instant.now().toString(), "none",
                    List.of("No step data has been imported yet."));
        }
        LocalDate date = latestDate.get();
        int steps = sumStepsForDate(accountId, date);
        Integer calories = sumMetricForDate(accountId, "calories", date);
        Integer activeMinutes = sumActiveMinutesForDate(accountId, date);

        List<String> limitations = new ArrayList<>();
        limitations.add(ACTIVE_ZONE_MINUTES_LIMITATION);
        if (calories == null || activeMinutes == null) {
            limitations.add("Calories and active minutes require a Garmin Connect sync — " +
                    "not produced by a plain FIT file import.");
        }

        return new ActivitySummary(
                steps, calories, activeMinutes, null,
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toString(),
                "medium", limitations
        );
    }

    // Rollup thresholds (parity-matrix.md row 9): beyond a few weeks, one
    // point per real day stops being readable (366 raw points for a
    // year+ view) — bucket into weekly, then monthly points instead. Chosen
    // to line up with the Flutter trend card's existing 7/30/90-day window
    // selector: 7 and 30 both stay daily, 90 buckets to weekly.
    private static final int WEEKLY_ROLLUP_THRESHOLD_DAYS = 31;
    private static final int MONTHLY_ROLLUP_THRESHOLD_DAYS = 120;

    /**
     * Real step trends for up to {@code days} most recent dates with data.
     * Returns fewer points if less history exists.
     *
     * <p>For {@code days > 31} this returns weekly points instead of daily
     * ones, and for {@code days > 120} monthly points — each the average
     * steps/day across the real days with data in that bucket (never
     * zero-filled for days with no data), with {@link ActivityTrendPoint#granularity()}
     * disclosing which. A raw list of up to 366 individual daily points
     * doesn't render usefully as a year+ trend; averaging within the bucket
     * (rather than summing) keeps the number comparable across granularities.
     */
    public List<ActivityTrendPoint> computeStepTrends(Long accountId, int days) {
        try {
            // One grouped query (day, sum-for-that-day) instead of the
            // previous DISTINCT-dates-then-N-per-date-queries approach —
            // matters once `days` can be up to 366.
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DATE(time) AS day, SUM(value)::int AS total FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'steps' " +
                    "GROUP BY DATE(time) ORDER BY DATE(time) DESC LIMIT ?",
                    accountId, days
            );
            List<DailyPoint> daily = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                daily.add(new DailyPoint(((java.sql.Date) row.get("day")).toLocalDate(), ((Number) row.get("total")).intValue()));
            }
            daily.sort((a, b) -> a.date.compareTo(b.date));

            if (days <= WEEKLY_ROLLUP_THRESHOLD_DAYS) {
                List<ActivityTrendPoint> trends = new ArrayList<>();
                for (DailyPoint p : daily) {
                    trends.add(new ActivityTrendPoint(p.date.toString(), p.steps, "day"));
                }
                return trends;
            }

            String granularity = days <= MONTHLY_ROLLUP_THRESHOLD_DAYS ? "week" : "month";
            return bucketAverage(daily, granularity);
        } catch (Exception e) {
            log.warn("Failed to compute step trends for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Same trend points as {@link #computeStepTrends}, plus the account's
     * personal steps baseline (parity-matrix.md row 8) — reuses {@link
     * BaselineService#computeBaseline} directly rather than recomputing a
     * second, potentially divergent baseline, same discipline as {@link
     * com.openwearableinsights.api.sleep.application.SleepInsightService#personalSleepNeedHours}.
     * Honest absence: {@code baselineSteps} is {@code null} when the
     * account has no real "steps" baseline yet (see {@link
     * ActivityTrend} Javadoc for the current-vs-historical caveat this
     * always discloses).
     */
    public ActivityTrend computeStepTrendsWithBaseline(Long accountId, int days) {
        List<ActivityTrendPoint> points = computeStepTrends(accountId, days);
        PersonalBaseline baseline = baselineService.computeBaseline(accountId);
        Optional<Double> baselineSteps = baseline.baselineFor("steps");

        List<String> limitations = new ArrayList<>();
        if (baselineSteps.isEmpty()) {
            limitations.add("No personal steps baseline yet — need more measurement " +
                    "history before a baseline reference line can be shown.");
            return new ActivityTrend(points, null, null, null, null, limitations);
        }

        limitations.add(BASELINE_CURRENT_NOT_HISTORICAL_LIMITATION);
        if (baseline.isProvisional()) {
            limitations.add("Steps baseline is still provisional (" + baseline.windowDescription() +
                    ") — treat the reference line as a rough guide, not a solid target.");
        }
        return new ActivityTrend(
                points,
                baselineSteps.get(),
                baseline.windowDescription(),
                baseline.confidence(),
                baseline.sampleSizeFor("steps"),
                limitations
        );
    }

    private record DailyPoint(LocalDate date, int steps) {}

    /** Buckets real daily points into weekly/monthly averages — see {@link #computeStepTrends}. */
    private List<ActivityTrendPoint> bucketAverage(List<DailyPoint> daily, String granularity) {
        Map<LocalDate, List<Integer>> byBucket = new LinkedHashMap<>();
        for (DailyPoint p : daily) {
            LocalDate bucketStart = "week".equals(granularity)
                    ? p.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : p.date.withDayOfMonth(1);
            byBucket.computeIfAbsent(bucketStart, k -> new ArrayList<>()).add(p.steps);
        }
        List<ActivityTrendPoint> trends = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Integer>> entry : byBucket.entrySet()) {
            int avg = (int) Math.round(entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0));
            trends.add(new ActivityTrendPoint(entry.getKey().toString(), avg, granularity));
        }
        trends.sort((a, b) -> a.date().compareTo(b.date()));
        return trends;
    }

    private Optional<LocalDate> findMostRecentStepsDate(Long accountId) {
        try {
            List<LocalDate> dates = jdbcTemplate.queryForList(
                    "SELECT DISTINCT DATE(time) FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'steps' " +
                    "ORDER BY DATE(time) DESC LIMIT 1",
                    LocalDate.class, accountId
            );
            return dates.isEmpty() ? Optional.empty() : Optional.of(dates.get(0));
        } catch (Exception e) {
            log.warn("Failed to find most recent steps date for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private int sumStepsForDate(Long accountId, LocalDate date) {
        try {
            Integer sum = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(value), 0)::int FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'steps' AND DATE(time) = ?",
                    Integer.class, accountId, date
            );
            return sum != null ? sum : 0;
        } catch (Exception e) {
            log.warn("Failed to sum steps for account {} on {}: {}", accountId, date, e.getMessage(), e);
            return 0;
        }
    }

    /** Null (not zero) when the metric has no rows that day — distinguishes "0" from "unavailable". */
    private Integer sumMetricForDate(Long accountId, String metricType, LocalDate date) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM measurements WHERE account_id = ? AND metric_type = ? AND DATE(time) = ?",
                    Integer.class, accountId, metricType, date
            );
            if (count == null || count == 0) return null;
            Double sum = jdbcTemplate.queryForObject(
                    "SELECT SUM(value) FROM measurements WHERE account_id = ? AND metric_type = ? AND DATE(time) = ?",
                    Double.class, accountId, metricType, date
            );
            return sum != null ? (int) Math.round(sum) : null;
        } catch (Exception e) {
            log.warn("Failed to sum '{}' for account {} on {}: {}", metricType, accountId, date, e.getMessage(), e);
            return null;
        }
    }

    /** Moderate + vigorous intensity minutes — see ACTIVE_ZONE_MINUTES_LIMITATION for what this isn't. */
    private Integer sumActiveMinutesForDate(Long accountId, LocalDate date) {
        Integer moderate = sumMetricForDate(accountId, "intensity_minutes_moderate", date);
        Integer vigorous = sumMetricForDate(accountId, "intensity_minutes_vigorous", date);
        if (moderate == null && vigorous == null) return null;
        return (moderate != null ? moderate : 0) + (vigorous != null ? vigorous : 0);
    }
}
