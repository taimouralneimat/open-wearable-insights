package com.openwearableinsights.api.sleep.application;

import com.openwearableinsights.api.sleep.domain.SleepSummary;
import com.openwearableinsights.api.sleep.domain.SleepSummary.SleepStagePoint;
import com.openwearableinsights.api.sleep.domain.SleepTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes real sleep summaries from sleep_stage measurements.
 *
 * <p>Stage encoding matches packages/test-data/synthetic_generator.py:
 * SLEEP_STAGES = ["deep", "rem", "light", "awake"], value = list index.
 * Each reading represents a 15-minute interval — the same approximation
 * used by CurrentMetricsService/BaselineService for the readiness score,
 * kept consistent here rather than inventing a second convention.
 *
 * <p>Unlike the readiness score (which excludes unreliable sleep data from
 * the blended factor rather than showing a misleading number), this is a
 * dedicated sleep view — showing the computed value with an honest
 * low-confidence caveat is more useful here than hiding it entirely.
 */
@Service
public class SleepInsightService {

    private static final Logger log = LoggerFactory.getLogger(SleepInsightService.class);

    private static final List<String> STAGE_NAMES = List.of("deep", "rem", "light", "awake");
    private static final double HOURS_PER_READING = 0.25;
    private static final double TARGET_SLEEP_HOURS = 7.5;

    // A full night at 15-min intervals is ~32 readings; below this we're
    // clearly looking at a partial/sparse night, not a real full session.
    private static final int LOW_CONFIDENCE_READING_THRESHOLD = 16;
    private static final int HIGH_CONFIDENCE_READING_THRESHOLD = 28;

    private final JdbcTemplate jdbcTemplate;

    public SleepInsightService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Compute the most recent night's sleep summary. Empty if no sleep_stage
     * data exists at all for this account.
     */
    public Optional<SleepSummary> computeLatestSummary(Long accountId) {
        Optional<LocalDate> latestDate = findMostRecentSleepDate(accountId);
        return latestDate.map(date -> computeSummaryForDate(accountId, date));
    }

    /**
     * Compute summaries for up to {@code days} most recent nights with data.
     * Returns fewer than {@code days} points if less history exists —
     * never fabricates missing nights.
     */
    public List<SleepTrendPoint> computeTrends(Long accountId, int days) {
        List<LocalDate> dates = findRecentSleepDates(accountId, days);
        List<SleepTrendPoint> trends = new ArrayList<>();
        for (LocalDate date : dates) {
            SleepSummary s = computeSummaryForDate(accountId, date);
            trends.add(new SleepTrendPoint(
                    date.toString(), s.totalHours(), s.deepHours(), s.remHours(),
                    s.lightHours(), s.awakeHours(), s.sleepScore()
            ));
        }
        return trends;
    }

    private SleepSummary computeSummaryForDate(Long accountId, LocalDate date) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT value FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'sleep_stage' AND DATE(time) = ?",
                    accountId, date
            );

            double[] hoursByStage = new double[STAGE_NAMES.size()];
            List<SleepStagePoint> stages = new ArrayList<>();
            List<Map<String, Object>> ordered = jdbcTemplate.queryForList(
                    "SELECT time, value FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'sleep_stage' AND DATE(time) = ? " +
                    "ORDER BY time",
                    accountId, date
            );
            for (Map<String, Object> row : ordered) {
                int stageIndex = ((Number) row.get("value")).intValue();
                if (stageIndex < 0 || stageIndex >= STAGE_NAMES.size()) continue;
                hoursByStage[stageIndex] += HOURS_PER_READING;
                Timestamp t = (Timestamp) row.get("time");
                stages.add(new SleepStagePoint(
                        t.toInstant().atZone(ZoneOffset.UTC).toLocalTime().toString(),
                        STAGE_NAMES.get(stageIndex)
                ));
            }

            double deepHours = hoursByStage[0];
            double remHours = hoursByStage[1];
            double lightHours = hoursByStage[2];
            double awakeHours = hoursByStage[3];
            double totalHours = deepHours + remHours + lightHours + awakeHours;

            int readingCount = rows.size();
            String confidence = computeConfidence(readingCount);
            int sleepScore = computeSleepScore(totalHours, deepHours, remHours);

            List<String> limitations = new ArrayList<>();
            limitations.add("Stage duration is approximated from reading density " +
                    "(15 min/reading) — not full polysomnography-grade session parsing.");
            if ("low".equals(confidence)) {
                limitations.add(String.format(
                        "Only %d reading(s) for this night (a full night is ~32 at 15-min " +
                        "intervals) — this is a partial/sparse sample, not a full session.",
                        readingCount));
            }

            return new SleepSummary(
                    totalHours, deepHours, remHours, lightHours, awakeHours,
                    sleepScore, stages, confidence, limitations
            );
        } catch (Exception e) {
            log.warn("Failed to compute sleep summary for account {} on {}: {}",
                    accountId, date, e.getMessage(), e);
            return new SleepSummary(0, 0, 0, 0, 0, 0, List.of(), "none",
                    List.of("Failed to compute sleep summary: " + e.getMessage()));
        }
    }

    private Optional<LocalDate> findMostRecentSleepDate(Long accountId) {
        try {
            List<LocalDate> dates = jdbcTemplate.queryForList(
                    "SELECT DISTINCT DATE(time) FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'sleep_stage' " +
                    "ORDER BY DATE(time) DESC LIMIT 1",
                    LocalDate.class, accountId
            );
            return dates.isEmpty() ? Optional.empty() : Optional.of(dates.get(0));
        } catch (Exception e) {
            log.warn("Failed to find most recent sleep date for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private List<LocalDate> findRecentSleepDates(Long accountId, int limit) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT DATE(time) FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'sleep_stage' " +
                    "ORDER BY DATE(time) DESC LIMIT ?",
                    LocalDate.class, accountId, limit
            ).reversed();
        } catch (Exception e) {
            log.warn("Failed to find recent sleep dates for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    private String computeConfidence(int readingCount) {
        if (readingCount < LOW_CONFIDENCE_READING_THRESHOLD) return "low";
        if (readingCount >= HIGH_CONFIDENCE_READING_THRESHOLD) return "high";
        return "medium";
    }

    /**
     * v0.1 original methodology — not a reproduction of any vendor formula.
     * Half weight on duration vs. a 7.5h target, half weight on the
     * proportion of sleep spent in deep/REM (commonly considered the more
     * restorative stages in general sleep science, not a proprietary claim).
     */
    private int computeSleepScore(double totalHours, double deepHours, double remHours) {
        if (totalHours <= 0) return 0;
        double durationRatio = Math.min(totalHours / TARGET_SLEEP_HOURS, 1.0);
        double restorativeRatio = (deepHours + remHours) / totalHours;
        double score = 100 * (0.5 * durationRatio + 0.5 * restorativeRatio);
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }
}
