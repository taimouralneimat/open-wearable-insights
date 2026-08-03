package com.openwearableinsights.api.activities.application;

import com.openwearableinsights.api.activities.domain.ActivitySummary;
import com.openwearableinsights.api.activities.domain.ActivityTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    private final JdbcTemplate jdbcTemplate;

    public ActivityInsightService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    /**
     * Real step trends for up to {@code days} most recent dates with data.
     * Returns fewer points if less history exists.
     */
    public List<ActivityTrendPoint> computeStepTrends(Long accountId, int days) {
        try {
            List<LocalDate> dates = jdbcTemplate.queryForList(
                    "SELECT DISTINCT DATE(time) FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'steps' " +
                    "ORDER BY DATE(time) DESC LIMIT ?",
                    LocalDate.class, accountId, days
            );
            List<ActivityTrendPoint> trends = new ArrayList<>();
            for (LocalDate date : dates.reversed()) {
                trends.add(new ActivityTrendPoint(date.toString(), sumStepsForDate(accountId, date)));
            }
            return trends;
        } catch (Exception e) {
            log.warn("Failed to compute step trends for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
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
