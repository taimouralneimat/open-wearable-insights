package com.openwearableinsights.api.strength;

import com.openwearableinsights.api.strength.application.StrengthWorkoutRepository;
import com.openwearableinsights.api.strength.domain.StrengthExercise;
import com.openwearableinsights.api.strength.domain.StrengthSet;
import com.openwearableinsights.api.strength.domain.StrengthWorkout;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StrengthWorkoutRepository} using a mocked {@link
 * JdbcTemplate}, matching the Mockito style used elsewhere (see {@code
 * biomarkers.BiomarkerReadingRepositoryTest}).
 *
 * <p>Uses synthetic workout data only — no real health data.
 */
class StrengthWorkoutRepositoryTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Instant STARTED_AT = Instant.parse("2026-08-01T10:00:00Z");

    @Test
    void logWorkout_insertsWorkoutThenBatchInsertsSetsFlattenedWithWorkoutWideOrder() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("INSERT INTO strength_workouts"), eq(Long.class),
                eq(ACCOUNT_ID), any(Timestamp.class), eq(45), eq("Leg day"))).thenReturn(99L);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        when(jdbc.batchUpdate(contains("INSERT INTO strength_sets"), setterCaptor.capture()))
                .thenReturn(new int[]{1, 1, 1});
        stubFindById(jdbc, 99L, STARTED_AT, 45, "Leg day", List.of(
                setRow("Back Squat", 1, 10, 60.0),
                setRow("Back Squat", 2, 8, 65.0),
                setRow("Romanian Deadlift", 3, 10, 50.0)
        ));

        StrengthWorkoutRepository repository = new StrengthWorkoutRepository(jdbc);
        List<StrengthExercise> exercises = List.of(
                new StrengthExercise("Back Squat", List.of(new StrengthSet(0, 10, 60.0), new StrengthSet(0, 8, 65.0))),
                new StrengthExercise("Romanian Deadlift", List.of(new StrengthSet(0, 10, 50.0)))
        );

        StrengthWorkout workout = repository.logWorkout(ACCOUNT_ID, STARTED_AT, 45, "Leg day", exercises);

        assertThat(workout.id()).isEqualTo(99L);
        assertThat(workout.userDurationMinutes()).isEqualTo(45);
        assertThat(workout.durationMinutes()).isEqualTo(45);
        assertThat(workout.durationIsEstimated()).isFalse();

        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        assertThat(setter.getBatchSize()).isEqualTo(3);

        // set_order must be assigned globally across exercises (1, 2, 3), not reset per exercise.
        PreparedStatement ps0 = mock(PreparedStatement.class);
        setter.setValues(ps0, 0);
        verify(ps0).setLong(1, 99L);
        verify(ps0).setString(2, "Back Squat");
        verify(ps0).setInt(3, 1);
        verify(ps0).setInt(4, 10);
        verify(ps0).setDouble(5, 60.0);

        PreparedStatement ps2 = mock(PreparedStatement.class);
        setter.setValues(ps2, 2);
        verify(ps2).setString(2, "Romanian Deadlift");
        verify(ps2).setInt(3, 3);
        verify(ps2).setInt(4, 10);
        verify(ps2).setDouble(5, 50.0);
    }

    @Test
    void logWorkout_bodyweightSet_writesNullWeightNotZero() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("INSERT INTO strength_workouts"), eq(Long.class),
                eq(ACCOUNT_ID), any(Timestamp.class), eq((Integer) null), eq((String) null))).thenReturn(5L);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        when(jdbc.batchUpdate(contains("INSERT INTO strength_sets"), setterCaptor.capture()))
                .thenReturn(new int[]{1});
        stubFindById(jdbc, 5L, STARTED_AT, null, null, List.of(setRow("Pull-up", 1, 12, null)));

        StrengthWorkoutRepository repository = new StrengthWorkoutRepository(jdbc);
        List<StrengthExercise> exercises = List.of(new StrengthExercise("Pull-up", List.of(new StrengthSet(0, 12, null))));

        StrengthWorkout workout = repository.logWorkout(ACCOUNT_ID, STARTED_AT, null, null, exercises);

        // No user duration -> estimated from set count (1 set).
        assertThat(workout.durationIsEstimated()).isTrue();
        assertThat(workout.durationMinutes()).isGreaterThan(0);

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, 0);
        verify(ps).setNull(5, Types.DOUBLE);
    }

    @Test
    void findRecent_groupsFlatSetRowsBackIntoPerExerciseOrder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM strength_workouts"), eq(ACCOUNT_ID), any(Integer.class)))
                .thenReturn(List.of(workoutRow(1L, STARTED_AT, 30, "Push day")));
        when(jdbc.queryForList(contains("FROM strength_sets"), eq(1L)))
                .thenReturn(List.of(
                        setRow("Bench Press", 1, 10, 80.0),
                        setRow("Bench Press", 2, 8, 85.0),
                        setRow("Overhead Press", 3, 10, 40.0)
                ));

        StrengthWorkoutRepository repository = new StrengthWorkoutRepository(jdbc);
        List<StrengthWorkout> workouts = repository.findRecent(ACCOUNT_ID, 20);

        assertThat(workouts).hasSize(1);
        StrengthWorkout workout = workouts.get(0);
        assertThat(workout.exercises()).hasSize(2);
        assertThat(workout.exercises().get(0).exerciseName()).isEqualTo("Bench Press");
        assertThat(workout.exercises().get(0).sets()).hasSize(2);
        assertThat(workout.exercises().get(1).exerciseName()).isEqualTo("Overhead Press");
        assertThat(workout.exercises().get(1).sets()).hasSize(1);
        assertThat(workout.durationMinutes()).isEqualTo(30);
        assertThat(workout.durationIsEstimated()).isFalse();
    }

    @Test
    void findRecent_dbError_returnsEmptyListNotException() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), any(Integer.class)))
                .thenThrow(new RuntimeException("connection lost"));

        StrengthWorkoutRepository repository = new StrengthWorkoutRepository(jdbc);
        List<StrengthWorkout> workouts = repository.findRecent(ACCOUNT_ID, 20);

        assertThat(workouts).isEmpty();
    }

    @Test
    void findById_notFound_returnsEmptyOptional() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM strength_workouts"), eq(ACCOUNT_ID), eq(404L)))
                .thenReturn(List.of());

        StrengthWorkoutRepository repository = new StrengthWorkoutRepository(jdbc);
        Optional<StrengthWorkout> workout = repository.findById(ACCOUNT_ID, 404L);

        assertThat(workout).isEmpty();
    }

    private void stubFindById(JdbcTemplate jdbc, Long workoutId, Instant startedAt, Integer durationMinutes,
                               String note, List<Map<String, Object>> setRows) {
        when(jdbc.queryForList(contains("FROM strength_workouts"), eq(ACCOUNT_ID), eq(workoutId)))
                .thenReturn(List.of(workoutRow(workoutId, startedAt, durationMinutes, note)));
        when(jdbc.queryForList(contains("FROM strength_sets"), eq(workoutId)))
                .thenReturn(setRows);
    }

    private static Map<String, Object> workoutRow(Long id, Instant startedAt, Integer durationMinutes, String note) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("started_at", Timestamp.from(startedAt));
        row.put("duration_minutes", durationMinutes);
        row.put("note", note);
        return row;
    }

    private static Map<String, Object> setRow(String exerciseName, int setOrder, int reps, Double weightKg) {
        Map<String, Object> row = new HashMap<>();
        row.put("exercise_name", exerciseName);
        row.put("set_order", setOrder);
        row.put("reps", reps);
        row.put("weight_kg", weightKg);
        return row;
    }
}
