package com.openwearableinsights.api.monthlyreport.application;

import com.openwearableinsights.api.monthlyreport.domain.MonthlyMetricSection;
import com.openwearableinsights.api.monthlyreport.domain.MonthlyMetricSection.TrendDirection;
import com.openwearableinsights.api.monthlyreport.domain.MonthlyPerformanceReport;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import com.openwearableinsights.api.sleep.application.SleepInsightService;
import com.openwearableinsights.api.sleep.domain.SleepTrendPoint;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes this app's own original monthly/longitudinal performance report
 * -- backs docs/product/parity-matrix.md row 30 ("Monthly/longitudinal
 * performance assessment").
 *
 * <p><b>What this deliberately is not:</b> competitor public docs describe a
 * structured monthly report, gated behind 28+ recovery scores, breaking down
 * strain/sleep/recovery over the period. This service does not reproduce
 * that report's design, layout, or scoring -- nobody outside that vendor
 * knows the exact methodology anyway. Instead, this composes real,
 * already-computed outputs from three other modules into a new, original,
 * fully-disclosed monthly summary, the same "compose real data into an
 * original report" approach {@code healthspan.application.HealthspanService}
 * already established for row 24.
 *
 * <p><b>Insufficient-history gate ({@value #MIN_RECOVERY_SCORES_REQUIRED}
 * recovery scores):</b> the source doc's acceptance criteria is "28+ days of
 * recovery scores" before the feature is available at all. Since this report
 * is inherently scoped to one calendar month (it breaks down strain/sleep/
 * recovery "over the period"), the most literal and most conservative
 * reading -- and the one this service implements -- is that the <em>
 * specific month being reported on</em> must itself have at least {@value
 * #MIN_RECOVERY_SCORES_REQUIRED} real persisted readiness scores (see {@link
 * ReadinessScoreHistoryRepository#findScoresByAccountId}), not merely 28
 * lifetime scores anywhere in history. A calendar month has at most 31 days,
 * so this requires near-complete daily coverage for that specific month --
 * deliberately strict, matching this app's "never a partial report" house
 * style (see {@link #computeReport(Long, YearMonth)} class Javadoc below).
 * An alternative reading -- 28 lifetime scores unlocking the feature, after
 * which every month (even a sparse one) gets a report -- was considered and
 * rejected: it would let a month with, say, 3 real days of data produce a
 * "monthly" strain/sleep/recovery breakdown that reads as far more complete
 * than it is, which is exactly the kind of partial-report-dressed-as-
 * complete this codebase's honesty discipline exists to avoid.
 *
 * <p><b>Three real, already-computed inputs, one per dimension:</b>
 * <ul>
 *   <li><b>Recovery</b> -- the account's own persisted readiness-score
 *   history ({@link ReadinessScoreHistoryRepository#findScoresByAccountId}),
 *   filtered to the reported month. This is also the exact data source the
 *   insufficient-history gate itself is computed from -- reused, not
 *   recomputed.</li>
 *   <li><b>Strain</b> -- real HR-zone training load ({@link
 *   TrainingLoadService#fetchDailyLoadHistory}), filtered to the reported
 *   month. Never recomputes the per-session load formula itself -- see
 *   {@code TrainingLoadService}'s own class Javadoc for that.</li>
 *   <li><b>Sleep</b> -- the real weekly/monthly-rollup-aware sleep trend
 *   ({@link SleepInsightService#computeTrends}), filtered to the reported
 *   month, reusing its {@code sleepScore} field. Never re-queries raw
 *   sleep_stage measurements itself.</li>
 * </ul>
 * All three underlying methods are windowed by "days back from today," not
 * by an arbitrary past calendar month, so each is called with a {@code days}
 * value wide enough to reach from the first day of the reported month to
 * today, and the result is filtered down to points whose date falls in that
 * month (see {@link #daysBackToCoverMonth}). For {@code
 * SleepInsightService#computeTrends}, this means a month more than ~31 days
 * in the past may come back as its own weekly-rollup points rather than
 * nightly ones (see that service's own rollup thresholds) -- filtering still
 * works (each point's date/bucket-start is checked against the target
 * month), but daily granularity is only guaranteed for the current
 * definition of "most recently complete month" (the default -- see {@link
 * #computeReport(Long)}). This trade-off is accepted rather than adding a
 * second, date-range-based query path to either service, and is disclosed
 * in the relevant section's own {@code limitations}.
 *
 * <p><b>Trend direction -- first half vs second half, never fabricated
 * significance:</b> each section's {@code trendDirection} compares the
 * average of the first half of the month's real data points against the
 * second half (see {@link #computeTrend}) -- the same honest,
 * no-statistical-significance-claimed convention already used by
 * {@code sleep_page.dart}'s {@code _TrendSummaryRow} on the Flutter side.
 * Requires at least {@value #MIN_POINTS_FOR_TREND} real data points in the
 * month (mirroring that same Flutter convention's threshold); below that,
 * {@code trendDirection} is {@code UNKNOWN} rather than a direction read
 * from too few points. The steady-vs-moving threshold is a percentage
 * change ({@value #TREND_STEADY_THRESHOLD_PCT}), not a fixed point
 * difference like the Flutter sleep-score convention it's modeled on --
 * this report's three dimensions have different units (0-100 score,
 * training-load arbitrary units, 0-100 sleep score), so a single fixed
 * point threshold wouldn't generalize; the percentage is this service's own
 * documented choice, not empirically tuned.
 *
 * <p><b>Confidence -- capped, never inflated:</b> each section reports its
 * own confidence from how much of the month it actually has real data for
 * (see {@link #buildSection}); the report's {@code overallConfidence} is the
 * weakest of the three sections' confidence, capped at "medium" -- this
 * composite never claims "high" confidence, the same discipline {@code
 * HealthspanService} already applies for row 24.
 *
 * <p><b>Auto-generated, on demand:</b> {@link #computeReport(Long)}/{@link
 * #computeReport(Long, YearMonth)} compute fresh on every call from data the
 * other three modules already persist -- no scheduled job, no stored
 * snapshot, following the same "compute fresh every request" convention
 * {@code TrainingLoadService}/{@code HealthspanService} already use.
 */
@Service
public class MonthlyPerformanceReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPerformanceReportService.class);

    /** Versioned, deterministic -- see class Javadoc for the full methodology. */
    public static final String ALGORITHM_VERSION = "monthlyreport-v1";

    // The "28 recovery scores" gate, scoped per reported month -- see class
    // Javadoc "Insufficient-history gate" for why.
    static final int MIN_RECOVERY_SCORES_REQUIRED = 28;

    // A dimension needs at least this many real data points within the
    // month before a trend direction is reported at all -- mirrors the
    // >=6-point threshold sleep_page.dart's _TrendSummaryRow already uses.
    private static final int MIN_POINTS_FOR_TREND = 6;

    // First-half-vs-second-half percentage-change threshold below which a
    // dimension is reported STEADY rather than IMPROVING/DECLINING -- see
    // class Javadoc "Trend direction" for why this is a percentage rather
    // than the fixed point-threshold the Flutter sleep trend uses.
    private static final double TREND_STEADY_THRESHOLD_PCT = 0.05;

    // Coverage (days with real data / days in month) at or above which a
    // section's own confidence is "medium" rather than "low" -- mirrors the
    // general "needs at least half the window with real data" shape other
    // confidence heuristics in this app use (e.g. SleepInsightService's
    // MIN_NIGHTS_FOR_CONSISTENCY-style thresholds).
    private static final double COVERAGE_FOR_MEDIUM_CONFIDENCE = 0.5;

    private static final List<String> CONFIDENCE_RANK = List.of("none", "low", "medium", "high");

    private static final String METHODOLOGY =
            "This app's own original monthly summary, not any vendor's proprietary report design: real "
                    + "readiness-score history, HR-zone training load, and sleep-score trend data already "
                    + "computed elsewhere in this app are filtered to the reported calendar month and "
                    + "averaged, with a first-half-vs-second-half-of-the-month trend direction for each "
                    + "dimension. Requires at least " + MIN_RECOVERY_SCORES_REQUIRED + " real persisted "
                    + "readiness scores within the reported month before any report is generated.";

    private static final List<String> BASE_LIMITATIONS = List.of(
            "Not a reproduction of any vendor's proprietary monthly-report design or scoring.",
            "Computed fresh from real data on every request -- there is no stored monthly snapshot, so a "
                    + "report for the same month can change if data for that month is imported or corrected later.",
            "This composite never claims \"high\" confidence -- see the overallConfidence field."
    );

    private final ReadinessScoreHistoryRepository readinessScoreHistoryRepository;
    private final TrainingLoadService trainingLoadService;
    private final SleepInsightService sleepInsightService;

    public MonthlyPerformanceReportService(
            ReadinessScoreHistoryRepository readinessScoreHistoryRepository,
            TrainingLoadService trainingLoadService,
            SleepInsightService sleepInsightService
    ) {
        this.readinessScoreHistoryRepository = readinessScoreHistoryRepository;
        this.trainingLoadService = trainingLoadService;
        this.sleepInsightService = sleepInsightService;
    }

    /**
     * The most recently complete calendar month's report -- "auto-generated"
     * per the acceptance criteria means computed on demand for a sensible
     * default month, not requiring the user to pick one. The current
     * (incomplete) month is deliberately never the default: a report titled
     * "this month" for a month still in progress would misleadingly look
     * like a finished period.
     */
    public MonthlyPerformanceReport computeReport(Long accountId) {
        return computeReport(accountId, YearMonth.now().minusMonths(1));
    }

    /** A specific calendar month's report. See class Javadoc for the full methodology. */
    public MonthlyPerformanceReport computeReport(Long accountId, YearMonth month) {
        List<DatedValue> recoveryPoints = fetchRecoveryPoints(accountId, month);
        int recoveryScoreCount = recoveryPoints.size();

        if (recoveryScoreCount < MIN_RECOVERY_SCORES_REQUIRED) {
            return insufficientHistory(month, recoveryScoreCount);
        }

        MonthlyMetricSection recovery = buildSection(
                "Recovery (readiness score)", recoveryPoints, "0-100 score", month, List.of());
        MonthlyMetricSection strain = buildStrainSection(accountId, month);
        MonthlyMetricSection sleep = buildSleepSection(accountId, month);

        String overallConfidence = capAtMedium(
                minConfidence(List.of(recovery.confidence(), strain.confidence(), sleep.confidence())));

        return new MonthlyPerformanceReport(
                month.toString(), ALGORITHM_VERSION, true,
                recoveryScoreCount, MIN_RECOVERY_SCORES_REQUIRED,
                strain, sleep, recovery,
                overallConfidence, METHODOLOGY, BASE_LIMITATIONS, null, Instant.now()
        );
    }

    // --- Insufficient-history state ---

    /** Honest "not enough history yet" state -- see class Javadoc "Insufficient-history gate". Never a partial report. */
    private MonthlyPerformanceReport insufficientHistory(YearMonth month, int recoveryScoreCount) {
        String message = String.format(
                "Not enough recovery (readiness) score history yet for %s -- found %d of the %d real daily "
                        + "scores needed for this month's report. Keep the app computing your daily readiness "
                        + "score and this report will become available once enough real history exists.",
                month, recoveryScoreCount, MIN_RECOVERY_SCORES_REQUIRED);
        List<String> limitations = new ArrayList<>(BASE_LIMITATIONS);
        limitations.add(message);
        return new MonthlyPerformanceReport(
                month.toString(), ALGORITHM_VERSION, false,
                recoveryScoreCount, MIN_RECOVERY_SCORES_REQUIRED,
                null, null, null,
                "none", METHODOLOGY, limitations, message, Instant.now()
        );
    }

    // --- Dimension: recovery (readiness score) ---

    private List<DatedValue> fetchRecoveryPoints(Long accountId, YearMonth month) {
        try {
            Map<LocalDate, Integer> allScores = readinessScoreHistoryRepository.findScoresByAccountId(accountId);
            return allScores.entrySet().stream()
                    .filter(e -> YearMonth.from(e.getKey()).equals(month))
                    .map(e -> new DatedValue(e.getKey(), e.getValue().doubleValue()))
                    .sorted((a, b) -> a.date().compareTo(b.date()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch readiness score history for account {} month {}: {}",
                    accountId, month, e.getMessage(), e);
            return List.of();
        }
    }

    // --- Dimension: strain (training load) ---

    private MonthlyMetricSection buildStrainSection(Long accountId, YearMonth month) {
        List<String> extraLimitations = List.of(
                "Training load is this app's own v1 HR-zone-weighted unit (" + TrainingLoadService.ALGORITHM_VERSION
                        + "), not a standardized physical unit -- comparable only to your own history, not to a "
                        + "population norm.",
                "For strain, IMPROVING/DECLINING describes the trend line's direction only (more or less "
                        + "training load) -- it is not a value judgment the way it is for sleep and recovery, "
                        + "where higher is favorable. See the Training Load view's own ACWR-based load status "
                        + "for whether your training balance itself is favorable."
        );

        List<TrainingLoadTrendPoint> history;
        try {
            history = trainingLoadService.fetchDailyLoadHistory(accountId, daysBackToCoverMonth(month));
        } catch (Exception e) {
            log.warn("Failed to fetch training load history for account {} month {}: {}",
                    accountId, month, e.getMessage(), e);
            history = List.of();
        }

        List<DatedValue> points = history.stream()
                .map(p -> new DatedValue(LocalDate.parse(p.date()), p.load()))
                .filter(p -> YearMonth.from(p.date()).equals(month))
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .toList();

        return buildSection("Strain (training load)", points, "training load (a.u.)", month, extraLimitations);
    }

    // --- Dimension: sleep ---

    private MonthlyMetricSection buildSleepSection(Long accountId, YearMonth month) {
        List<String> extraLimitations = List.of(
                "Sleep value is the average nightly sleep score (0-100, half duration vs your personal need, "
                        + "half restorative-stage proportion) already computed by the sleep trend view, not a "
                        + "separate monthly-specific formula.",
                "For a month more than about a month in the past, the underlying sleep trend may be returned "
                        + "as weekly-averaged points rather than nightly ones (see SleepInsightService's own "
                        + "rollup thresholds) -- this does not affect the default \"most recently complete "
                        + "month\" report."
        );

        List<SleepTrendPoint> trend;
        try {
            trend = sleepInsightService.computeTrends(accountId, daysBackToCoverMonth(month));
        } catch (Exception e) {
            log.warn("Failed to fetch sleep trend for account {} month {}: {}", accountId, month, e.getMessage(), e);
            trend = List.of();
        }

        List<DatedValue> points = trend.stream()
                .map(p -> new DatedValue(LocalDate.parse(p.date()), (double) p.sleepScore()))
                .filter(p -> YearMonth.from(p.date()).equals(month))
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .toList();

        return buildSection("Sleep (sleep score)", points, "0-100 score", month, extraLimitations);
    }

    // --- Shared section-building ---

    private record DatedValue(LocalDate date, double value) {}

    private record TrendResult(TrendDirection direction, String detail) {}

    /**
     * Builds one dimension's section from its own real, already-filtered
     * data points for the month. Never fabricates a value for a day with no
     * real data -- {@code averageValue} is the mean of only the days that
     * have real data, and {@code daysWithData} discloses how many that was
     * out of {@code daysInMonth}.
     */
    private MonthlyMetricSection buildSection(
            String name, List<DatedValue> pointsOldestFirst, String unit, YearMonth month, List<String> extraLimitations
    ) {
        int daysInMonth = month.lengthOfMonth();
        int daysWithData = pointsOldestFirst.size();
        List<String> limitations = new ArrayList<>(extraLimitations);

        if (pointsOldestFirst.isEmpty()) {
            limitations.add("No real data for this dimension was found in this month.");
            return new MonthlyMetricSection(
                    name, null, unit, 0, daysInMonth, TrendDirection.UNKNOWN,
                    "Not enough real data this month to read a trend.", "none", limitations);
        }

        double average = pointsOldestFirst.stream().mapToDouble(DatedValue::value).average().orElse(0);
        double coverage = (double) daysWithData / daysInMonth;
        String confidence = coverage >= COVERAGE_FOR_MEDIUM_CONFIDENCE ? "medium" : "low";
        if (daysWithData < daysInMonth) {
            limitations.add(String.format(
                    "Only %d of %d days in the month have real data for this dimension -- the average and "
                            + "trend are computed over those days only, never zero-filled for the rest.",
                    daysWithData, daysInMonth));
        }

        TrendResult trend = computeTrend(pointsOldestFirst);
        return new MonthlyMetricSection(
                name, average, unit, daysWithData, daysInMonth, trend.direction(), trend.detail(), confidence, limitations);
    }

    /**
     * First-half-vs-second-half-of-the-month trend direction -- see class
     * Javadoc "Trend direction" for the full reasoning behind the point
     * threshold and percentage-change convention.
     */
    private TrendResult computeTrend(List<DatedValue> pointsOldestFirst) {
        if (pointsOldestFirst.size() < MIN_POINTS_FOR_TREND) {
            return new TrendResult(TrendDirection.UNKNOWN, String.format(
                    "Not enough real data points this month (%d, need at least %d) to read a trend direction.",
                    pointsOldestFirst.size(), MIN_POINTS_FOR_TREND));
        }

        int mid = pointsOldestFirst.size() / 2;
        double firstHalfAvg = pointsOldestFirst.subList(0, mid).stream().mapToDouble(DatedValue::value).average().orElse(0);
        double secondHalfAvg = pointsOldestFirst.subList(mid, pointsOldestFirst.size()).stream()
                .mapToDouble(DatedValue::value).average().orElse(0);

        if (firstHalfAvg == 0) {
            return new TrendResult(TrendDirection.UNKNOWN,
                    "Not enough real data to compare the first half of the month against the second half.");
        }

        double pctChange = (secondHalfAvg - firstHalfAvg) / firstHalfAvg;
        TrendDirection direction = Math.abs(pctChange) < TREND_STEADY_THRESHOLD_PCT
                ? TrendDirection.STEADY
                : (pctChange > 0 ? TrendDirection.IMPROVING : TrendDirection.DECLINING);

        String detail = String.format(
                "First half of the month averaged %.1f, second half averaged %.1f (%s%.0f%%).",
                firstHalfAvg, secondHalfAvg, pctChange >= 0 ? "+" : "", pctChange * 100);
        return new TrendResult(direction, detail);
    }

    /**
     * How many trailing days from today the underlying "days back from now"
     * trend methods need to be asked for so their real returned data
     * definitely reaches back to the first day of {@code month} -- see class
     * Javadoc for why this filter-after-fetch approach is used instead of a
     * dedicated date-range query.
     */
    private int daysBackToCoverMonth(YearMonth month) {
        long days = ChronoUnit.DAYS.between(month.atDay(1), LocalDate.now());
        return (int) Math.max(1, days + 1);
    }

    private String minConfidence(List<String> confidences) {
        return confidences.stream()
                .min((a, b) -> Integer.compare(CONFIDENCE_RANK.indexOf(a), CONFIDENCE_RANK.indexOf(b)))
                .orElse("none");
    }

    private String capAtMedium(String confidence) {
        return CONFIDENCE_RANK.indexOf(confidence) > CONFIDENCE_RANK.indexOf("medium") ? "medium" : confidence;
    }
}
