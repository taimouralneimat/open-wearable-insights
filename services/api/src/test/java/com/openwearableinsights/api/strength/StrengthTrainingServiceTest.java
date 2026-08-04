package com.openwearableinsights.api.strength;

import com.openwearableinsights.api.strength.application.StrengthTrainingService;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrend;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrendPoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StrengthTrainingService}: the duration-estimate
 * formula (pure) and the combined Garmin + manual trend computation
 * (mocked {@link JdbcTemplate}, matching the style used elsewhere, e.g.
 * {@code trainingload.TrainingLoadServiceTest}).
 *
 * <p>Uses synthetic workout/session data only — no real health data.
 */
class StrengthTrainingServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    @Test
    void estimateDurationMinutes_threeMinutesPerSet() {
        assertThat(StrengthTrainingService.estimateDurationMinutes(10)).isEqualTo(30);
        assertThat(StrengthTrainingService.estimateDurationMinutes(1)).isEqualTo(3);
        assertThat(StrengthTrainingService.estimateDurationMinutes(0)).isZero();
    }

    @Test
    void windowFromParam_recognizesEachValueAndDefaultsToWeekly() {
        assertThat(StrengthTrainingService.Window.fromParam("weekly")).isEqualTo(StrengthTrainingService.Window.WEEKLY);
        assertThat(StrengthTrainingService.Window.fromParam("monthly")).isEqualTo(StrengthTrainingService.Window.MONTHLY);
        assertThat(StrengthTrainingService.Window.fromParam("sixmonth")).isEqualTo(StrengthTrainingService.Window.SIXMONTH);
        assertThat(StrengthTrainingService.Window.fromParam(null)).isEqualTo(StrengthTrainingService.Window.WEEKLY);
        assertThat(StrengthTrainingService.Window.fromParam("nonsense")).isEqualTo(StrengthTrainingService.Window.WEEKLY);
    }

    @Test
    void computeTrend_weekly_combinesGarminAndManualMinutesOnSameDayWithoutBlendingSilently() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate day = LocalDate.of(2026, 8, 3); // a Monday
        Instant sessionStart = day.atStartOfDay(ZoneOffset.UTC).plusHours(9).toInstant();

        stubGarminActivities(jdbc, List.of(activityRow(sessionStart, 3600.0))); // 60 real minutes
        stubManualWorkouts(jdbc, List.of(workoutRow(sessionStart.plusSeconds(7200), null, 10))); // no user duration, 10 sets -> 30 estimated
        stubGoal(jdbc, null);

        StrengthTrainingService service = new StrengthTrainingService(jdbc);
        StrengthActivityTrend trend = service.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.WEEKLY);

        assertThat(trend.window()).isEqualTo("weekly");
        assertThat(trend.points()).hasSize(1);
        StrengthActivityTrendPoint point = trend.points().get(0);
        assertThat(point.periodStart()).isEqualTo(day.toString());
        assertThat(point.garminMinutes()).isEqualTo(60.0);
        assertThat(point.manualMinutes()).isEqualTo(30.0);
        assertThat(point.manualMinutesEstimated()).isTrue();
        assertThat(point.totalMinutes()).isEqualTo(90.0); // never silently blended into one unlabeled number
    }

    @Test
    void computeTrend_monthly_bucketsByIsoWeekStartingMonday() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Two sessions in the same ISO week (Mon 2026-08-03 .. Sun 2026-08-09).
        Instant tuesday = LocalDate.of(2026, 8, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant thursday = LocalDate.of(2026, 8, 6).atStartOfDay(ZoneOffset.UTC).toInstant();

        stubGarminActivities(jdbc, List.of(activityRow(tuesday, 1800.0), activityRow(thursday, 1800.0)));
        stubManualWorkouts(jdbc, List.of());
        stubGoal(jdbc, 120);

        StrengthTrainingService service = new StrengthTrainingService(jdbc);
        StrengthActivityTrend trend = service.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.MONTHLY);

        assertThat(trend.window()).isEqualTo("monthly");
        assertThat(trend.points()).hasSize(1);
        StrengthActivityTrendPoint point = trend.points().get(0);
        LocalDate expectedWeekStart = LocalDate.of(2026, 8, 4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(point.periodStart()).isEqualTo(expectedWeekStart.toString());
        assertThat(point.garminMinutes()).isEqualTo(60.0); // 30 + 30, summed within the week bucket
        assertThat(trend.weeklyGoalMinutes()).isEqualTo(120);
    }

    @Test
    void computeTrend_userReportedDuration_neverMarkedAsEstimated() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant start = Instant.parse("2026-08-01T09:00:00Z");
        stubGarminActivities(jdbc, List.of());
        stubManualWorkouts(jdbc, List.of(workoutRow(start, 45, 8))); // user gave 45 explicitly
        stubGoal(jdbc, null);

        StrengthTrainingService service = new StrengthTrainingService(jdbc);
        StrengthActivityTrend trend = service.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.WEEKLY);

        assertThat(trend.points()).hasSize(1);
        StrengthActivityTrendPoint point = trend.points().get(0);
        assertThat(point.manualMinutes()).isEqualTo(45.0);
        assertThat(point.manualMinutesEstimated()).isFalse();
    }

    @Test
    void computeTrend_dbError_returnsEmptyPointsNotException() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(), any(), any())).thenThrow(new RuntimeException("connection lost"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ACCOUNT_ID))).thenReturn(null);

        StrengthTrainingService service = new StrengthTrainingService(jdbc);
        StrengthActivityTrend trend = service.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.WEEKLY);

        assertThat(trend.points()).isEmpty();
        assertThat(trend.limitations()).isNotEmpty();
    }

    @Test
    void fetchAndSetWeeklyGoalMinutes_roundTrips() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("UPDATE accounts SET weekly_strength_minutes_goal"), eq(150), eq(ACCOUNT_ID)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("weekly_strength_minutes_goal"), eq(Integer.class), eq(ACCOUNT_ID)))
                .thenReturn(150);

        StrengthTrainingService service = new StrengthTrainingService(jdbc);
        Integer result = service.setWeeklyGoalMinutes(ACCOUNT_ID, 150);

        assertThat(result).isEqualTo(150);
    }

    private void stubGarminActivities(JdbcTemplate jdbc, List<Map<String, Object>> rows) {
        when(jdbc.queryForList(contains("FROM activities"), eq(ACCOUNT_ID), any(Timestamp.class), eq("training")))
                .thenReturn(rows);
    }

    private void stubManualWorkouts(JdbcTemplate jdbc, List<Map<String, Object>> rows) {
        when(jdbc.queryForList(contains("FROM strength_workouts"), eq(ACCOUNT_ID), any(Timestamp.class)))
                .thenReturn(rows);
    }

    private void stubGoal(JdbcTemplate jdbc, Integer goal) {
        when(jdbc.queryForObject(contains("weekly_strength_minutes_goal"), eq(Integer.class), eq(ACCOUNT_ID)))
                .thenReturn(goal);
    }

    private static Map<String, Object> activityRow(Instant startTime, double durationSeconds) {
        Map<String, Object> row = new HashMap<>();
        row.put("start_time", Timestamp.from(startTime));
        row.put("duration_seconds", durationSeconds);
        return row;
    }

    private static Map<String, Object> workoutRow(Instant startedAt, Integer durationMinutes, int setCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("started_at", Timestamp.from(startedAt));
        row.put("duration_minutes", durationMinutes);
        row.put("set_count", setCount);
        return row;
    }
}
