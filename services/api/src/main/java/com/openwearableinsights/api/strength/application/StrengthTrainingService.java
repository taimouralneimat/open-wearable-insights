package com.openwearableinsights.api.strength.application;

import com.openwearableinsights.api.strength.domain.StrengthActivityTrend;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes the "Strength Activity Time" trend (docs/product/parity-matrix.md
 * row 25) and manages the optional weekly strength-minutes goal.
 *
 * <p><b>Two data sources, disclosed separately, never silently blended:</b>
 * <ul>
 *   <li><b>Garmin-derived minutes</b> — real session duration from the
 *   {@code activities} table (see {@code activities} module, ADR-0006),
 *   filtered to {@code sport = 'training'}. This is a heuristic, not an
 *   exact match: {@code GarminFitConnector} stores only FIT's top-level
 *   {@code Sport} enum (lowercased), and Garmin devices tag a strength
 *   workout as {@code Sport.TRAINING} with a {@code SubSport.STRENGTH_TRAINING}
 *   that this codebase doesn't parse or store yet (verified against the FIT
 *   SDK's {@code Sport}/{@code SubSport} enums — there is no dedicated
 *   top-level "strength" sport). {@code sport = 'training'} is therefore the
 *   closest available signal; it can include other {@code SubSport} values
 *   under the same top-level sport (e.g. flexibility/cardio training) and
 *   would miss a strength session a device tagged under a different sport
 *   entirely. See the {@code strength} module's package-info for why
 *   FIT set-level strength messages ({@code SetMesg}) aren't parsed instead
 *   — deliberately deferred, same reasoning as the biomarkers module's PDF
 *   deferral (row 22).</li>
 *   <li><b>Manually-logged minutes</b> — from {@code strength_workouts}
 *   (see {@link StrengthWorkoutRepository}), each either the user's own
 *   reported duration or {@link #estimateDurationMinutes} when they didn't
 *   give one. Every trend point flags {@code manualMinutesEstimated} when
 *   any contributing workout used the estimate.</li>
 * </ul>
 *
 * <p><b>Estimation formula ({@value #ESTIMATED_MINUTES_PER_SET} minutes per
 * set):</b> when a manually-logged workout has no user-reported duration,
 * this estimates one as {@code totalSets * ESTIMATED_MINUTES_PER_SET}. This
 * is a plain, openly-stated rule of thumb (a working set plus the rest
 * before the next one commonly runs a couple of minutes) — not a
 * physiological formula, not derived from this user's own data, and not
 * claimed to be accurate for any specific workout. It exists so a workout
 * logged without a duration still counts toward the time trend instead of
 * being silently dropped, exactly the same honesty tradeoff
 * {@code TrainingLoadService}'s {@code FALLBACK_ZONE_WEIGHT} makes for
 * sessions with no HR-zone breakdown.
 */
@Service
public class StrengthTrainingService {

    private static final Logger log = LoggerFactory.getLogger(StrengthTrainingService.class);

    /** See class Javadoc "Estimation formula". */
    public static final int ESTIMATED_MINUTES_PER_SET = 3;

    /** Garmin FIT sport string treated as strength training — see class Javadoc for why this is a heuristic. */
    private static final String GARMIN_STRENGTH_SPORT = "training";

    private static final List<String> LIMITATIONS = List.of(
            "Garmin-derived minutes match sessions with sport 'training' (the closest FIT signal for a "
                    + "strength workout) — this app doesn't parse the sub-sport detail yet that would confirm "
                    + "'strength' specifically, so it may include other training types or miss ones tagged "
                    + "differently by the device.",
            "Manually-logged workouts without an entered duration are estimated as "
                    + ESTIMATED_MINUTES_PER_SET + " minutes per set, a rough rule of thumb, not a measurement — "
                    + "enter an actual duration when logging a workout for an exact figure.",
            "Per-set FIT data (individual reps/weight from a Garmin device) isn't imported — only the manual "
                    + "log captures rep-level detail; Garmin sessions contribute duration only."
    );

    public enum Window {
        WEEKLY(7, Bucket.DAY),
        MONTHLY(30, Bucket.WEEK),
        SIXMONTH(180, Bucket.MONTH);

        final int days;
        final Bucket bucket;

        Window(int days, Bucket bucket) {
            this.days = days;
            this.bucket = bucket;
        }

        public static Window fromParam(String raw) {
            if (raw == null || raw.isBlank()) {
                return WEEKLY;
            }
            return switch (raw.trim().toLowerCase()) {
                case "weekly" -> WEEKLY;
                case "monthly" -> MONTHLY;
                case "sixmonth", "six_month", "6mo" -> SIXMONTH;
                default -> WEEKLY;
            };
        }
    }

    private enum Bucket { DAY, WEEK, MONTH }

    private final JdbcTemplate jdbcTemplate;

    public StrengthTrainingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** See class Javadoc "Estimation formula". Pure, so directly unit-testable. */
    public static int estimateDurationMinutes(int totalSets) {
        return Math.max(totalSets, 0) * ESTIMATED_MINUTES_PER_SET;
    }

    public StrengthActivityTrend computeTrend(Long accountId, Window window) {
        Instant since = Instant.now().minus(window.days, ChronoUnit.DAYS);

        TreeMap<LocalDate, MutableBucket> buckets = new TreeMap<>();
        try {
            accumulateGarminMinutes(accountId, since, window.bucket, buckets);
            accumulateManualMinutes(accountId, since, window.bucket, buckets);
        } catch (Exception e) {
            log.warn("Failed to compute strength activity trend for account {}: {}", accountId, e.getMessage(), e);
            return new StrengthActivityTrend(windowParam(window), List.of(), fetchWeeklyGoalMinutes(accountId), LIMITATIONS);
        }

        List<StrengthActivityTrendPoint> points = buckets.entrySet().stream()
                .map(e -> {
                    LocalDate start = e.getKey();
                    LocalDate end = bucketEnd(start, window.bucket);
                    MutableBucket b = e.getValue();
                    return new StrengthActivityTrendPoint(
                            start.toString(), end.toString(),
                            b.garminMinutes, b.manualMinutes, b.manualEstimated,
                            b.garminMinutes + b.manualMinutes
                    );
                })
                .toList();

        return new StrengthActivityTrend(windowParam(window), points, fetchWeeklyGoalMinutes(accountId), LIMITATIONS);
    }

    private String windowParam(Window window) {
        return switch (window) {
            case WEEKLY -> "weekly";
            case MONTHLY -> "monthly";
            case SIXMONTH -> "sixmonth";
        };
    }

    private void accumulateGarminMinutes(Long accountId, Instant since, Bucket bucket, TreeMap<LocalDate, MutableBucket> buckets) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT start_time, duration_seconds FROM activities " +
                "WHERE account_id = ? AND start_time >= ? AND lower(sport) = ?",
                accountId, Timestamp.from(since), GARMIN_STRENGTH_SPORT
        );
        for (Map<String, Object> row : rows) {
            LocalDate day = ((Timestamp) row.get("start_time")).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            double minutes = ((Number) row.get("duration_seconds")).doubleValue() / 60.0;
            bucketFor(buckets, day, bucket).garminMinutes += minutes;
        }
    }

    private void accumulateManualMinutes(Long accountId, Instant since, Bucket bucket, TreeMap<LocalDate, MutableBucket> buckets) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT w.started_at AS started_at, w.duration_minutes AS duration_minutes, " +
                "COUNT(s.id) AS set_count FROM strength_workouts w " +
                "LEFT JOIN strength_sets s ON s.workout_id = w.id " +
                "WHERE w.account_id = ? AND w.started_at >= ? " +
                "GROUP BY w.id, w.started_at, w.duration_minutes",
                accountId, Timestamp.from(since)
        );
        for (Map<String, Object> row : rows) {
            LocalDate day = ((Timestamp) row.get("started_at")).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            Integer userMinutes = row.get("duration_minutes") != null ? ((Number) row.get("duration_minutes")).intValue() : null;
            int setCount = ((Number) row.get("set_count")).intValue();
            boolean estimated = userMinutes == null;
            int minutes = estimated ? estimateDurationMinutes(setCount) : userMinutes;

            MutableBucket b = bucketFor(buckets, day, bucket);
            b.manualMinutes += minutes;
            b.manualEstimated = b.manualEstimated || estimated;
        }
    }

    private MutableBucket bucketFor(TreeMap<LocalDate, MutableBucket> buckets, LocalDate day, Bucket bucket) {
        LocalDate key = bucketStart(day, bucket);
        return buckets.computeIfAbsent(key, k -> new MutableBucket());
    }

    private LocalDate bucketStart(LocalDate day, Bucket bucket) {
        return switch (bucket) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    private LocalDate bucketEnd(LocalDate start, Bucket bucket) {
        return switch (bucket) {
            case DAY -> start;
            case WEEK -> start.plusDays(6);
            case MONTH -> start.plusMonths(1).minusDays(1);
        };
    }

    /** The account's optional weekly strength-minutes goal, or {@code null} if unset. */
    public Integer fetchWeeklyGoalMinutes(Long accountId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT weekly_strength_minutes_goal FROM accounts WHERE id = ?",
                    Integer.class, accountId
            );
        } catch (Exception e) {
            log.warn("Failed to fetch weekly strength goal for account {}: {}", accountId, e.getMessage(), e);
            return null;
        }
    }

    /** Sets (or clears, with {@code null}) the account's weekly strength-minutes goal. */
    public Integer setWeeklyGoalMinutes(Long accountId, Integer minutes) {
        jdbcTemplate.update(
                "UPDATE accounts SET weekly_strength_minutes_goal = ? WHERE id = ?",
                minutes, accountId
        );
        return fetchWeeklyGoalMinutes(accountId);
    }

    private static final class MutableBucket {
        double garminMinutes;
        double manualMinutes;
        boolean manualEstimated;
    }
}
