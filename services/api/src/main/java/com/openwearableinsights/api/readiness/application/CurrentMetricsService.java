package com.openwearableinsights.api.readiness.application;

import com.openwearableinsights.api.readiness.domain.CurrentMetrics;
import com.openwearableinsights.api.shared.LocalDayClock;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
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
    private final TrainingLoadService trainingLoadService;
    private final LocalDayClock localDayClock;

    public CurrentMetricsService(JdbcTemplate jdbcTemplate, TrainingLoadService trainingLoadService, LocalDayClock localDayClock) {
        this.jdbcTemplate = jdbcTemplate;
        this.trainingLoadService = trainingLoadService;
        this.localDayClock = localDayClock;
    }

    /**
     * Fetch current-day metrics for the default account.
     * Returns the most recent values for each metric type.
     */
    public CurrentMetrics fetchCurrent() {
        return fetchCurrent(DEFAULT_ACCOUNT_ID);
    }

    public CurrentMetrics fetchCurrent(Long accountId) {
        // Local midnight, not UTC midnight — see LocalDayClock's javadoc for
        // why this matters: UTC-midnight truncation silently misattributes
        // up to ~N hours of "today's" readings (N = the account's real UTC
        // offset) to the wrong day for anyone not in UTC.
        Instant todayStart = localDayClock.startOfToday();

        Optional<Double> hrv = fetchLatestMetric(accountId, "hrv", todayStart);
        Optional<Double> rhr = fetchLatestMetric(accountId, "rhr", todayStart);
        Optional<Double> stress = fetchLatestMetric(accountId, "stress", todayStart);
        Optional<Double> sleepDuration = fetchSleepDuration(accountId, todayStart);
        // Garmin-exclusive — see garminconnect module. Same "today only" rule
        // as the other metrics above: if Garmin hasn't synced today's value
        // yet, this is honestly missing rather than fed in stale.
        Optional<Double> bodyBatteryLow = fetchLatestMetric(accountId, "body_battery_low", todayStart);

        // Training load: real HR-zone-weighted load from workout sessions
        // (trainingload.application.TrainingLoadService), averaged per
        // active day over the 7-day (acute) and 28-day (chronic) windows.
        // Replaces the step-count proxy this used before that module existed.
        double acuteLoad = trainingLoadService.fetchAcuteLoad(accountId);
        double chronicLoad = trainingLoadService.fetchChronicLoad(accountId);

        // Sleep need: intentionally empty here, not a hardcoded default.
        // ReadinessCalculator.calculate(CurrentMetrics, PersonalBaseline) already
        // falls back to the account's real "sleep_duration" rolling baseline
        // (BaselineService) when this is empty — that fallback was previously
        // dead code because this was always Optional.of(7.5), silently feeding
        // the same flat number into every user's readiness score regardless of
        // their own history. See sleep.application.SleepInsightService for the
        // same baseline reused (and finally surfaced) on the Sleep view itself.
        Optional<Double> sleepNeed = Optional.empty();

        // Data completeness: fraction of expected metrics present. Body
        // Battery is intentionally excluded from this count — it's an
        // enrichment on top of the core metric set, not a core expectation,
        // so accounts without it (no Garmin Connect sync yet) shouldn't be
        // marked as having lower-quality data than they actually do.
        int expectedMetrics = 5; // hrv, rhr, stress, sleep, training load
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
                bodyBatteryLow,
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
}
