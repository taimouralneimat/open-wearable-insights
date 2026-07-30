package com.openwearableinsights.api.trainingload.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadSummary;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes real training load from workout sessions in the {@code activities}
 * table, replacing the step-count proxy that {@code CurrentMetricsService}
 * used before this module existed.
 *
 * <p><b>Per-session load ({@value #ALGORITHM_VERSION}):</b> a simple,
 * deterministic HR-zone-weighted load, in the spirit of TRIMP (Training
 * Impulse) but avoiding TRIMP's need for a per-user max/resting HR to
 * compute an exponential intensity weight — {@code hr_zone_seconds} already
 * buckets the session into discrete intensity zones, so we weight each
 * zone's minutes by "how intense that zone is" directly:
 *
 * <pre>
 *   load = sum over zone i of (i + 1) * minutesInZone(i)
 * </pre>
 *
 * <p>Zone 0 (lowest intensity, e.g. warm-up/recovery) gets multiplier 1;
 * zone 4 (highest, e.g. anaerobic/VO2max) gets multiplier 5. This is
 * intentionally the simplest defensible weighting that (a) makes harder
 * zones count for more per minute than easier ones, matching how TRIMP and
 * other HR-zone load models behave, and (b) requires no additional
 * per-account physiological inputs (max HR, HR reserve, etc.) that we don't
 * reliably have yet. It is a documented v1 approximation, not a
 * peer-reviewed TRIMP variant — see docs/product/parity-matrix.md row 2.
 *
 * <p><b>Missing hr_zone_seconds fallback:</b> some sessions (e.g. from
 * connectors/devices that don't report zone breakdowns) have an empty or
 * null {@code hr_zone_seconds}. Rather than silently scoring a real workout
 * as zero load — which would corrupt the acute/chronic average — we fall
 * back to a flat-intensity estimate: {@link #FALLBACK_ZONE_WEIGHT} (the
 * "moderate" zone-3-of-5 multiplier) times session minutes. This assumes an
 * "average" workout intensity, which is obviously wrong for any specific
 * session, but is a defensible central estimate and is clearly documented
 * here rather than hidden.
 *
 * <p><b>Aggregation:</b> acute load = average of per-day summed session
 * load over the last 7 days; chronic load = the same over the last 28 days.
 * Following {@code CurrentMetricsService#fetchStepsSum}'s existing
 * convention, the average is computed only over days that have at least
 * one activity row — a rest day contributes no row to the GROUP BY, so it
 * does not pull the average toward zero. This means both acuteLoad and
 * chronicLoad represent "typical load on a training day" rather than
 * "average load per calendar day including rest days". We keep this
 * consistent with the existing steps-based convention (and with each
 * other, which is what matters for the ACWR ratio in
 * {@code ReadinessCalculator}) rather than switching semantics only for
 * this one input. This could be revisited project-wide once rest-day
 * semantics are settled elsewhere.
 */
@Service
public class TrainingLoadService {

    private static final Logger log = LoggerFactory.getLogger(TrainingLoadService.class);

    /** Versioned, deterministic — see class Javadoc for the exact formula. */
    public static final String ALGORITHM_VERSION = "trainingload-v1";

    private static final int ACUTE_WINDOW_DAYS = 7;
    private static final int CHRONIC_WINDOW_DAYS = 28;

    // Flat-intensity fallback multiplier for sessions with no HR-zone
    // breakdown: the same weight as zone index 2 (the middle of 5 zones,
    // 0-indexed), i.e. "moderate" intensity. See class Javadoc.
    private static final double FALLBACK_ZONE_WEIGHT = 3.0;

    // loadStatus thresholds for /trainingload/summary. These follow a common
    // sports-science convention for the acute:chronic workload ratio (ACWR)
    // — roughly 0.8-1.3 as a "sweet spot", above 1.5 as elevated-risk
    // territory — but this is stated here as a wellness heuristic, not a
    // clinical claim: this app is wellness/fitness analytics, not a medical
    // device (docs/product/vision.md principle 4), the ratio is computed
    // from a single-source, v1, non-personalized load formula (see class
    // Javadoc), and the thresholds are not derived from this user's own
    // data. See TrainingLoadSummary's Javadoc and #classifyLoadStatus.
    private static final double LOAD_STATUS_LOW_MAX = 0.8;
    private static final double LOAD_STATUS_OPTIMAL_MAX = 1.3;
    private static final double LOAD_STATUS_ELEVATED_MAX = 1.5;

    private static final List<String> LOAD_STATUS_LIMITATIONS = List.of(
            "loadStatus is a wellness heuristic based on a common sports-science "
                    + "ACWR convention (roughly 0.8-1.3 considered a \"sweet spot\", "
                    + "above 1.5 considered elevated-risk territory in some training "
                    + "literature) applied to this app's own v1 HR-zone load formula "
                    + "(" + ALGORITHM_VERSION + "). It is not a medical or clinical "
                    + "assessment, is not personalized to this user, and is not a "
                    + "prediction or guarantee of injury risk or its absence."
    );

    private static final List<String> NO_DATA_LIMITATIONS = List.of(
            "Not enough recent activity data to compute acute/chronic training "
                    + "load or ACWR — needs at least one activity in the last "
                    + ACUTE_WINDOW_DAYS + " days and at least one in the last "
                    + CHRONIC_WINDOW_DAYS + " days."
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TrainingLoadService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** Average daily training load over the last {@value #ACUTE_WINDOW_DAYS} days. */
    public double fetchAcuteLoad(Long accountId) {
        return fetchAverageDailyLoad(accountId, Instant.now().minus(ACUTE_WINDOW_DAYS, ChronoUnit.DAYS));
    }

    /** Average daily training load over the last {@value #CHRONIC_WINDOW_DAYS} days. */
    public double fetchChronicLoad(Long accountId) {
        return fetchAverageDailyLoad(accountId, Instant.now().minus(CHRONIC_WINDOW_DAYS, ChronoUnit.DAYS));
    }

    /**
     * Per-day training load history for the last {@code days} days, sorted
     * oldest-first. Reuses the same per-day GROUP-BY aggregation that
     * {@link #fetchAverageDailyLoad} averages down to a single number — this
     * just returns the intermediate per-day map instead of collapsing it.
     *
     * <p>Only days with at least one activity row are included; rest days
     * are omitted, not zero-filled or interpolated (same convention as the
     * acute/chronic averages — see class Javadoc).
     */
    public List<TrainingLoadTrendPoint> fetchDailyLoadHistory(Long accountId, int days) {
        Map<LocalDate, Double> loadByDay = fetchDailyLoad(accountId, Instant.now().minus(days, ChronoUnit.DAYS));
        return loadByDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrainingLoadTrendPoint(e.getKey().toString(), e.getValue()))
                .toList();
    }

    /**
     * Current acute/chronic/ACWR snapshot for {@code /trainingload/summary}.
     *
     * <p>{@code acwr} is {@code acuteLoad / chronicLoad} — the exact formula
     * {@code ReadinessCalculator} uses for the "Training load (ACWR)"
     * factor. Following the same presence convention
     * {@code CurrentMetricsService} already uses when feeding that factor
     * (an average of zero is indistinguishable from "no qualifying activity
     * in the window" for this average-over-active-days aggregation), both
     * loads must be strictly positive before a ratio is reported — otherwise
     * this returns an honest "not enough data" summary rather than a
     * fabricated 0 or 1.0.
     */
    public TrainingLoadSummary computeSummary(Long accountId) {
        double acute = fetchAcuteLoad(accountId);
        double chronic = fetchChronicLoad(accountId);

        if (acute <= 0 || chronic <= 0) {
            return new TrainingLoadSummary(
                    acute > 0 ? acute : null,
                    chronic > 0 ? chronic : null,
                    null,
                    "unknown",
                    ALGORITHM_VERSION,
                    "none",
                    NO_DATA_LIMITATIONS
            );
        }

        double acwr = acute / chronic;
        return new TrainingLoadSummary(
                acute, chronic, acwr,
                classifyLoadStatus(acwr),
                ALGORITHM_VERSION,
                "medium",
                LOAD_STATUS_LIMITATIONS
        );
    }

    /**
     * Plain-language load-status band from the ACWR ratio. See the
     * {@code LOAD_STATUS_*} constants' Javadoc for the sports-science
     * convention this is based on and why it's a wellness heuristic, not a
     * clinical claim.
     */
    private String classifyLoadStatus(double acwr) {
        if (acwr < LOAD_STATUS_LOW_MAX) {
            return "low";
        }
        if (acwr <= LOAD_STATUS_OPTIMAL_MAX) {
            return "optimal";
        }
        if (acwr <= LOAD_STATUS_ELEVATED_MAX) {
            return "elevated";
        }
        return "high";
    }

    /**
     * Per-session HR-zone-weighted load. Public and pure (no DB access) so
     * it's directly unit-testable against known synthetic sessions.
     *
     * @param hrZoneSeconds seconds spent in each HR zone, index 0 = lowest
     *                      intensity; null/empty triggers the flat-intensity
     *                      fallback
     * @param durationSeconds session duration, used only by the fallback
     */
    public double computeSessionLoad(List<Double> hrZoneSeconds, double durationSeconds) {
        if (!hasZoneData(hrZoneSeconds)) {
            return computeFallbackLoad(durationSeconds);
        }
        double load = 0.0;
        for (int zone = 0; zone < hrZoneSeconds.size(); zone++) {
            Double seconds = hrZoneSeconds.get(zone);
            if (seconds == null || seconds <= 0) {
                continue;
            }
            double minutes = seconds / 60.0;
            double zoneWeight = zone + 1; // zone 0 -> 1, zone 4 -> 5
            load += zoneWeight * minutes;
        }
        return load;
    }

    private boolean hasZoneData(List<Double> hrZoneSeconds) {
        if (hrZoneSeconds == null || hrZoneSeconds.isEmpty()) {
            return false;
        }
        return hrZoneSeconds.stream().anyMatch(v -> v != null && v > 0);
    }

    private double computeFallbackLoad(double durationSeconds) {
        double minutes = durationSeconds / 60.0;
        return FALLBACK_ZONE_WEIGHT * minutes;
    }

    private double fetchAverageDailyLoad(Long accountId, Instant since) {
        Map<LocalDate, Double> loadByDay = fetchDailyLoad(accountId, since);
        if (loadByDay.isEmpty()) {
            return 0.0;
        }
        return loadByDay.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Shared per-day aggregation: sums each day's session loads into one
     * {@code load-by-day} map for all activity rows since {@code since}.
     * {@link #fetchAverageDailyLoad} collapses this to a single average;
     * {@link #fetchDailyLoadHistory} returns it (sorted) as-is. Kept as one
     * query + one GROUP-BY loop so the two callers can't drift.
     */
    private Map<LocalDate, Double> fetchDailyLoad(Long accountId, Instant since) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT start_time, duration_seconds, hr_zone_seconds FROM activities " +
                    "WHERE account_id = ? AND start_time >= ?",
                    accountId, Timestamp.from(since)
            );

            Map<LocalDate, Double> loadByDay = new HashMap<>();
            for (Map<String, Object> row : rows) {
                LocalDate day = ((Timestamp) row.get("start_time")).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                double durationSeconds = ((Number) row.get("duration_seconds")).doubleValue();
                List<Double> hrZoneSeconds = parseHrZoneSeconds(row.get("hr_zone_seconds"));
                double sessionLoad = computeSessionLoad(hrZoneSeconds, durationSeconds);
                loadByDay.merge(day, sessionLoad, Double::sum);
            }
            return loadByDay;
        } catch (Exception e) {
            log.warn("Failed to fetch training load for account {}: {}", accountId, e.getMessage(), e);
            return Map.of();
        }
    }

    private List<Double> parseHrZoneSeconds(Object hrZoneJson) {
        if (hrZoneJson == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    hrZoneJson.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class)
            );
        } catch (Exception e) {
            log.warn("Failed to parse hr_zone_seconds '{}': {}", hrZoneJson, e.getMessage());
            return List.of();
        }
    }
}
