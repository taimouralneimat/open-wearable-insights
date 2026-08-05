package com.openwearableinsights.api.healthspan.application;

import com.openwearableinsights.api.healthspan.domain.HealthspanFactor;
import com.openwearableinsights.api.healthspan.domain.HealthspanFactor.Direction;
import com.openwearableinsights.api.healthspan.domain.HealthspanScore;
import com.openwearableinsights.api.healthspan.domain.HealthspanSummary;
import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.sleep.application.SleepInsightService;
import com.openwearableinsights.api.sleep.domain.SleepConsistency;
import com.openwearableinsights.api.strength.application.StrengthTrainingService;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrend;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrendPoint;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadSummary;
import com.openwearableinsights.api.vo2max.application.Vo2MaxService;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxEstimate;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes this app's own original composite "healthspan" wellness score —
 * backs docs/product/parity-matrix.md row 24 ("Healthspan / biological age
 * score").
 *
 * <p><b>What this deliberately is not:</b> competitor public docs describe a
 * biological-age / "pace of aging" score whose methodology was developed
 * alongside an external research institute and is not publicly disclosed in
 * full. This service does not reproduce, approximate, or reverse-engineer
 * that formula — nobody outside that vendor knows it. Instead, this is a
 * new, original, fully-disclosed composite designed for this app, built
 * entirely from real metrics this codebase already computes elsewhere. It
 * makes no biological-age, actuarial, or medical claim — see {@link
 * #DISCLAIMER}, which is returned verbatim in every response.
 *
 * <p><b>Methodology ({@value #ALGORITHM_VERSION}) — "recent vs. your own
 * prior history," not a fixed external norm:</b> following this app's house
 * style ({@code readiness.application.ReadinessCalculator}: never a fixed
 * population norm when a personal baseline is available), every factor
 * compares the account's own recent period against its own preceding
 * period, rather than against an age/sex-normalized population reference
 * this app doesn't reliably have the profile data for (see {@code
 * Vo2MaxService}'s deferred age/sex banding, row 18). This is, in spirit,
 * the "pace of aging" framing the competitor's docs describe: not "is this
 * number good in absolute terms" but "is this trending favorably for you."
 * Each factor's percent change (favorable direction defined per-metric) is
 * scaled by {@value #PCT_CHANGE_SCALE} and clamped to &plusmn;40, then
 * weighted and summed onto a score centered at 50 (same clamp-and-sum shape
 * as {@code ReadinessCalculator}), never renormalizing weights when a
 * factor is missing — a missing factor simply contributes nothing, exactly
 * like readiness's own missing-factor handling.
 *
 * <p><b>Five inputs, five weights (this app's own documented judgment call,
 * not empirically tuned, not clinically or actuarially validated, and not
 * based on any external research partnership):</b>
 * <ul>
 *   <li><b>Cardiovascular fitness — VO2max trend, weight 0.30</b> (heaviest
 *   weight: cardiorespiratory fitness is widely discussed in general
 *   sports-science literature as one of the stronger predictors of
 *   healthy-aging outcomes). Reuses {@link Vo2MaxService#computeTrend}
 *   (monthly points, up to 12 trailing months) — never recomputes
 *   HRmax/HRrest itself. Recent-half vs. prior-half average within the
 *   window; higher VO2max is favorable.</li>
 *   <li><b>Resistance-training consistency — Strength Activity Time trend,
 *   weight 0.20</b> (regular resistance training is widely discussed as a
 *   sarcopenia/muscle-maintenance factor in healthy aging). Reuses {@link
 *   StrengthTrainingService#computeTrend}; more total minutes recently vs.
 *   prior is favorable.</li>
 *   <li><b>Resting-heart-rate trend, weight 0.20</b> (lower resting heart
 *   rate is a broadly-cited general cardiovascular-fitness signal). The
 *   "recent" figure reuses {@link BaselineService}'s existing rolling RHR
 *   baseline directly (the same figure the readiness score and VO2max
 *   already use) rather than a second convention; the "prior" figure is
 *   this service's own new query against the same {@code measurements}
 *   table (mirroring the pattern {@code Vo2MaxService#computeTrend}
 *   already uses for monthly RHR), deliberately excluding the trailing 28
 *   days baseline already covers so the two periods don't overlap. Lower
 *   recent RHR is favorable.</li>
 *   <li><b>Sleep consistency, weight 0.15.</b> Reuses {@link
 *   SleepInsightService#computeSleepConsistency} directly (already a 0-100
 *   score). That service exposes a single fixed 14-night window — this
 *   factor is therefore identical in both the 30-day and 6-month score,
 *   explicitly disclosed (see {@link #BASE_LIMITATIONS}) rather than
 *   silently implying a longer history was considered.</li>
 *   <li><b>Training-load balance, weight 0.15.</b> Reuses {@link
 *   TrainingLoadService#computeSummary}'s {@code loadStatus} (fixed 7d/28d
 *   ACWR window — same disclosed-fixed-window caveat as sleep consistency
 *   above). {@code "optimal"} is favorable, {@code "low"}/{@code
 *   "elevated"} mildly unfavorable, {@code "high"} (overtraining risk)
 *   unfavorable, {@code "unknown"} omitted.</li>
 * </ul>
 *
 * <p><b>Two windows, one methodology:</b> {@link #computeSummary} returns
 * both a 30-day and a 6-month score from the exact same formula above — only
 * the VO2max/Strength/RHR "recent vs. prior" comparison span differs (sleep
 * consistency and training-load balance are fixed-window inputs shared by
 * both, as documented above).
 *
 * <p><b>Data-retention question (docs/product/parity-matrix.md row 24's
 * "Legal/dependency constraint" column: "needs a long-term data-retention
 * design (6mo+ rolling windows)"):</b> verified, not assumed — grepped this
 * codebase for any scheduled deletion, TTL, or retention-policy job (e.g. a
 * TimescaleDB {@code add_retention_policy} on the {@code measurements}
 * hypertable, or a {@code @Scheduled} pruning job) and found none. {@code
 * measurements} is created as a hypertable ({@code V01__initial_schema.sql})
 * purely for TimescaleDB's time-series query performance, with no retention
 * policy attached. This app is single-user, local-first (ADR-0008), so
 * there is no multi-tenant storage-cost pressure driving one either. A
 * 6-month rolling window therefore works today against real historical data
 * with no new retention infrastructure — this was a real risk worth
 * checking, and checking it found nothing to build, which is itself the
 * honest documented finding, not an assumption.
 *
 * <p><b>Insufficient-data handling:</b> a window's score is {@code null}
 * (see {@link #emptyScore}) whenever fewer than {@value
 * #MIN_FACTORS_REQUIRED} of the five factors have enough real data — never
 * a partial or fabricated number from too few inputs.
 *
 * <p><b>Confidence — capped, never inflated:</b> this composite blends
 * several already-uncertain individual estimates (VO2max alone is capped at
 * "medium" — see {@code Vo2MaxService} class Javadoc), so it never claims
 * "high" confidence under any circumstances. It reports "medium" only when
 * at most one factor is missing <em>and</em> every present factor's own
 * confidence is itself at least "medium" (never higher than the weakest
 * contributing input); otherwise "low"; "none" when the score itself is
 * unavailable. See {@link #computeConfidence}.
 */
@Service
public class HealthspanService {

    private static final Logger log = LoggerFactory.getLogger(HealthspanService.class);

    /** Versioned, deterministic — see class Javadoc for the full formula. */
    public static final String ALGORITHM_VERSION = "healthspan-v1";

    /** Always present, verbatim, per docs/product/parity-matrix.md row 24's acceptance criteria. */
    public static final String DISCLAIMER =
            "This is a wellness estimate, not a medical or actuarial age - a composite of your own real "
                    + "fitness, sleep, and training data, computed with this app's own original methodology. It "
                    + "does not reproduce any vendor's proprietary biological-age formula and was not developed "
                    + "with or validated by any external research institute.";

    private static final String METHODOLOGY =
            "This app's own original composite, not any vendor's proprietary formula: each of five real "
                    + "metrics already computed elsewhere in this app (VO2max trend, resistance-training "
                    + "consistency, resting-heart-rate trend, sleep consistency, training-load balance) compares "
                    + "your own recent period against your own preceding period - \"is this trending favorably "
                    + "for you,\" not a comparison to any population norm or biological-age table. Each factor's "
                    + "percent change is converted to a signed score, weighted (VO2max 30%, resistance training "
                    + "20%, resting heart rate 20%, sleep consistency 15%, training-load balance 15% - this "
                    + "app's own documented judgment call, not empirically tuned or clinically validated), and "
                    + "summed onto a 0-100 scale centered at 50.";

    // Factor weights. Sum to 1.0. See class Javadoc for the reasoning behind
    // each choice.
    private static final double W_VO2MAX = 0.30;
    private static final double W_STRENGTH = 0.20;
    private static final double W_RHR = 0.20;
    private static final double W_SLEEP = 0.15;
    private static final double W_TRAININGLOAD = 0.15;

    // At least this many of the five factors must have real data for a
    // window's score to be reported at all — see class Javadoc
    // "Insufficient-data handling".
    private static final int MIN_FACTORS_REQUIRED = 3;

    // Converts a favorable fractional change (e.g. 0.10 = 10% improvement)
    // into a raw factor score, clamped to +/-40 — same clamp-and-scale shape
    // ReadinessCalculator uses for its own factors (e.g. HRV deviation *
    // 2.5, clamped to +/-40). A 20% favorable swing maxes out the clamp.
    private static final double PCT_CHANGE_SCALE = 200.0;
    private static final double FACTOR_SCORE_CLAMP = 40.0;

    // How many trailing calendar months (per Vo2MaxService#computeTrend)
    // count as "the window" for each composite window. 30-day uses 2 so a
    // recent-vs-prior split has at least a chance of two distinct months;
    // a true within-month comparison isn't possible from monthly granularity.
    private static final int VO2MAX_MONTHS_THIRTY_DAY = 2;
    private static final int VO2MAX_MONTHS_SIX_MONTH = 6;

    // RHR "prior" query window (days) and the trailing days already covered
    // by BaselineService's own rolling baseline, excluded here so the
    // "recent" and "prior" periods never overlap. See class Javadoc.
    private static final int RHR_PRIOR_WINDOW_DAYS_THIRTY_DAY = 30;
    private static final int RHR_PRIOR_WINDOW_DAYS_SIX_MONTH = 180;
    private static final int RHR_RECENT_EXCLUSION_DAYS = 28;
    private static final int MIN_RHR_SAMPLES_FOR_PRIOR = 5;

    // loadStatus -> raw factor score. "unknown" is intentionally absent
    // (omits the factor) — see class Javadoc.
    private static final Map<String, Double> LOAD_STATUS_SCORE = Map.of(
            "optimal", 30.0,
            "low", -10.0,
            "elevated", -10.0,
            "high", -30.0
    );

    private static final List<String> BASE_LIMITATIONS = List.of(
            "Not a medical or actuarial age estimate - a wellness estimate only, see the disclaimer field.",
            "Weights (VO2max 30%, resistance training 20%, resting heart rate 20%, sleep consistency 15%, "
                    + "training-load balance 15%) are this app's own documented judgment call, not empirically "
                    + "tuned, and not clinically or actuarially validated.",
            "Sleep consistency and training-load balance always reflect their own fixed windows (the trailing "
                    + "14 nights and the trailing 7d/28d ACWR, respectively) regardless of whether you're viewing "
                    + "the 30-day or 6-month score - the underlying services don't expose a longer historical "
                    + "window yet.",
            "Composes several already-uncertain individual estimates (e.g. the VO2max estimate itself is "
                    + "capped at medium confidence) - this composite never claims high confidence."
    );

    public enum Window { THIRTY_DAY, SIX_MONTH }

    private final JdbcTemplate jdbcTemplate;
    private final Vo2MaxService vo2MaxService;
    private final StrengthTrainingService strengthTrainingService;
    private final SleepInsightService sleepInsightService;
    private final TrainingLoadService trainingLoadService;
    private final BaselineService baselineService;

    public HealthspanService(
            JdbcTemplate jdbcTemplate,
            Vo2MaxService vo2MaxService,
            StrengthTrainingService strengthTrainingService,
            SleepInsightService sleepInsightService,
            TrainingLoadService trainingLoadService,
            BaselineService baselineService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.vo2MaxService = vo2MaxService;
        this.strengthTrainingService = strengthTrainingService;
        this.sleepInsightService = sleepInsightService;
        this.trainingLoadService = trainingLoadService;
        this.baselineService = baselineService;
    }

    /** Both windows the acceptance criteria calls for — see class Javadoc "Two windows, one methodology". */
    public HealthspanSummary computeSummary(Long accountId) {
        return new HealthspanSummary(
                computeScore(accountId, Window.THIRTY_DAY),
                computeScore(accountId, Window.SIX_MONTH)
        );
    }

    /** A single window's composite score. Public so tests (and {@link #computeSummary}) can target one window directly. */
    public HealthspanScore computeScore(Long accountId, Window window) {
        List<HealthspanFactor> factors = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        computeVo2MaxFactor(accountId, window).ifPresentOrElse(factors::add,
                () -> missing.add("Cardiovascular fitness (VO2max trend)"));
        computeStrengthFactor(accountId, window).ifPresentOrElse(factors::add,
                () -> missing.add("Resistance-training consistency"));
        computeRhrFactor(accountId, window).ifPresentOrElse(factors::add,
                () -> missing.add("Resting-heart-rate trend"));
        computeSleepFactor(accountId).ifPresentOrElse(factors::add,
                () -> missing.add("Sleep consistency"));
        computeTrainingLoadFactor(accountId).ifPresentOrElse(factors::add,
                () -> missing.add("Training-load balance"));

        if (factors.size() < MIN_FACTORS_REQUIRED) {
            return emptyScore(window, missing);
        }

        double weightedSum = factors.stream().mapToDouble(HealthspanFactor::contribution).sum();
        int score = (int) Math.round(clamp(50 + weightedSum, 0, 100));

        String missingTreatment = missing.isEmpty()
                ? "All factors present."
                : "Excluded from composite: " + String.join(", ", missing) + ".";

        return new HealthspanScore(
                score,
                windowLabel(window),
                ALGORITHM_VERSION,
                computeConfidence(factors, missing.size()),
                factors,
                missingTreatment,
                METHODOLOGY,
                DISCLAIMER,
                BASE_LIMITATIONS,
                Instant.now()
        );
    }

    // --- Factor: cardiovascular fitness (VO2max trend) ---

    private Optional<HealthspanFactor> computeVo2MaxFactor(Long accountId, Window window) {
        List<Vo2MaxTrendPoint> trend;
        try {
            trend = vo2MaxService.computeTrend(accountId);
        } catch (Exception e) {
            log.warn("Failed to fetch VO2max trend for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
        if (trend.isEmpty()) {
            return Optional.empty();
        }

        int monthsBack = window == Window.SIX_MONTH ? VO2MAX_MONTHS_SIX_MONTH : VO2MAX_MONTHS_THIRTY_DAY;
        YearMonth now = YearMonth.now();
        List<Double> valuesOldestFirst = trend.stream()
                .filter(p -> ChronoUnit.MONTHS.between(YearMonth.parse(p.month()), now) < monthsBack)
                .sorted((a, b) -> a.month().compareTo(b.month()))
                .map(Vo2MaxTrendPoint::vo2Max)
                .toList();

        Optional<TrendComparison> comparison = splitRecentVsPrior(valuesOldestFirst);
        if (comparison.isEmpty()) {
            return Optional.empty();
        }
        TrendComparison c = comparison.get();

        // Higher VO2max is favorable.
        double rawScore = clamp(c.pctChange() * PCT_CHANGE_SCALE, -FACTOR_SCORE_CLAMP, FACTOR_SCORE_CLAMP);
        double contribution = W_VO2MAX * rawScore;
        Direction direction = directionFor(rawScore);

        String confidence = vo2MaxConfidence(accountId);
        String comparisonText = String.format(
                "%.1f mL/kg/min recently vs %.1f mL/kg/min in the preceding period (%s)",
                c.recentAvg(), c.priorAvg(), pctLabel(c.pctChange()));

        return Optional.of(new HealthspanFactor(
                "Cardiovascular fitness (VO2max trend)", c.recentAvg(), "mL/kg/min", comparisonText,
                direction, W_VO2MAX, contribution, confidence, "vo2max"));
    }

    private String vo2MaxConfidence(Long accountId) {
        try {
            Vo2MaxEstimate estimate = vo2MaxService.computeEstimate(accountId);
            return estimate.confidence();
        } catch (Exception e) {
            return "low";
        }
    }

    // --- Factor: resistance-training consistency (Strength Activity Time trend) ---

    private Optional<HealthspanFactor> computeStrengthFactor(Long accountId, Window window) {
        StrengthTrainingService.Window stWindow = window == Window.SIX_MONTH
                ? StrengthTrainingService.Window.SIXMONTH
                : StrengthTrainingService.Window.MONTHLY;

        StrengthActivityTrend trend;
        try {
            trend = strengthTrainingService.computeTrend(accountId, stWindow);
        } catch (Exception e) {
            log.warn("Failed to fetch strength trend for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }

        List<Double> minutesOldestFirst = trend.points().stream()
                .map(StrengthActivityTrendPoint::totalMinutes)
                .toList();

        Optional<TrendComparison> comparison = splitRecentVsPrior(minutesOldestFirst);
        if (comparison.isEmpty()) {
            return Optional.empty();
        }
        TrendComparison c = comparison.get();

        // More total strength-training minutes recently is favorable.
        double rawScore = clamp(c.pctChange() * PCT_CHANGE_SCALE, -FACTOR_SCORE_CLAMP, FACTOR_SCORE_CLAMP);
        double contribution = W_STRENGTH * rawScore;
        Direction direction = directionFor(rawScore);

        // No confidence field on StrengthActivityTrend — this composite's
        // own coverage heuristic: enough distinct periods with real data to
        // trust a recent-vs-prior split.
        int minPeriodsForMedium = stWindow == StrengthTrainingService.Window.SIXMONTH ? 4 : 3;
        String confidence = trend.points().size() >= minPeriodsForMedium ? "medium" : "low";

        String comparisonText = String.format(
                "%.0f strength-training min/period recently vs %.0f min/period in the preceding period (%s)",
                c.recentAvg(), c.priorAvg(), pctLabel(c.pctChange()));

        return Optional.of(new HealthspanFactor(
                "Resistance-training consistency", c.recentAvg(), "min/period", comparisonText,
                direction, W_STRENGTH, contribution, confidence, "strength"));
    }

    // --- Factor: resting-heart-rate trend ---

    private Optional<HealthspanFactor> computeRhrFactor(Long accountId, Window window) {
        PersonalBaseline baseline;
        try {
            baseline = baselineService.computeBaseline(accountId);
        } catch (Exception e) {
            log.warn("Failed to fetch RHR baseline for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
        Optional<Double> recentRhrOpt = baseline.baselineFor("rhr");
        if (recentRhrOpt.isEmpty()) {
            return Optional.empty();
        }
        double recentRhr = recentRhrOpt.get();

        int priorWindowDays = window == Window.SIX_MONTH
                ? RHR_PRIOR_WINDOW_DAYS_SIX_MONTH
                : RHR_PRIOR_WINDOW_DAYS_THIRTY_DAY;
        Instant priorStart = Instant.now().minus(priorWindowDays, ChronoUnit.DAYS);
        Instant priorEnd = Instant.now().minus(RHR_RECENT_EXCLUSION_DAYS, ChronoUnit.DAYS);
        if (!priorStart.isBefore(priorEnd)) {
            return Optional.empty();
        }

        Optional<AvgWithCount> prior = fetchAvgRhr(accountId, priorStart, priorEnd);
        if (prior.isEmpty()) {
            return Optional.empty();
        }
        double priorRhr = prior.get().avg();

        // Lower recent RHR is favorable, so this is the mirror image of the
        // "higher is favorable" factors above.
        double pctChange = priorRhr == 0 ? 0 : (recentRhr - priorRhr) / priorRhr;
        double favorablePctChange = -pctChange;
        double rawScore = clamp(favorablePctChange * PCT_CHANGE_SCALE, -FACTOR_SCORE_CLAMP, FACTOR_SCORE_CLAMP);
        double contribution = W_RHR * rawScore;
        Direction direction = directionFor(rawScore);

        boolean enoughSamples = prior.get().count() >= MIN_RHR_SAMPLES_FOR_PRIOR;
        String confidence = enoughSamples && !baseline.isProvisional() ? "medium" : "low";

        String comparisonText = String.format(
                "%.0f bpm recently (%s baseline) vs %.0f bpm in the preceding period (%s)",
                recentRhr, baseline.windowDescription(), priorRhr, pctLabel(favorablePctChange));

        return Optional.of(new HealthspanFactor(
                "Resting-heart-rate trend", recentRhr, "bpm", comparisonText,
                direction, W_RHR, contribution, confidence, "readiness_baseline+measurements"));
    }

    private record AvgWithCount(double avg, int count) {}

    /**
     * This service's own query for the account's average RHR over a past
     * window — mirrors the pattern {@code Vo2MaxService#computeTrend}
     * already uses for monthly RHR, rather than reaching into {@link
     * BaselineService}'s internals for a historical (not just current)
     * baseline it doesn't expose.
     */
    private Optional<AvgWithCount> fetchAvgRhr(Long accountId, Instant since, Instant until) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT AVG(value) AS avg_value, COUNT(*) AS sample_count FROM measurements " +
                            "WHERE account_id = ? AND metric_type = 'rhr' AND time >= ? AND time < ?",
                    accountId, Timestamp.from(since), Timestamp.from(until)
            );
            if (rows.isEmpty() || rows.get(0).get("avg_value") == null) {
                return Optional.empty();
            }
            double avg = ((Number) rows.get(0).get("avg_value")).doubleValue();
            int count = ((Number) rows.get(0).get("sample_count")).intValue();
            if (count < MIN_RHR_SAMPLES_FOR_PRIOR) {
                return Optional.empty();
            }
            return Optional.of(new AvgWithCount(avg, count));
        } catch (Exception e) {
            log.warn("Failed to fetch prior-period RHR for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // --- Factor: sleep consistency ---

    private Optional<HealthspanFactor> computeSleepFactor(Long accountId) {
        SleepConsistency consistency;
        try {
            consistency = sleepInsightService.computeSleepConsistency(accountId);
        } catch (Exception e) {
            log.warn("Failed to fetch sleep consistency for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
        if (consistency.consistencyScore() == null) {
            return Optional.empty();
        }

        // Same linear "distance from a neutral midpoint" shape
        // ReadinessCalculator uses for its own 0-1 "Data completeness"
        // factor, scaled for a 0-100 input instead.
        double rawScore = clamp((consistency.consistencyScore() - 50) * 0.8, -FACTOR_SCORE_CLAMP, FACTOR_SCORE_CLAMP);
        double contribution = W_SLEEP * rawScore;
        Direction direction = directionFor(rawScore);

        String comparisonText = String.format(
                "Sleep consistency %d/100 over the trailing 14 nights (fixed window - see limitations)",
                consistency.consistencyScore());

        return Optional.of(new HealthspanFactor(
                "Sleep consistency", consistency.consistencyScore().doubleValue(), "0-100 score", comparisonText,
                direction, W_SLEEP, contribution, consistency.confidence(), "sleep"));
    }

    // --- Factor: training-load balance ---

    private Optional<HealthspanFactor> computeTrainingLoadFactor(Long accountId) {
        TrainingLoadSummary summary;
        try {
            summary = trainingLoadService.computeSummary(accountId);
        } catch (Exception e) {
            log.warn("Failed to fetch training-load summary for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
        Double rawScore = LOAD_STATUS_SCORE.get(summary.loadStatus());
        if (rawScore == null) {
            return Optional.empty();
        }
        double contribution = W_TRAININGLOAD * rawScore;
        Direction direction = directionFor(rawScore);

        String comparisonText = String.format(
                "Training-load balance: %s (ACWR %s, fixed 7d/28d window - see limitations)",
                summary.loadStatus(), summary.acwr() != null ? String.format("%.2f", summary.acwr()) : "n/a");

        return Optional.of(new HealthspanFactor(
                "Training-load balance", summary.acwr(), "ACWR ratio", comparisonText,
                direction, W_TRAININGLOAD, contribution, summary.confidence(), "trainingload"));
    }

    // --- Shared helpers ---

    private record TrendComparison(double recentAvg, double priorAvg, double pctChange) {}

    /**
     * Splits an oldest-first list of real values into a "prior" first half
     * and a "recent" second half and compares their averages. Requires at
     * least 2 values (1 prior + 1 recent) — fewer means no real trend can
     * be read, so this returns empty rather than a fabricated comparison.
     */
    private Optional<TrendComparison> splitRecentVsPrior(List<Double> valuesOldestFirst) {
        if (valuesOldestFirst.size() < 2) {
            return Optional.empty();
        }
        int splitIndex = Math.max(1, valuesOldestFirst.size() / 2);
        List<Double> prior = valuesOldestFirst.subList(0, splitIndex);
        List<Double> recent = valuesOldestFirst.subList(splitIndex, valuesOldestFirst.size());
        if (prior.isEmpty() || recent.isEmpty()) {
            return Optional.empty();
        }
        double priorAvg = prior.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double recentAvg = recent.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (priorAvg == 0) {
            return Optional.empty();
        }
        double pctChange = (recentAvg - priorAvg) / priorAvg;
        return Optional.of(new TrendComparison(recentAvg, priorAvg, pctChange));
    }

    private Direction directionFor(double rawScore) {
        if (rawScore > 0.5) return Direction.FAVORABLE;
        if (rawScore < -0.5) return Direction.UNFAVORABLE;
        return Direction.NEUTRAL;
    }

    private String pctLabel(double favorablePctChange) {
        if (favorablePctChange > 0.01) {
            return String.format("trending favorably, +%.0f%%", favorablePctChange * 100);
        }
        if (favorablePctChange < -0.01) {
            return String.format("trending unfavorably, %.0f%%", favorablePctChange * 100);
        }
        return "roughly steady";
    }

    /**
     * Honest "not enough history yet" state — see class Javadoc
     * "Insufficient-data handling". Never a partial or fabricated score.
     */
    private HealthspanScore emptyScore(Window window, List<String> missing) {
        List<String> limitations = new ArrayList<>(BASE_LIMITATIONS);
        limitations.add("Not enough real history yet across enough of the contributing metrics ("
                + String.join(", ", missing) + ") for a " + windowDisplayName(window)
                + " healthspan estimate - needs at least " + MIN_FACTORS_REQUIRED + " of 5 factors with real data.");
        return new HealthspanScore(
                null,
                windowLabel(window),
                ALGORITHM_VERSION,
                "none",
                List.of(),
                "Excluded from composite: " + String.join(", ", missing) + ".",
                METHODOLOGY,
                DISCLAIMER,
                limitations,
                Instant.now()
        );
    }

    /**
     * Composite confidence: never "high" (see class Javadoc "Confidence"),
     * "medium" only when at most one factor is missing and every present
     * factor's own confidence is itself at least "medium" — the cap is
     * mechanical, derived from the actual weakest contributing input, never
     * inflated above it.
     */
    private String computeConfidence(List<HealthspanFactor> factors, int missingCount) {
        if (missingCount >= 2) {
            return "low";
        }
        boolean anyWeak = factors.stream().anyMatch(f ->
                f.confidence() == null || "low".equals(f.confidence()) || "none".equals(f.confidence()));
        return anyWeak ? "low" : "medium";
    }

    private String windowLabel(Window window) {
        return window == Window.SIX_MONTH ? "6month" : "30day";
    }

    private String windowDisplayName(Window window) {
        return window == Window.SIX_MONTH ? "6-month" : "30-day";
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
