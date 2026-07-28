package com.openwearableinsights.api.readiness.application;

import com.openwearableinsights.api.readiness.domain.CurrentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches current-day metric values from the measurements table.
 *
 * Used by ReadinessController to get raw values (not deviations) for
 * the personalized baseline calculation.
 */
@Service
public class CurrentMetricsService {

    private static final Logger log = LoggerFactory.getLogger(CurrentMetricsService.class);
    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    // Sleep duration is approximated from sleep_stage reading density until
    // real session parsing lands (see fetchSleepDuration). Values outside a
    // physiologically plausible range indicate the approximation broke down
    // for this data (e.g. sparse synthetic fixtures), not a real signal —
    // better to report missing than to feed a nonsense number into the score.
    private static final double MIN_PLAUSIBLE_SLEEP_HOURS = 2.0;
    private static final double MAX_PLAUSIBLE_SLEEP_HOURS = 14.0;

    private final JdbcTemplate jdbcTemplate;

    public CurrentMetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Fetch current-day metrics for the default account.
     * Returns the most recent values for each metric type.
     */
    public CurrentMetrics fetchCurrent() {
        return fetchCurrent(DEFAULT_ACCOUNT_ID);
    }

    public CurrentMetrics fetchCurrent(Long accountId) {
        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);

        Optional<Double> hrv = fetchLatestMetric(accountId, "hrv", todayStart);
        Optional<Double> rhr = fetchLatestMetric(accountId, "rhr", todayStart);
        Optional<Double> stress = fetchLatestMetric(accountId, "stress", todayStart);
        Optional<Double> sleepDuration = fetchSleepDuration(accountId, todayStart);

        // Training load: sum of steps for acute (7d) and chronic (28d) windows
        double acuteLoad = fetchStepsSum(accountId, Instant.now().minus(7, ChronoUnit.DAYS));
        double chronicLoad = fetchStepsSum(accountId, Instant.now().minus(28, ChronoUnit.DAYS));

        // Sleep need: default 7.5h if not specified
        Optional<Double> sleepNeed = Optional.of(7.5);

        // Data completeness: fraction of expected metrics present
        int expectedMetrics = 5; // hrv, rhr, stress, sleep, steps
        int presentMetrics = 0;
        if (hrv.isPresent()) presentMetrics++;
        if (rhr.isPresent()) presentMetrics++;
        if (stress.isPresent()) presentMetrics++;
        if (sleepDuration.isPresent()) presentMetrics++;
        if (acuteLoad > 0) presentMetrics++;
        double completeness = presentMetrics / (double) expectedMetrics;

        return new CurrentMetrics(
                hrv, rhr, sleepDuration, sleepNeed,
                acuteLoad > 0 ? Optional.of(acuteLoad) : Optional.empty(),
                chronicLoad > 0 ? Optional.of(chronicLoad) : Optional.empty(),
                stress,
                completeness
        );
    }

    private Optional<Double> fetchLatestMetric(Long accountId, String metricType, Instant since) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT value FROM measurements " +
                    "WHERE account_id = ? AND metric_type = ? AND time >= ? " +
                    "ORDER BY time DESC LIMIT 1",
                    accountId, metricType, Timestamp.from(since)
            );
            if (!rows.isEmpty() && rows.get(0).get("value") != null) {
                return Optional.of(((Number) rows.get(0).get("value")).doubleValue());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch latest '{}' for account {}: {}", metricType, accountId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private Optional<Double> fetchSleepDuration(Long accountId, Instant since) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM measurements " +
                    "WHERE account_id = ? AND metric_type = ? AND time >= ?",
                    Integer.class, accountId, "sleep_stage", Timestamp.from(since)
            );
            if (count != null && count > 0) {
                // Approximate: count * 15 min / 4 = hours. This is a placeholder
                // until real sleep-session parsing lands (normalization pipeline).
                double hours = count * 0.25;
                if (hours < MIN_PLAUSIBLE_SLEEP_HOURS || hours > MAX_PLAUSIBLE_SLEEP_HOURS) {
                    log.warn("Approximated sleep duration {} h for account {} is outside the " +
                            "plausible range [{}, {}] — reading density doesn't match the " +
                            "15-min-interval assumption. Reporting as missing rather than " +
                            "feeding a bad number into the score.",
                            hours, accountId, MIN_PLAUSIBLE_SLEEP_HOURS, MAX_PLAUSIBLE_SLEEP_HOURS);
                    return Optional.empty();
                }
                return Optional.of(hours);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch sleep duration for account {}: {}", accountId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    private double fetchStepsSum(Long accountId, Instant since) {
        try {
            Double avg = jdbcTemplate.queryForObject(
                    "SELECT AVG(daily_steps) FROM (" +
                    "  SELECT DATE(time) as d, SUM(value) as daily_steps " +
                    "  FROM measurements " +
                    "  WHERE account_id = ? AND metric_type = ? AND time >= ? " +
                    "  GROUP BY DATE(time)" +
                    ") sub",
                    Double.class, accountId, "steps", Timestamp.from(since)
            );
            return avg != null ? avg : 0.0;
        } catch (Exception e) {
            log.warn("Failed to fetch steps sum for account {}: {}", accountId, e.getMessage(), e);
            return 0.0;
        }
    }
}
