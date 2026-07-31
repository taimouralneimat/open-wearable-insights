package com.openwearableinsights.api.administration.application;

import com.openwearableinsights.api.administration.adapter.in.DataQualityController.DataQualitySummary;
import com.openwearableinsights.api.administration.adapter.in.DataQualityController.MetricQuality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes real data-quality metrics from the measurements table, replacing
 * the hardcoded placeholder values {@link com.openwearableinsights.api.administration.adapter.in.DataQualityController}
 * used to return unconditionally (parity-matrix row #12).
 *
 * <p>The canonical metric list below matches the comment on the
 * {@code measurements.metric_type} column in V01__initial_schema.sql —
 * some of these (rhr, calories, spo2, respiration) aren't produced by
 * GarminFitConnector today (see ADR-0006's known limitations), so they
 * honestly show near-zero coverage rather than being silently omitted.
 * That's real, useful signal for this dashboard's actual purpose, not a bug.
 */
@Service
public class DataQualityService {

    private static final Logger log = LoggerFactory.getLogger(DataQualityService.class);

    private static final int WINDOW_DAYS = 7;
    private static final String ALGORITHM_VERSION = "dataquality-v1";

    private static final List<String> CANONICAL_METRICS = List.of(
            "hr", "hrv", "rhr", "sleep_stage", "steps", "calories", "stress", "spo2", "respiration"
    );

    private final JdbcTemplate jdbcTemplate;

    public DataQualityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DataQualitySummary computeSummary(Long accountId) {
        Instant since = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS);

        Map<String, MetricStats> statsByType = fetchMetricStats(accountId, since);

        List<MetricQuality> metrics = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        double coverageSum = 0.0;

        for (String metricType : CANONICAL_METRICS) {
            MetricStats stats = statsByType.get(metricType);
            double coverage = stats == null ? 0.0 : Math.min(1.0, stats.daysWithData / (double) WINDOW_DAYS);
            coverageSum += coverage;

            String quality = coverage >= 0.8 ? "good" : coverage >= 0.5 ? "fair" : "poor";
            String frequency = stats == null
                    ? "no data"
                    : String.format("%.1f readings/day", stats.count / (double) Math.max(1, stats.daysWithData));

            metrics.add(new MetricQuality(displayName(metricType), coverage, quality, frequency));

            if (stats == null) {
                issues.add(displayName(metricType) + " has no data in the last " + WINDOW_DAYS + " days.");
            } else if (coverage < 0.5) {
                issues.add(String.format("%s coverage is low (%.0f%%) in the last %d days.",
                        displayName(metricType), coverage * 100, WINDOW_DAYS));
            }
        }

        double completeness = coverageSum / CANONICAL_METRICS.size();

        Instant mostRecent = statsByType.values().stream()
                .map(s -> s.lastReading)
                .max(Instant::compareTo)
                .orElse(null);
        String freshness = freshnessLabel(mostRecent);

        int daysOfData = fetchDaysWithAnyData(accountId, since);
        int totalSources = fetchDistinctSourceCount(accountId);

        List<String> limitations = new ArrayList<>();
        limitations.add("Computed from the last " + WINDOW_DAYS + " days of real measurement data for this account.");
        if (totalSources == 0) {
            limitations.add("No data has been imported yet — every figure above reflects an empty account, not a measurement problem.");
        }

        return new DataQualitySummary(
                completeness, freshness, daysOfData, totalSources,
                metrics, issues, ALGORITHM_VERSION,
                totalSources == 0 ? "none" : "medium",
                limitations
        );
    }

    private Map<String, MetricStats> fetchMetricStats(Long accountId, Instant since) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT metric_type, count(*) as cnt, count(DISTINCT DATE(time)) as days_with_data, max(time) as last_reading " +
                    "FROM measurements WHERE account_id = ? AND time >= ? GROUP BY metric_type",
                    accountId, Timestamp.from(since)
            );
            Map<String, MetricStats> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                result.put(
                        (String) row.get("metric_type"),
                        new MetricStats(
                                ((Number) row.get("cnt")).longValue(),
                                ((Number) row.get("days_with_data")).intValue(),
                                ((Timestamp) row.get("last_reading")).toInstant()
                        )
                );
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch metric stats for account {}: {}", accountId, e.getMessage(), e);
            return Map.of();
        }
    }

    private int fetchDaysWithAnyData(Long accountId, Instant since) {
        try {
            Integer days = jdbcTemplate.queryForObject(
                    "SELECT count(DISTINCT DATE(time)) FROM measurements WHERE account_id = ? AND time >= ?",
                    Integer.class, accountId, Timestamp.from(since)
            );
            return days != null ? days : 0;
        } catch (Exception e) {
            log.warn("Failed to fetch days-with-data for account {}: {}", accountId, e.getMessage(), e);
            return 0;
        }
    }

    private int fetchDistinctSourceCount(Long accountId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(DISTINCT p.source) FROM provenance p " +
                    "JOIN import_batches ib ON p.import_batch_id = ib.id " +
                    "WHERE ib.account_id = ?",
                    Integer.class, accountId
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to fetch source count for account {}: {}", accountId, e.getMessage(), e);
            return 0;
        }
    }

    private String freshnessLabel(Instant mostRecent) {
        if (mostRecent == null) {
            return "no data";
        }
        long hoursOld = ChronoUnit.HOURS.between(mostRecent, Instant.now());
        if (hoursOld <= 24) return "fresh";
        if (hoursOld <= 72) return "stale";
        return "very stale";
    }

    private String displayName(String metricType) {
        return switch (metricType) {
            case "hr" -> "Heart rate";
            case "hrv" -> "HRV";
            case "rhr" -> "Resting HR";
            case "sleep_stage" -> "Sleep";
            case "steps" -> "Steps";
            case "calories" -> "Calories";
            case "stress" -> "Stress";
            case "spo2" -> "SpO2";
            case "respiration" -> "Respiration";
            default -> metricType;
        };
    }

    private record MetricStats(long count, int daysWithData, Instant lastReading) {}
}
