package com.openwearableinsights.api.sleep.application;

import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.sleep.domain.SleepConsistency;
import com.openwearableinsights.api.sleep.domain.SleepDebt;
import com.openwearableinsights.api.sleep.domain.SleepPlan;
import com.openwearableinsights.api.sleep.domain.SleepSummary;
import com.openwearableinsights.api.sleep.domain.SleepSummary.SleepStagePoint;
import com.openwearableinsights.api.sleep.domain.SleepTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    // Rolling window for accumulated sleep debt (docs/analytics/sleep-methodology.md:
    // "deficit/surplus accumulated over a recent window").
    private static final int DEBT_WINDOW_DAYS = 14;

    // Sleep Planner (parity row #20): how many recent nights inform the
    // inferred "usual wake time", and how debt gets repaid.
    private static final int WAKE_TIME_WINDOW_DAYS = 7;
    private static final int MIN_NIGHTS_FOR_WAKE_TIME = 3;
    // Repay debt gradually rather than recommending one huge catch-up night —
    // a week's worth of accumulated debt spread across a week, capped so a
    // large debt never turns into an absurd "sleep for 14 hours" suggestion.
    private static final double DEBT_REPAYMENT_FRACTION = 1.0 / 7.0;
    private static final double MAX_DEBT_REPAYMENT_HOURS = 1.0;

    // Sleep consistency (parity row #3): wider window than wake-time
    // inference above since it's now well-powered by real Garmin Connect
    // history, not just recent FIT imports.
    private static final int CONSISTENCY_WINDOW_DAYS = 14;
    private static final int MIN_NIGHTS_FOR_CONSISTENCY = 5;
    // Consistency score floor: 100+ minutes average deviation maps to 0.
    private static final double CONSISTENCY_ZERO_AT_MINUTES = 100.0;
    private static final int MEDIUM_CONFIDENCE_NIGHTS = 10;

    private final JdbcTemplate jdbcTemplate;
    private final BaselineService baselineService;
    // Measurements are stored as UTC instants (correct — see V01 schema),
    // but bed/wake clock-times shown to the user must be their own local
    // time, not the storage timezone. This app runs entirely on the user's
    // own machine (ADR-0008), so the JVM's default zone IS the user's real
    // zone — no config needed. Only affects display formatting here; day
    // bucketing (DATE(time) in the SQL below) deliberately stays UTC-based
    // and is NOT touched by this — a real, separate, larger question (would
    // a session crossing local midnight near a UTC-day boundary ever get
    // split or merged across two DATE(time) buckets?) that needs its own
    // dedicated look, not a same-night fix bundled in here.
    private final ZoneId displayZone;

    @Autowired
    public SleepInsightService(JdbcTemplate jdbcTemplate, BaselineService baselineService) {
        this(jdbcTemplate, baselineService, ZoneId.systemDefault());
    }

    /** Explicit-zone seam — mainly so test clock-time assertions don't depend on the test runner's host timezone. */
    public SleepInsightService(JdbcTemplate jdbcTemplate, BaselineService baselineService, ZoneId displayZone) {
        this.jdbcTemplate = jdbcTemplate;
        this.baselineService = baselineService;
        this.displayZone = displayZone;
    }

    /**
     * Compute the most recent night's sleep summary. Empty if no sleep_stage
     * data exists at all for this account.
     */
    public Optional<SleepSummary> computeLatestSummary(Long accountId) {
        Optional<LocalDate> latestDate = findMostRecentSleepDate(accountId);
        Double personalNeed = personalSleepNeedHours(accountId);
        return latestDate.map(date -> computeSummaryForDate(accountId, date, personalNeed));
    }

    // Rollup thresholds — same values and reasoning as
    // ActivityInsightService's WEEKLY_ROLLUP_THRESHOLD_DAYS/
    // MONTHLY_ROLLUP_THRESHOLD_DAYS (parity-matrix.md row 9): beyond a few
    // weeks, one bar per real night stops being readable.
    private static final int WEEKLY_ROLLUP_THRESHOLD_DAYS = 31;
    private static final int MONTHLY_ROLLUP_THRESHOLD_DAYS = 120;

    /**
     * Compute summaries for up to {@code days} most recent nights with data.
     * Returns fewer than {@code days} points if less history exists —
     * never fabricates missing nights.
     *
     * <p>For {@code days > 31} this returns weekly points instead of nightly
     * ones, and for {@code days > 120} monthly points — each of the six
     * numeric fields averaged across the real nights with data in that
     * bucket (never zero-filled, never summed), with {@link
     * SleepTrendPoint#granularity()} disclosing which. Same reasoning as
     * {@code ActivityInsightService#computeStepTrends}'s identical rollup.
     */
    public List<SleepTrendPoint> computeTrends(Long accountId, int days) {
        List<LocalDate> dates = findRecentSleepDates(accountId, days);
        Double personalNeed = personalSleepNeedHours(accountId);
        List<SleepTrendPoint> nightly = new ArrayList<>();
        for (LocalDate date : dates) {
            SleepSummary s = computeSummaryForDate(accountId, date, personalNeed);
            nightly.add(new SleepTrendPoint(
                    date.toString(), s.totalHours(), s.deepHours(), s.remHours(),
                    s.lightHours(), s.awakeHours(), s.sleepScore(), "day"
            ));
        }

        if (days <= WEEKLY_ROLLUP_THRESHOLD_DAYS) {
            return nightly;
        }
        String granularity = days <= MONTHLY_ROLLUP_THRESHOLD_DAYS ? "week" : "month";
        return bucketAverage(nightly, granularity);
    }

    /** Buckets real nightly points into weekly/monthly averages — see {@link #computeTrends}. */
    private List<SleepTrendPoint> bucketAverage(List<SleepTrendPoint> nightly, String granularity) {
        Map<LocalDate, List<SleepTrendPoint>> byBucket = new LinkedHashMap<>();
        for (SleepTrendPoint p : nightly) {
            LocalDate date = LocalDate.parse(p.date());
            LocalDate bucketStart = "week".equals(granularity)
                    ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : date.withDayOfMonth(1);
            byBucket.computeIfAbsent(bucketStart, k -> new ArrayList<>()).add(p);
        }
        List<SleepTrendPoint> trends = new ArrayList<>();
        for (Map.Entry<LocalDate, List<SleepTrendPoint>> entry : byBucket.entrySet()) {
            List<SleepTrendPoint> points = entry.getValue();
            int n = points.size();
            trends.add(new SleepTrendPoint(
                    entry.getKey().toString(),
                    points.stream().mapToDouble(SleepTrendPoint::totalHours).sum() / n,
                    points.stream().mapToDouble(SleepTrendPoint::deepHours).sum() / n,
                    points.stream().mapToDouble(SleepTrendPoint::remHours).sum() / n,
                    points.stream().mapToDouble(SleepTrendPoint::lightHours).sum() / n,
                    points.stream().mapToDouble(SleepTrendPoint::awakeHours).sum() / n,
                    (int) Math.round(points.stream().mapToInt(SleepTrendPoint::sleepScore).average().orElse(0)),
                    granularity
            ));
        }
        trends.sort((a, b) -> a.date().compareTo(b.date()));
        return trends;
    }

    /**
     * Accumulated sleep debt/surplus over the last {@value #DEBT_WINDOW_DAYS}
     * days: sum of (personal need - actual) across nights with real data.
     * Positive = net debt, negative = net surplus. Nights with no data are
     * skipped entirely, never assumed to be zero — a sparse history simply
     * reduces nightsConsidered and confidence, rather than corrupting the sum.
     */
    public SleepDebt computeSleepDebt(Long accountId) {
        Double personalNeed = personalSleepNeedHours(accountId);
        List<String> limitations = new ArrayList<>();
        limitations.add("Need is your own rolling average actual sleep duration, not a " +
                "physiological requirement — an estimate, not a clinical recommendation.");

        if (personalNeed == null) {
            limitations.add("Not enough sleep history yet to estimate a personal need — " +
                    "keep the app importing sleep data and this will populate.");
            return new SleepDebt(null, null, 0, DEBT_WINDOW_DAYS, "none", limitations);
        }

        List<LocalDate> dates = findRecentSleepDates(accountId, DEBT_WINDOW_DAYS);
        double accumulated = 0.0;
        for (LocalDate date : dates) {
            SleepSummary s = computeSummaryForDate(accountId, date, personalNeed);
            if (s.totalHours() > 0) {
                accumulated += (personalNeed - s.totalHours());
            }
        }

        String confidence = dates.size() >= 10 ? "medium" : dates.size() >= 4 ? "low" : "none";
        if (dates.size() < DEBT_WINDOW_DAYS) {
            limitations.add(String.format("Only %d of the last %d nights have real sleep data.",
                    dates.size(), DEBT_WINDOW_DAYS));
        }

        return new SleepDebt(personalNeed, accumulated, dates.size(), DEBT_WINDOW_DAYS, confidence, limitations);
    }

    /**
     * Tonight's bedtime recommendation (parity row #20) — a direct
     * extension of {@link #computeSleepDebt}: the target wake time is
     * inferred from the account's own recent pattern (average time-of-day
     * of the last sleep_stage reading each night), the target sleep
     * duration is personal need plus a capped, gradual debt repayment, and
     * the recommended bedtime is simply wake time minus that duration.
     * Honest empty state — never a fabricated bedtime — when there isn't
     * enough history for either the need or the wake-time pattern.
     */
    public SleepPlan computeSleepPlan(Long accountId) {
        Double personalNeed = personalSleepNeedHours(accountId);
        LocalTime targetWakeTime = inferUsualWakeTime(accountId);

        List<String> limitations = new ArrayList<>();
        limitations.add("Estimate only, not a clinical or circadian-rhythm-validated " +
                "recommendation — treat as a starting point, not a prescription.");

        if (personalNeed == null || targetWakeTime == null) {
            limitations.add(personalNeed == null
                    ? "Not enough sleep history yet for a personal need estimate."
                    : "Not enough recent nights (need at least " + MIN_NIGHTS_FOR_WAKE_TIME +
                            ") to infer your usual wake time.");
            return new SleepPlan(null, null, null, 0.0,
                    "Not enough sleep history yet to recommend a bedtime — keep the app importing sleep data.",
                    "none", limitations);
        }

        SleepDebt debt = computeSleepDebt(accountId);
        double debtHours = debt.accumulatedHours() != null ? debt.accumulatedHours() : 0.0;
        double debtRepayment = clamp(Math.max(0, debtHours) * DEBT_REPAYMENT_FRACTION, 0, MAX_DEBT_REPAYMENT_HOURS);
        double targetSleepHours = personalNeed + debtRepayment;

        LocalTime recommendedBedtime = targetWakeTime.minusMinutes(Math.round(targetSleepHours * 60));

        String reasoning = debtRepayment > 0.05
                ? String.format(
                        "Based on your usual wake time of ~%s and your personal need of ~%.1fh, plus %.1fh " +
                        "extra tonight to gradually catch up on accumulated sleep debt, aim to be asleep by %s.",
                        targetWakeTime, personalNeed, debtRepayment, recommendedBedtime)
                : String.format(
                        "Based on your usual wake time of ~%s and your personal need of ~%.1fh, aim to be asleep by %s.",
                        targetWakeTime, personalNeed, recommendedBedtime);

        String confidence = "high".equals(debt.confidence()) || "medium".equals(debt.confidence()) ? "medium" : "low";

        return new SleepPlan(
                recommendedBedtime.toString(), targetWakeTime.toString(),
                targetSleepHours, debtRepayment, reasoning, confidence, limitations
        );
    }

    /**
     * How consistent bed/wake times have been over the last
     * {@value #CONSISTENCY_WINDOW_DAYS} nights (parity row #3) — a
     * previously entirely-missing metric, distinct from duration/debt.
     *
     * <p>Bedtime and wake time are each represented as minutes-since-noon
     * rather than minutes-since-midnight before averaging — a plain
     * minutes-since-midnight mean breaks for values that straddle midnight
     * (23:30 and 00:15 are 45 minutes apart, not ~23 hours), which is
     * exactly where most real bedtimes cluster. Shifting the wrap-around
     * point to noon (nobody's bed/wake time is anywhere near midday) avoids
     * that without needing full circular statistics.
     */
    public SleepConsistency computeSleepConsistency(Long accountId) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Not a medical measure — a description of how regular your logged " +
                "bed/wake times have been, not a recommendation.");

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT MIN(time) as bedtime, MAX(time) as waketime FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'sleep_stage' AND time >= ? " +
                    "GROUP BY DATE(time)",
                    accountId, Timestamp.from(Instant.now().minus(CONSISTENCY_WINDOW_DAYS, java.time.temporal.ChronoUnit.DAYS))
            );
        } catch (Exception e) {
            log.warn("Failed to compute sleep consistency for account {}: {}", accountId, e.getMessage(), e);
            rows = List.of();
        }

        if (rows.size() < MIN_NIGHTS_FOR_CONSISTENCY) {
            limitations.add("Needs at least " + MIN_NIGHTS_FOR_CONSISTENCY + " nights of real sleep " +
                    "data in the last " + CONSISTENCY_WINDOW_DAYS + " days — only " + rows.size() + " so far.");
            return new SleepConsistency(null, null, null, null, null, rows.size(), "none", limitations);
        }

        List<Integer> bedtimeShifted = new ArrayList<>();
        List<Integer> wakeShifted = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            bedtimeShifted.add(shiftedMinutesSinceNoon(((Timestamp) row.get("bedtime")).toInstant()));
            wakeShifted.add(shiftedMinutesSinceNoon(((Timestamp) row.get("waketime")).toInstant()));
        }

        double avgBedtimeShifted = average(bedtimeShifted);
        double avgWakeShifted = average(wakeShifted);
        double bedtimeStdDev = standardDeviation(bedtimeShifted, avgBedtimeShifted);
        double wakeStdDev = standardDeviation(wakeShifted, avgWakeShifted);

        int consistencyScore = (int) Math.round(
                clamp(100.0 * (1 - ((bedtimeStdDev + wakeStdDev) / 2.0) / CONSISTENCY_ZERO_AT_MINUTES), 0, 100));
        String confidence = rows.size() >= MEDIUM_CONFIDENCE_NIGHTS ? "medium" : "low";

        return new SleepConsistency(
                consistencyScore,
                unshiftToLocalTime(avgBedtimeShifted).toString(),
                unshiftToLocalTime(avgWakeShifted).toString(),
                bedtimeStdDev, wakeStdDev,
                rows.size(), confidence, limitations
        );
    }

    /** Minutes since midnight, then shifted so noon (not midnight) is the wrap-around point — see computeSleepConsistency. */
    private int shiftedMinutesSinceNoon(Instant instant) {
        LocalTime t = instant.atZone(displayZone).toLocalTime();
        int minutesSinceMidnight = t.getHour() * 60 + t.getMinute();
        return (minutesSinceMidnight + 12 * 60) % (24 * 60);
    }

    private LocalTime unshiftToLocalTime(double shiftedMinutes) {
        int minutesSinceMidnight = (((int) Math.round(shiftedMinutes)) + 12 * 60) % (24 * 60);
        return LocalTime.of(minutesSinceMidnight / 60, minutesSinceMidnight % 60);
    }

    private double average(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private double standardDeviation(List<Integer> values, double mean) {
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    /**
     * Average time-of-day of the last sleep_stage reading across recent
     * nights with data — a proxy for "usual wake time" from real behavior,
     * not asked for explicitly. Simple mean of minutes-since-midnight; not
     * circular-mean-corrected, so this is unreliable for wake times very
     * close to midnight (an honest, minor, documented limitation — typical
     * wake times are nowhere near that boundary).
     */
    private LocalTime inferUsualWakeTime(Long accountId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DATE(time) as d, MAX(time) as last_reading FROM measurements " +
                    "WHERE account_id = ? AND metric_type = 'sleep_stage' " +
                    "GROUP BY DATE(time) ORDER BY DATE(time) DESC LIMIT ?",
                    accountId, WAKE_TIME_WINDOW_DAYS
            );
            if (rows.size() < MIN_NIGHTS_FOR_WAKE_TIME) {
                return null;
            }
            int totalMinutes = 0;
            for (Map<String, Object> row : rows) {
                Instant lastReading = ((Timestamp) row.get("last_reading")).toInstant();
                LocalTime t = lastReading.atZone(displayZone).toLocalTime();
                totalMinutes += t.getHour() * 60 + t.getMinute();
            }
            int avgMinutes = totalMinutes / rows.size();
            return LocalTime.of(avgMinutes / 60, avgMinutes % 60);
        } catch (Exception e) {
            log.warn("Failed to infer usual wake time for account {}: {}", accountId, e.getMessage(), e);
            return null;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The account's personal rolling-baseline sleep need — reuses
     * BaselineService's "sleep_duration" baseline directly rather than
     * recomputing it a second, potentially divergent way, so this always
     * agrees with what the readiness score itself uses for the same figure.
     */
    private Double personalSleepNeedHours(Long accountId) {
        return baselineService.computeBaseline(accountId).baselineFor("sleep_duration").orElse(null);
    }

    private SleepSummary computeSummaryForDate(Long accountId, LocalDate date, Double personalNeedHours) {
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
                        t.toInstant().atZone(displayZone).toLocalTime().toString(),
                        STAGE_NAMES.get(stageIndex)
                ));
            }

            double deepHours = hoursByStage[0];
            double remHours = hoursByStage[1];
            double lightHours = hoursByStage[2];
            double awakeHours = hoursByStage[3];
            double asleepHours = deepHours + remHours + lightHours;
            double totalHours = asleepHours + awakeHours;

            int readingCount = rows.size();
            String confidence = computeConfidence(readingCount);
            int sleepScore = computeSleepScore(totalHours, deepHours, remHours, personalNeedHours);

            List<String> limitations = new ArrayList<>();
            limitations.add("Stage duration is approximated from reading density " +
                    "(15 min/reading) — not full polysomnography-grade session parsing.");
            if ("low".equals(confidence)) {
                limitations.add(String.format(
                        "Only %d reading(s) for this night (a full night is ~32 at 15-min " +
                        "intervals) — this is a partial/sparse sample, not a full session.",
                        readingCount));
            }
            if (personalNeedHours == null) {
                limitations.add("Sleep score uses a " + TARGET_SLEEP_HOURS + "h default target — " +
                        "not enough history yet for a personal need estimate.");
            }

            return new SleepSummary(
                    totalHours, asleepHours, deepHours, remHours, lightHours, awakeHours,
                    sleepScore, personalNeedHours, stages, confidence, limitations
            );
        } catch (Exception e) {
            log.warn("Failed to compute sleep summary for account {} on {}: {}",
                    accountId, date, e.getMessage(), e);
            return new SleepSummary(0, 0, 0, 0, 0, 0, 0, null, List.of(), "none",
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
     * Half weight on duration vs. the target, half weight on the proportion
     * of sleep spent in deep/REM (commonly considered the more restorative
     * stages in general sleep science, not a proprietary claim).
     *
     * @param personalNeedHours the account's real rolling-baseline need;
     *                          falls back to {@link #TARGET_SLEEP_HOURS} only
     *                          when null (not enough history yet) — never a
     *                          permanent flat target once real data exists.
     */
    private int computeSleepScore(double totalHours, double deepHours, double remHours, Double personalNeedHours) {
        if (totalHours <= 0) return 0;
        double target = personalNeedHours != null ? personalNeedHours : TARGET_SLEEP_HOURS;
        double durationRatio = Math.min(totalHours / target, 1.0);
        double restorativeRatio = (deepHours + remHours) / totalHours;
        double score = 100 * (0.5 * durationRatio + 0.5 * restorativeRatio);
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }
}
