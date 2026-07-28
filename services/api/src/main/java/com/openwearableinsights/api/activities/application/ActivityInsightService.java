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
 * Computes real activity summaries from step measurements.
 *
 * <p>Calories, active minutes, and active zone minutes are NOT computed —
 * the data model has no measurement type for them yet. Rather than
 * fabricating estimates from steps (which would look like a real signal
 * but wouldn't be one), those fields are reported as unavailable, per the
 * same honesty discipline as the readiness score's missing-data handling.
 *
 * <p>Like SleepInsightService, "summary" means the most recent day with
 * real data rather than strictly today — more useful once data import lags
 * a day behind, and consistent with how the sleep view already works.
 */
@Service
public class ActivityInsightService {

    private static final Logger log = LoggerFactory.getLogger(ActivityInsightService.class);

    private static final List<String> UNTRACKED_METRICS_LIMITATION = List.of(
            "Calories, active minutes, and active zone minutes are not yet " +
            "tracked in the data model — only step counts are computed from " +
            "real measurement data."
    );

    private final JdbcTemplate jdbcTemplate;

    public ActivityInsightService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ActivitySummary computeLatestSummary(Long accountId) {
        Optional<LocalDate> latestDate = findMostRecentStepsDate(accountId);
        if (latestDate.isEmpty()) {
            List<String> limitations = new ArrayList<>();
            limitations.add("No step data has been imported yet.");
            limitations.addAll(UNTRACKED_METRICS_LIMITATION);
            return new ActivitySummary(0, null, null, null, Instant.now().toString(), "none", limitations);
        }
        int steps = sumStepsForDate(accountId, latestDate.get());
        return new ActivitySummary(
                steps, null, null, null,
                latestDate.get().atStartOfDay(ZoneOffset.UTC).toInstant().toString(),
                "medium", UNTRACKED_METRICS_LIMITATION
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
}
