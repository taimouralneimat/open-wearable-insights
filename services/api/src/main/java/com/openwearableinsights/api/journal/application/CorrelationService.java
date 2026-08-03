package com.openwearableinsights.api.journal.application;

import com.openwearableinsights.api.journal.domain.BehaviorCorrelation;
import com.openwearableinsights.api.journal.domain.GarminSignalCorrelation;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Computes exploratory correlations between logged journal behaviors and
 * readiness scores.
 *
 * <p>This is a simple group-mean comparison (readiness on days a behavior
 * was logged vs. days it wasn't) — not a causal model, and not dressed up
 * with statistics (like p-values) that a handful of data points can't
 * actually support. Every result requires a minimum sample size in BOTH
 * groups and always carries an explicit correlation-not-causation caution,
 * per docs/product/parity-matrix.md row 6.
 */
@Service
public class CorrelationService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);

    // Below this in either group, a mean comparison is too thin to show at
    // all — not just low-confidence, genuinely not worth surfacing.
    private static final int MIN_SAMPLE_SIZE_PER_GROUP = 3;
    // Below this, still shown but marked "low" rather than "medium" —
    // "high" is never used for a comparison this small, by design.
    private static final int MEDIUM_CONFIDENCE_THRESHOLD = 5;

    private final JdbcTemplate jdbcTemplate;
    private final ReadinessScoreHistoryRepository readinessScoreHistoryRepository;

    public CorrelationService(JdbcTemplate jdbcTemplate,
                               ReadinessScoreHistoryRepository readinessScoreHistoryRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.readinessScoreHistoryRepository = readinessScoreHistoryRepository;
    }

    public List<BehaviorCorrelation> computeCorrelations(Long accountId) {
        Map<LocalDate, Integer> readinessByDate = readinessScoreHistoryRepository.findScoresByAccountId(accountId);
        if (readinessByDate.isEmpty()) {
            return List.of();
        }

        Map<String, Set<LocalDate>> loggedDatesByBehavior = fetchLoggedDatesByBehavior(accountId);

        List<BehaviorCorrelation> results = new ArrayList<>();
        for (Map.Entry<String, Set<LocalDate>> entry : loggedDatesByBehavior.entrySet()) {
            String[] parts = entry.getKey().split("::", 2);
            String category = parts[0];
            String behavior = parts[1];
            Set<LocalDate> loggedDates = entry.getValue();

            List<Integer> loggedScores = new ArrayList<>();
            List<Integer> notLoggedScores = new ArrayList<>();
            for (Map.Entry<LocalDate, Integer> r : readinessByDate.entrySet()) {
                if (loggedDates.contains(r.getKey())) {
                    loggedScores.add(r.getValue());
                } else {
                    notLoggedScores.add(r.getValue());
                }
            }

            if (loggedScores.size() < MIN_SAMPLE_SIZE_PER_GROUP
                    || notLoggedScores.size() < MIN_SAMPLE_SIZE_PER_GROUP) {
                continue;
            }

            double avgLogged = average(loggedScores);
            double avgNotLogged = average(notLoggedScores);
            int minGroupSize = Math.min(loggedScores.size(), notLoggedScores.size());
            String confidence = minGroupSize >= MEDIUM_CONFIDENCE_THRESHOLD ? "medium" : "low";

            results.add(new BehaviorCorrelation(
                    category, behavior, loggedScores.size(), notLoggedScores.size(),
                    avgLogged, avgNotLogged, avgLogged - avgNotLogged, confidence,
                    List.of(
                            "This is a correlation, not causation — logging this behavior " +
                            "doesn't mean it caused any change in your readiness. Many other " +
                            "factors vary day to day too.",
                            String.format("Based on %d logged day(s) vs. %d non-logged day(s) — " +
                                    "a small sample. Treat this as a pattern worth watching, not a conclusion.",
                                    loggedScores.size(), notLoggedScores.size())
                    )
            ));
        }

        results.sort(Comparator.comparingDouble((BehaviorCorrelation c) -> Math.abs(c.difference())).reversed());
        return results;
    }

    private Map<String, Set<LocalDate>> fetchLoggedDatesByBehavior(Long accountId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT behavior, time FROM journal_entries WHERE account_id = ?",
                    accountId
            );
            Map<String, Set<LocalDate>> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String stored = (String) row.get("behavior");
                String key = stored != null && stored.contains("::") ? stored : "Other::" + stored;
                LocalDate date = ((Timestamp) row.get("time")).toInstant()
                        .atZone(ZoneOffset.UTC).toLocalDate();
                map.computeIfAbsent(key, k -> new HashSet<>()).add(date);
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to fetch journal entries for account {}: {}", accountId, e.getMessage(), e);
            return Map.of();
        }
    }

    private double average(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    // -- Garmin-exclusive signal correlations (Body Battery, Training Readiness) -- //

    // Fixed order so results are stable across calls; List.of(Map.entry(...))
    // rather than Map.of(...), which doesn't preserve insertion order.
    private static final List<Map.Entry<String, String>> GARMIN_SIGNALS = List.of(
            Map.entry("body_battery_low", "Body Battery low"),
            Map.entry("garmin_training_readiness", "Garmin Training Readiness")
    );

    /**
     * Same group-mean-comparison methodology as {@link #computeCorrelations},
     * against Garmin-exclusive signals instead of this app's own readiness
     * score — e.g. "your Body Battery runs higher on days you log an evening
     * walk." Neither Garmin's own app nor a competitor's offers this: it
     * requires both the richer Garmin data (garminconnect module) and a
     * user's own logged behaviors, combined. Only present once a Garmin
     * Connect sync has provided the signal — silently skipped otherwise,
     * same as every other optional data source in this app.
     */
    public List<GarminSignalCorrelation> computeGarminSignalCorrelations(Long accountId) {
        Map<String, Set<LocalDate>> loggedDatesByBehavior = fetchLoggedDatesByBehavior(accountId);
        if (loggedDatesByBehavior.isEmpty()) {
            return List.of();
        }

        List<GarminSignalCorrelation> results = new ArrayList<>();
        for (Map.Entry<String, String> signal : GARMIN_SIGNALS) {
            Map<LocalDate, Double> signalByDate = fetchDailyMetricAverage(accountId, signal.getKey());
            if (signalByDate.isEmpty()) continue;

            for (Map.Entry<String, Set<LocalDate>> entry : loggedDatesByBehavior.entrySet()) {
                String[] parts = entry.getKey().split("::", 2);
                String category = parts[0];
                String behavior = parts[1];
                Set<LocalDate> loggedDates = entry.getValue();

                List<Double> loggedValues = new ArrayList<>();
                List<Double> notLoggedValues = new ArrayList<>();
                for (Map.Entry<LocalDate, Double> r : signalByDate.entrySet()) {
                    if (loggedDates.contains(r.getKey())) {
                        loggedValues.add(r.getValue());
                    } else {
                        notLoggedValues.add(r.getValue());
                    }
                }

                if (loggedValues.size() < MIN_SAMPLE_SIZE_PER_GROUP
                        || notLoggedValues.size() < MIN_SAMPLE_SIZE_PER_GROUP) {
                    continue;
                }

                double avgLogged = averageDouble(loggedValues);
                double avgNotLogged = averageDouble(notLoggedValues);
                int minGroupSize = Math.min(loggedValues.size(), notLoggedValues.size());
                String confidence = minGroupSize >= MEDIUM_CONFIDENCE_THRESHOLD ? "medium" : "low";

                results.add(new GarminSignalCorrelation(
                        signal.getValue(), category, behavior,
                        loggedValues.size(), notLoggedValues.size(),
                        avgLogged, avgNotLogged, avgLogged - avgNotLogged, confidence,
                        List.of(
                                "This is a correlation, not causation — logging this behavior " +
                                "doesn't mean it caused any change in " + signal.getValue() + ". Many other " +
                                "factors vary day to day too.",
                                String.format("Based on %d logged day(s) vs. %d non-logged day(s) — " +
                                        "a small sample. Treat this as a pattern worth watching, not a conclusion.",
                                        loggedValues.size(), notLoggedValues.size())
                        )
                ));
            }
        }

        results.sort(Comparator.comparingDouble((GarminSignalCorrelation c) -> Math.abs(c.difference())).reversed());
        return results;
    }

    private Map<LocalDate, Double> fetchDailyMetricAverage(Long accountId, String metricType) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DATE(time) AS d, AVG(value) AS v FROM measurements " +
                    "WHERE account_id = ? AND metric_type = ? GROUP BY DATE(time)",
                    accountId, metricType
            );
            Map<LocalDate, Double> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                LocalDate date = ((java.sql.Date) row.get("d")).toLocalDate();
                map.put(date, ((Number) row.get("v")).doubleValue());
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to fetch daily '{}' for account {}: {}", metricType, accountId, e.getMessage(), e);
            return Map.of();
        }
    }

    private double averageDouble(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
