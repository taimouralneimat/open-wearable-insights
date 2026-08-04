package com.openwearableinsights.api.vo2max.application;

import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxEstimate;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Estimates VO2max (maximal oxygen uptake, mL/kg/min) — a standard
 * cardiovascular-fitness metric — from the account's own real heart-rate
 * data. Backs docs/product/parity-matrix.md row 18.
 *
 * <p><b>Formula ({@value #ALGORITHM_VERSION}):</b> the Uth-Sørensen-Overgaard-Pedersen
 * non-exercise regression (Uth N, Sørensen H, Overgaard K, Pedersen PK,
 * "Estimation of VO2max from the ratio between HRmax and HRrest — the Heart
 * Rate Ratio Method," European Journal of Applied Physiology, 2004):
 *
 * <pre>
 *   VO2max (mL/kg/min) &asymp; 15.3 &times; (HRmax / HRrest)
 * </pre>
 *
 * This is a published, peer-reviewed, non-proprietary formula — not
 * anything invented for this app or attributed to any wearable vendor. The
 * original validation reports a margin of error on the order of &plusmn;10-15%
 * against lab-measured (gas-analysis) VO2max, which is why this is
 * surfaced everywhere as an <b>estimate</b>, never a lab-equivalent
 * measurement or medical assessment (see {@link #baseLimitations()}).
 *
 * <p><b>HRmax source:</b> the highest real {@code max_heart_rate} recorded
 * across the account's own FIT-parsed activity sessions (see the {@code
 * activities} module) within the last {@value #HR_MAX_WINDOW_DAYS} days.
 * Two real, already-populated sources exist in this app for a "highest
 * heart rate observed" figure: the per-session {@code activities.max_heart_rate}
 * column (one pre-aggregated value per real workout) and the continuous
 * per-reading {@code measurements} rows with {@code metric_type = 'hr'}
 * (written by the same FIT-import pipeline — see {@code
 * connections.adapter.garmin.GarminFitConnector}). We use the former: it is
 * the same underlying signal (both are written from the same FIT session
 * data), but querying one pre-aggregated row per session is far cheaper than
 * scanning the much larger {@code measurements} hypertable for the same
 * answer, and {@code activities.max_heart_rate} is already the value this
 * app surfaces elsewhere (e.g. the activity session detail view) as "the"
 * max heart rate for a session — reusing it keeps this consistent with what
 * the user already sees. Garmin Connect's own daily wellness sync (a
 * separate, simpler sync path — see the {@code garminconnect} module) does
 * not populate either source, only daily aggregates like resting heart
 * rate; this estimate is honestly unavailable until at least one real
 * activity file has been imported with heart-rate data, exactly like the
 * training-load feature's dependency on the same table (see {@code
 * trainingload.application.TrainingLoadService}).
 *
 * <p><b>Window choice (180 days):</b> unlike the rolling baselines in
 * {@link BaselineService} (28 days, chosen because RHR/HRV/sleep genuinely
 * drift week to week), a true physiological HRmax ceiling changes slowly.
 * A short window would frequently miss the account's real highest effort
 * and understate HRmax (and therefore VO2max); a 180-day window is a
 * deliberate, documented judgment call that trades a small amount of
 * "freshness" (an old max eventually goes stale as fitness/age change) for
 * a much better chance of actually capturing a genuine hard effort, which
 * for most people doesn't happen every week. This is disclosed in {@link
 * #buildLimitations} when the contributing activity is itself old.
 *
 * <p><b>HRrest source:</b> reuses {@link BaselineService}'s existing "rhr"
 * personal baseline directly — the exact same figure already used by the
 * readiness score and sleep debt — rather than computing a second,
 * potentially divergent resting-heart-rate convention.
 *
 * <p><b>On-demand computation, no stored snapshot:</b> like {@code
 * trainingload.application.TrainingLoadService#computeSummary} and {@code
 * sleep.application.SleepInsightService}, this computes fresh on every
 * request rather than persisting a snapshot that a background job would
 * need to keep in sync. Tradeoff: recomputation cost (two cheap indexed
 * queries plus the existing baseline computation) is trivial for a
 * single-account local app, so the simplicity of "always current, nothing
 * to go stale" wins over the complexity of a scheduled recompute job and a
 * new table to keep consistent with {@code activities}/{@code
 * measurements} as they change.
 *
 * <p><b>Confidence:</b> capped at "medium" — never "high" — regardless of
 * how much data feeds it. This is a single-source-formula estimate with a
 * documented double-digit-percent margin of error even under ideal
 * conditions (see {@link #baseLimitations}), so "high" confidence would
 * overstate what this number can honestly claim.
 */
@Service
public class Vo2MaxService {

    private static final Logger log = LoggerFactory.getLogger(Vo2MaxService.class);

    /** Versioned, deterministic — see class Javadoc for the exact formula and data sources. */
    public static final String ALGORITHM_VERSION = "vo2max-v1";

    /** The Heart Rate Ratio Method's published coefficient. See class Javadoc. */
    private static final double HRR_COEFFICIENT = 15.3;

    // See class Javadoc "Window choice" for why this differs from
    // BaselineService's much shorter rolling windows.
    private static final int HR_MAX_WINDOW_DAYS = 180;

    // How many trailing months computeTrend looks back.
    private static final int TREND_MONTHS = 12;

    // A contributing activity older than this gets an explicit staleness
    // caveat in limitations (still within the 180-day window, but old
    // enough that "this might not be your current ceiling" is worth saying).
    private static final int STALE_HR_MAX_DAYS = 90;

    // Physiological plausibility bounds (documented judgment call, same
    // pattern as BaselineService's MIN/MAX_PLAUSIBLE_SLEEP_HOURS guard):
    // values outside these ranges are far more likely to be bad/corrupted
    // data (device glitch, unit mixup, etc.) than a real reading, so they're
    // treated as unavailable rather than fed into the formula.
    private static final int MIN_PLAUSIBLE_HR_MAX_BPM = 100;
    private static final int MAX_PLAUSIBLE_HR_MAX_BPM = 230;
    private static final int MIN_PLAUSIBLE_HR_REST_BPM = 30;
    private static final int MAX_PLAUSIBLE_HR_REST_BPM = 120;
    // HRmax must exceed HRrest by at least this much to be physiologically
    // sane — a small or negative gap means the two readings can't both be
    // trustworthy (resting heart rate cannot approach maximal heart rate).
    private static final int MIN_HR_MAX_OVER_REST_GAP_BPM = 20;

    private static final String METHODOLOGY =
            "Uth-Sorensen-Overgaard-Pedersen non-exercise regression (Uth N, Sorensen H, "
                    + "Overgaard K, Pedersen PK, \"Estimation of VO2max from the ratio between "
                    + "HRmax and HRrest - the Heart Rate Ratio Method,\" European Journal of "
                    + "Applied Physiology, 2004): VO2max (mL/kg/min) is approximately 15.3 x "
                    + "(HRmax / HRrest).";

    private final JdbcTemplate jdbcTemplate;
    private final BaselineService baselineService;

    public Vo2MaxService(JdbcTemplate jdbcTemplate, BaselineService baselineService) {
        this.jdbcTemplate = jdbcTemplate;
        this.baselineService = baselineService;
    }

    /**
     * Current VO2max estimate for the given account. Honest empty state
     * (all-null fields, confidence "none") when there isn't enough real
     * data yet — see class Javadoc for exactly which two real data sources
     * are required.
     */
    public Vo2MaxEstimate computeEstimate(Long accountId) {
        Optional<HrMaxReading> hrMax = findHrMax(accountId, Instant.now().minus(HR_MAX_WINDOW_DAYS, ChronoUnit.DAYS));
        PersonalBaseline baseline = baselineService.computeBaseline(accountId);
        Optional<Double> hrRest = baseline.baselineFor("rhr");

        if (hrMax.isEmpty() || hrRest.isEmpty()) {
            return emptyEstimate(hrMax.isPresent(), hrRest.isPresent());
        }

        int hrMaxBpm = hrMax.get().hrMaxBpm();
        double hrRestBpm = hrRest.get();

        if (!isPlausible(hrMaxBpm, hrRestBpm)) {
            log.warn("Implausible HRmax/HRrest for account {} (hrMax={}, hrRest={}) — " +
                    "omitting VO2max estimate rather than computing from suspect data.",
                    accountId, hrMaxBpm, hrRestBpm);
            List<String> limitations = new ArrayList<>(baseLimitations());
            limitations.add("Your recorded max heart rate (" + hrMaxBpm + " bpm) and resting "
                    + "heart rate baseline (" + Math.round(hrRestBpm) + " bpm) don't fall within "
                    + "a physiologically plausible range together, so no estimate is shown rather "
                    + "than computing one from data that's likely inaccurate.");
            return new Vo2MaxEstimate(null, hrMaxBpm, hrRestBpm, hrMax.get().source(),
                    hrRestSourceDescription(baseline), ALGORITHM_VERSION, "none", METHODOLOGY, limitations, Instant.now());
        }

        double vo2Max = HRR_COEFFICIENT * (hrMaxBpm / hrRestBpm);

        String confidence = computeConfidence(baseline.confidence(), hrMax.get().recordedAt());
        List<String> limitations = buildLimitations(baseline, hrMax.get().recordedAt());

        return new Vo2MaxEstimate(
                roundToOneDecimal(vo2Max),
                hrMaxBpm,
                hrRestBpm,
                hrMax.get().source(),
                hrRestSourceDescription(baseline),
                ALGORITHM_VERSION,
                confidence,
                METHODOLOGY,
                limitations,
                Instant.now()
        );
    }

    /**
     * Per-calendar-month VO2max trend for the trailing {@value #TREND_MONTHS}
     * months. Unlike {@link #computeEstimate}, which uses a {@value
     * #HR_MAX_WINDOW_DAYS}-day trailing window and the current smoothed RHR
     * baseline, each trend point is computed independently from that
     * specific month's own data: that month's highest recorded activity
     * heart rate, and that month's own straight average resting heart rate
     * (not the smoothed rolling baseline, which {@link BaselineService}
     * only exposes as-of-now, not as it looked historically). This is a
     * simpler, self-consistent per-month definition, deliberately not
     * reusing overlapping trailing windows across points (which would make
     * neighboring points highly correlated and a poor "trend" signal).
     * Months without both a qualifying activity and resting-heart-rate data
     * are omitted — never zero-filled or interpolated.
     */
    public List<Vo2MaxTrendPoint> computeTrend(Long accountId) {
        Instant since = Instant.now().minus((long) TREND_MONTHS * 31, ChronoUnit.DAYS);

        Map<YearMonth, Integer> hrMaxByMonth = fetchMonthlyHrMax(accountId, since);
        Map<YearMonth, Double> hrRestByMonth = fetchMonthlyAvgRhr(accountId, since);

        List<Vo2MaxTrendPoint> points = new ArrayList<>();
        for (Map.Entry<YearMonth, Integer> entry : hrMaxByMonth.entrySet()) {
            YearMonth month = entry.getKey();
            Integer hrMaxBpm = entry.getValue();
            Double hrRestBpm = hrRestByMonth.get(month);
            if (hrMaxBpm == null || hrRestBpm == null) {
                continue;
            }
            if (!isPlausible(hrMaxBpm, hrRestBpm)) {
                continue;
            }
            double vo2Max = HRR_COEFFICIENT * (hrMaxBpm / hrRestBpm);
            points.add(new Vo2MaxTrendPoint(month.format(DateTimeFormatter.ofPattern("yyyy-MM")), roundToOneDecimal(vo2Max)));
        }
        points.sort((a, b) -> a.month().compareTo(b.month()));
        return points;
    }

    private Vo2MaxEstimate emptyEstimate(boolean hasHrMax, boolean hasHrRest) {
        List<String> limitations = new ArrayList<>(baseLimitations());
        if (!hasHrMax) {
            limitations.add("No real activity session with a recorded heart rate in the last "
                    + HR_MAX_WINDOW_DAYS + " days yet — import an activity with heart-rate data "
                    + "(e.g. a run or ride) to unlock this estimate.");
        }
        if (!hasHrRest) {
            limitations.add("Not enough resting-heart-rate history yet for a personal baseline — "
                    + "keep the app importing daily data and this will populate.");
        }
        return new Vo2MaxEstimate(null, null, null, null, null, ALGORITHM_VERSION, "none", METHODOLOGY, limitations, Instant.now());
    }

    private String computeConfidence(String baselineConfidence, Instant hrMaxRecordedAt) {
        if ("low".equals(baselineConfidence)) {
            return "low";
        }
        long ageDays = ChronoUnit.DAYS.between(hrMaxRecordedAt, Instant.now());
        if (ageDays > STALE_HR_MAX_DAYS) {
            return "low";
        }
        // baselineConfidence is "medium" or "high" here; capped at "medium"
        // regardless — see class Javadoc "Confidence".
        return "medium";
    }

    private List<String> buildLimitations(PersonalBaseline baseline, Instant hrMaxRecordedAt) {
        List<String> limitations = new ArrayList<>(baseLimitations());
        long ageDays = ChronoUnit.DAYS.between(hrMaxRecordedAt, Instant.now());
        if (ageDays > STALE_HR_MAX_DAYS) {
            limitations.add("Your highest recorded heart rate is " + ageDays + " days old — a "
                    + "more recent hard effort could reveal a higher true max heart rate, which "
                    + "would raise this estimate.");
        }
        if (baseline.isProvisional()) {
            limitations.add("Resting-heart-rate baseline is still provisional (" + baseline.windowDescription() + ").");
        }
        return limitations;
    }

    private List<String> baseLimitations() {
        return new ArrayList<>(List.of(
                "This is a non-exercise regression estimate with a documented margin of error "
                        + "of roughly 10-15% in the original validation study - it is not a "
                        + "laboratory-measured VO2max (e.g. from a supervised graded exercise "
                        + "test with gas analysis) and is not a medical or clinical assessment.",
                "Max heart rate is the highest value recorded during real logged activities, "
                        + "not a measured true maximum from a supervised maximal-effort test - if "
                        + "you haven't had a very hard, near-maximal-effort workout recently, this "
                        + "will tend to underestimate both your true max heart rate and this "
                        + "VO2max estimate.",
                "Uses a single fixed coefficient (15.3) validated on a general adult population - "
                        + "it is not personalized for your age, sex, or training status beyond "
                        + "your own two measured heart-rate inputs."
        ));
    }

    private boolean isPlausible(int hrMaxBpm, double hrRestBpm) {
        if (hrMaxBpm < MIN_PLAUSIBLE_HR_MAX_BPM || hrMaxBpm > MAX_PLAUSIBLE_HR_MAX_BPM) {
            return false;
        }
        if (hrRestBpm < MIN_PLAUSIBLE_HR_REST_BPM || hrRestBpm > MAX_PLAUSIBLE_HR_REST_BPM) {
            return false;
        }
        return (hrMaxBpm - hrRestBpm) >= MIN_HR_MAX_OVER_REST_GAP_BPM;
    }

    private String hrRestSourceDescription(PersonalBaseline baseline) {
        int sampleSize = baseline.sampleSizeFor("rhr");
        return "Personal resting-heart-rate baseline (" + baseline.windowDescription()
                + ", n=" + sampleSize + ") - the same baseline used by the readiness score.";
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record HrMaxReading(int hrMaxBpm, Instant recordedAt, int sampleSize, String source) {}

    /**
     * Highest real {@code max_heart_rate} recorded across activity sessions
     * in the window, plus when that specific reading happened and how many
     * qualifying sessions contributed — see class Javadoc for why this
     * queries {@code activities} directly (matching {@code
     * trainingload.application.TrainingLoadService}'s convention) rather
     * than going through {@code activities.application.ActivitySessionRepository}.
     */
    private Optional<HrMaxReading> findHrMax(Long accountId, Instant windowStart) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT start_time, max_heart_rate FROM activities " +
                    "WHERE account_id = ? AND max_heart_rate IS NOT NULL AND start_time >= ? " +
                    "ORDER BY max_heart_rate DESC LIMIT 1",
                    accountId, Timestamp.from(windowStart)
            );
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activities WHERE account_id = ? AND max_heart_rate IS NOT NULL AND start_time >= ?",
                    Integer.class, accountId, Timestamp.from(windowStart)
            );
            int sampleSize = count != null ? count : 1;

            Map<String, Object> row = rows.get(0);
            int hrMaxBpm = ((Number) row.get("max_heart_rate")).intValue();
            Instant recordedAt = ((Timestamp) row.get("start_time")).toInstant();
            String source = "Highest heart rate recorded (" + hrMaxBpm + " bpm) across "
                    + sampleSize + " real activity session(s) with heart-rate data in the last "
                    + HR_MAX_WINDOW_DAYS + " days.";

            return Optional.of(new HrMaxReading(hrMaxBpm, recordedAt, sampleSize, source));
        } catch (Exception e) {
            log.warn("Failed to fetch HRmax for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Map<YearMonth, Integer> fetchMonthlyHrMax(Long accountId, Instant since) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT date_trunc('month', start_time) AS month, MAX(max_heart_rate) AS hr_max " +
                    "FROM activities WHERE account_id = ? AND max_heart_rate IS NOT NULL AND start_time >= ? " +
                    "GROUP BY date_trunc('month', start_time)",
                    accountId, Timestamp.from(since)
            );
            Map<YearMonth, Integer> byMonth = new HashMap<>();
            for (Map<String, Object> row : rows) {
                LocalDate monthStart = ((Timestamp) row.get("month")).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                byMonth.put(YearMonth.from(monthStart), ((Number) row.get("hr_max")).intValue());
            }
            return byMonth;
        } catch (Exception e) {
            log.warn("Failed to fetch monthly HRmax for account {}: {}", accountId, e.getMessage(), e);
            return Map.of();
        }
    }

    private Map<YearMonth, Double> fetchMonthlyAvgRhr(Long accountId, Instant since) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT date_trunc('month', time) AS month, AVG(value) AS avg_rhr " +
                    "FROM measurements WHERE account_id = ? AND metric_type = 'rhr' AND time >= ? " +
                    "GROUP BY date_trunc('month', time)",
                    accountId, Timestamp.from(since)
            );
            Map<YearMonth, Double> byMonth = new HashMap<>();
            for (Map<String, Object> row : rows) {
                LocalDate monthStart = ((Timestamp) row.get("month")).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                byMonth.put(YearMonth.from(monthStart), ((Number) row.get("avg_rhr")).doubleValue());
            }
            return byMonth;
        } catch (Exception e) {
            log.warn("Failed to fetch monthly avg RHR for account {}: {}", accountId, e.getMessage(), e);
            return Map.of();
        }
    }
}
