package com.openwearableinsights.api.garminconnect.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Surfaces the Garmin-exclusive metrics {@link GarminConnectSyncService} pulls
 * but the core deterministic engine (readiness/sleep/baselines) deliberately
 * doesn't touch — Body Battery and Garmin's own proprietary Training
 * Readiness score. Kept as their own read model, separate from
 * {@code readiness.application.CurrentMetricsService}, so Garmin's opinion
 * and this app's own computed opinion stay visibly distinct rather than
 * getting silently blended into one number.
 */
@Service
public class GarminEnrichmentService {

    private static final int TREND_DAYS = 14;

    private final JdbcTemplate jdbcTemplate;

    public GarminEnrichmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The most recent day with Body Battery and/or Training Readiness data —
     * not strictly "today", since Garmin's own daily aggregates lag behind
     * real-time by design (see GarminConnectSyncService), and requiring
     * "today" specifically would show an honest-but-unhelpful empty state
     * most of the day. The returned date makes clear how fresh this actually is.
     */
    public TodaySnapshot today(Long accountId) {
        Optional<DatedValue> charged = latest(accountId, "body_battery_charged");
        Optional<DatedValue> drained = latest(accountId, "body_battery_drained");
        Optional<DatedValue> high = latest(accountId, "body_battery_high");
        Optional<DatedValue> low = latest(accountId, "body_battery_low");
        Optional<DatedValue> readiness = latest(accountId, "garmin_training_readiness");

        LocalDate bodyBatteryDate = high.map(DatedValue::date).orElse(null);
        LocalDate readinessDate = readiness.map(DatedValue::date).orElse(null);

        return new TodaySnapshot(
                bodyBatteryDate,
                high.map(DatedValue::value).orElse(null),
                low.map(DatedValue::value).orElse(null),
                charged.map(DatedValue::value).orElse(null),
                drained.map(DatedValue::value).orElse(null),
                readinessDate,
                readiness.map(v -> (int) v.value()).orElse(null)
        );
    }

    /** Body Battery high/low per day over the last {@value #TREND_DAYS} days, oldest first. */
    public List<BodyBatteryTrendPoint> bodyBatteryTrend(Long accountId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE(time) AS d, " +
                "MAX(value) FILTER (WHERE metric_type = 'body_battery_high') AS high, " +
                "MAX(value) FILTER (WHERE metric_type = 'body_battery_low') AS low " +
                "FROM measurements " +
                "WHERE account_id = ? AND metric_type IN ('body_battery_high', 'body_battery_low') " +
                "AND time >= ? " +
                "GROUP BY DATE(time) ORDER BY DATE(time)",
                accountId, Timestamp.from(LocalDate.now(ZoneOffset.UTC).minusDays(TREND_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant())
        );

        return rows.stream()
                .map(row -> new BodyBatteryTrendPoint(
                        ((java.sql.Date) row.get("d")).toLocalDate(),
                        row.get("high") != null ? ((Number) row.get("high")).doubleValue() : null,
                        row.get("low") != null ? ((Number) row.get("low")).doubleValue() : null
                ))
                .toList();
    }

    private Optional<DatedValue> latest(Long accountId, String metricType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT time, value FROM measurements WHERE account_id = ? AND metric_type = ? " +
                "ORDER BY time DESC LIMIT 1",
                accountId, metricType
        );
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        LocalDate date = ((Timestamp) row.get("time")).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        double value = ((Number) row.get("value")).doubleValue();
        return Optional.of(new DatedValue(date, value));
    }

    private record DatedValue(LocalDate date, double value) {}

    public record TodaySnapshot(
            LocalDate bodyBatteryAsOf,
            Double bodyBatteryHigh,
            Double bodyBatteryLow,
            Double bodyBatteryCharged,
            Double bodyBatteryDrained,
            LocalDate trainingReadinessAsOf,
            Integer garminTrainingReadiness
    ) {}

    public record BodyBatteryTrendPoint(LocalDate date, Double high, Double low) {}
}
