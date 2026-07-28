package com.openwearableinsights.api.readiness.application;

import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes personalized rolling baselines from the user own measurement history.
 *
 * Queries the measurements hypertable to compute per-metric averages over a
 * rolling window. The window is 28 days if available, falling back to 7 days,
 * then to whatever data exists (provisional if <7 days).
 *
 * Sample size and confidence are exposed honestly: a 3-day baseline says so,
 * not pretending to be as reliable as a 60-day one.
 */
@Service
public class BaselineService {

    private static final Logger log = LoggerFactory.getLogger(BaselineService.class);
    private static final Long DEFAULT_ACCOUNT_ID = 1L;
    private static final int CHRONIC_WINDOW_DAYS = 28;
    private static final int ACUTE_WINDOW_DAYS = 7;
    private static final int PROVISIONAL_THRESHOLD_DAYS = 7;

    // See CurrentMetricsService — same plausibility guard against the
    // reading-density sleep-duration approximation producing nonsense values.
    private static final double MIN_PLAUSIBLE_SLEEP_HOURS = 2.0;
    private static final double MAX_PLAUSIBLE_SLEEP_HOURS = 14.0;

    private final JdbcTemplate jdbcTemplate;

    public BaselineService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Compute a personalized baseline for the default account.
     * Returns a PersonalBaseline with per-metric averages, sample sizes,
     * and honest confidence assessment.
     */
    public PersonalBaseline computeBaseline() {
        return computeBaseline(DEFAULT_ACCOUNT_ID);
    }

    /**
     * Compute a personalized baseline for a specific account.
     */
    public PersonalBaseline computeBaseline(Long accountId) {
        // Determine how many days of data we have
        int availableDays = countDaysOfData(accountId);

        // Choose the baseline window
        int windowDays;
        String windowDescription;
        if (availableDays >= CHRONIC_WINDOW_DAYS) {
            windowDays = CHRONIC_WINDOW_DAYS;
            windowDescription = windowDays + "-day rolling";
        } else if (availableDays >= ACUTE_WINDOW_DAYS) {
            windowDays = ACUTE_WINDOW_DAYS;
            windowDescription = windowDays + "-day rolling";
        } else if (availableDays > 0) {
            windowDays = availableDays;
            windowDescription = availableDays + "-day provisional";
        } else {
            // No data at all
            return new PersonalBaseline(
                    Map.of(), Map.of(), 0, "low", "no baseline data"
            );
        }

        // Compute per-metric baselines
        Map<String, Double> metricBaselines = new HashMap<>();
        Map<String, Integer> sampleSizes = new HashMap<>();

        Instant windowStart = Instant.now().minus(windowDays, ChronoUnit.DAYS);

        // HRV baseline
        computeMetricBaseline(accountId, "hrv", windowStart, metricBaselines, sampleSizes);
        // RHR baseline
        computeMetricBaseline(accountId, "rhr", windowStart, metricBaselines, sampleSizes);
        // Stress baseline
        computeMetricBaseline(accountId, "stress", windowStart, metricBaselines, sampleSizes);
        // Sleep duration baseline (from sleep_stage measurements)
        computeSleepDurationBaseline(accountId, windowStart, metricBaselines, sampleSizes);
        // Steps baseline (for training load context)
        computeMetricBaseline(accountId, "steps", windowStart, metricBaselines, sampleSizes);

        String confidence = computeConfidence(windowDays, metricBaselines.size());

        return new PersonalBaseline(
                metricBaselines,
                sampleSizes,
                windowDays,
                confidence,
                windowDescription
        );
    }

    private void computeMetricBaseline(Long accountId, String metricType, Instant windowStart,
                                       Map<String, Double> baselines, Map<String, Integer> sampleSizes) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT AVG(value) as avg_value, COUNT(*) as sample_count " +
                    "FROM measurements " +
                    "WHERE account_id = ? AND metric_type = ? AND time >= ?",
                    accountId, metricType, Timestamp.from(windowStart)
            );
            if (!rows.isEmpty() && rows.get(0).get("avg_value") != null) {
                double avg = ((Number) rows.get(0).get("avg_value")).doubleValue();
                int count = ((Number) rows.get(0).get("sample_count")).intValue();
                baselines.put(metricType, avg);
                sampleSizes.put(metricType, count);
            }
        } catch (Exception e) {
            log.warn("Failed to compute '{}' baseline for account {}: {}", metricType, accountId, e.getMessage(), e);
        }
    }

    private void computeSleepDurationBaseline(Long accountId, Instant windowStart,
                                               Map<String, Double> baselines, Map<String, Integer> sampleSizes) {
        try {
            // Count distinct days with sleep data and average the daily sleep duration
            // Sleep duration is approximated by counting sleep_stage measurements per day
            // and multiplying by the typical interval (this is a simplification;
            // full sleep duration computation will come with the normalization pipeline)
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(DISTINCT DATE(time)) as sleep_days, " +
                    "COUNT(*) as total_readings " +
                    "FROM measurements " +
                    "WHERE account_id = ? AND metric_type = ? AND time >= ?",
                    accountId, "sleep_stage", Timestamp.from(windowStart)
            );
            if (!rows.isEmpty() && rows.get(0).get("sleep_days") != null) {
                int sleepDays = ((Number) rows.get(0).get("sleep_days")).intValue();
                int totalReadings = ((Number) rows.get(0).get("total_readings")).intValue();
                if (sleepDays > 0) {
                    // Approximate: average readings per night * 15-minute interval / 4 = hours
                    // This is a placeholder; real sleep duration needs proper session parsing
                    double avgHours = (totalReadings / (double) sleepDays) * 0.25;
                    if (avgHours < MIN_PLAUSIBLE_SLEEP_HOURS || avgHours > MAX_PLAUSIBLE_SLEEP_HOURS) {
                        log.warn("Approximated sleep duration baseline {} h for account {} is " +
                                "outside the plausible range [{}, {}] — reading density doesn't " +
                                "match the 15-min-interval assumption. Omitting sleep_duration " +
                                "from the baseline rather than feeding a bad number into scores.",
                                avgHours, accountId, MIN_PLAUSIBLE_SLEEP_HOURS, MAX_PLAUSIBLE_SLEEP_HOURS);
                    } else {
                        baselines.put("sleep_duration", avgHours);
                        sampleSizes.put("sleep_duration", sleepDays);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to compute sleep-duration baseline for account {}: {}", accountId, e.getMessage(), e);
        }
    }

    private int countDaysOfData(Long accountId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT DATE(time)) FROM measurements WHERE account_id = ?",
                    Integer.class, accountId
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to count days of data for account {}: {}", accountId, e.getMessage(), e);
            return 0;
        }
    }

    private String computeConfidence(int baselineDays, int metricCount) {
        if (baselineDays < PROVISIONAL_THRESHOLD_DAYS || metricCount < 2) {
            return "low";
        }
        if (baselineDays >= CHRONIC_WINDOW_DAYS && metricCount >= 4) {
            return "high";
        }
        return "medium";
    }
}
