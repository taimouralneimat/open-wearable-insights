package com.openwearableinsights.api.strength.application;

import com.openwearableinsights.api.strength.domain.StrengthExercise;
import com.openwearableinsights.api.strength.domain.StrengthSet;
import com.openwearableinsights.api.strength.domain.StrengthWorkout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists and reads manually-logged {@code strength_workouts} /
 * {@code strength_sets} rows (see V09 migration).
 *
 * <p>Write side ({@link #logWorkout}) inserts the parent workout row, then
 * batch-inserts every set flattened across all exercises with a single
 * workout-wide {@code set_order} — mirrors {@code BiomarkerReadingRepository
 * #persist}'s single-insert-then-batch-insert shape, minus provenance: this
 * is direct self-reported user input (like {@code journal_entries}), not an
 * imported file, so there's no import batch to attribute it to.
 *
 * <p>Duration estimation (turning a possibly-null {@code duration_minutes}
 * into the {@code durationMinutes}/{@code durationIsEstimated} pair every
 * read returns) lives in {@link StrengthTrainingService}, not here — this
 * class only stores and retrieves what was actually written.
 */
@Repository
public class StrengthWorkoutRepository {

    private static final Logger log = LoggerFactory.getLogger(StrengthWorkoutRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public StrengthWorkoutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a new workout and its sets. {@code setOrder} is assigned
     * globally across all exercises in the order given, 1-based.
     *
     * @return the persisted workout, as {@link #findById} would return it
     */
    public StrengthWorkout logWorkout(Long accountId, Instant startedAt, Integer userDurationMinutes,
                                       String note, List<StrengthExercise> exercises) {
        Long workoutId = jdbcTemplate.queryForObject(
                "INSERT INTO strength_workouts (account_id, started_at, duration_minutes, note) " +
                "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, accountId, Timestamp.from(startedAt), userDurationMinutes, note
        );

        List<FlatSet> flatSets = flatten(exercises);
        if (!flatSets.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO strength_sets (workout_id, exercise_name, set_order, reps, weight_kg) " +
                    "VALUES (?, ?, ?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            FlatSet f = flatSets.get(i);
                            ps.setLong(1, workoutId);
                            ps.setString(2, f.exerciseName);
                            ps.setInt(3, f.set.setOrder());
                            ps.setInt(4, f.set.reps());
                            if (f.set.weightKg() == null) {
                                ps.setNull(5, Types.DOUBLE);
                            } else {
                                ps.setDouble(5, f.set.weightKg());
                            }
                        }

                        @Override
                        public int getBatchSize() {
                            return flatSets.size();
                        }
                    }
            );
        }

        log.info("Logged strength workout {} for account {} with {} sets", workoutId, accountId, flatSets.size());
        return findById(accountId, workoutId)
                .orElseThrow(() -> new IllegalStateException("Just-inserted workout " + workoutId + " not found"));
    }

    /** Most recent workouts first, each with its full set list. */
    public List<StrengthWorkout> findRecent(Long accountId, int limit) {
        try {
            List<Map<String, Object>> workoutRows = jdbcTemplate.queryForList(
                    "SELECT id, started_at, duration_minutes, note FROM strength_workouts " +
                    "WHERE account_id = ? ORDER BY started_at DESC LIMIT ?",
                    accountId, limit
            );
            List<StrengthWorkout> workouts = new ArrayList<>();
            for (Map<String, Object> row : workoutRows) {
                workouts.add(toWorkout(row));
            }
            return workouts;
        } catch (Exception e) {
            log.warn("Failed to fetch recent strength workouts for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    public Optional<StrengthWorkout> findById(Long accountId, Long id) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, started_at, duration_minutes, note FROM strength_workouts " +
                    "WHERE account_id = ? AND id = ?",
                    accountId, id
            );
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toWorkout(rows.get(0)));
        } catch (Exception e) {
            log.warn("Failed to fetch strength workout {} for account {}: {}", id, accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** Fetches this workout's sets and groups them back into per-exercise order (first-seen exercise order). */
    private StrengthWorkout toWorkout(Map<String, Object> row) {
        Long id = ((Number) row.get("id")).longValue();
        List<Map<String, Object>> setRows = jdbcTemplate.queryForList(
                "SELECT exercise_name, set_order, reps, weight_kg FROM strength_sets " +
                "WHERE workout_id = ? ORDER BY set_order",
                id
        );
        Map<String, List<StrengthSet>> byExercise = new LinkedHashMap<>();
        for (Map<String, Object> setRow : setRows) {
            String exerciseName = (String) setRow.get("exercise_name");
            byExercise.computeIfAbsent(exerciseName, k -> new ArrayList<>()).add(new StrengthSet(
                    ((Number) setRow.get("set_order")).intValue(),
                    ((Number) setRow.get("reps")).intValue(),
                    setRow.get("weight_kg") != null ? ((Number) setRow.get("weight_kg")).doubleValue() : null
            ));
        }
        List<StrengthExercise> exercises = byExercise.entrySet().stream()
                .map(e -> new StrengthExercise(e.getKey(), e.getValue()))
                .toList();

        Instant startedAt = ((Timestamp) row.get("started_at")).toInstant();
        Integer userDurationMinutes = row.get("duration_minutes") != null
                ? ((Number) row.get("duration_minutes")).intValue() : null;
        int totalSets = setRows.size();
        int effectiveDuration = userDurationMinutes != null
                ? userDurationMinutes
                : StrengthTrainingService.estimateDurationMinutes(totalSets);

        return new StrengthWorkout(
                id,
                startedAt.toString(),
                userDurationMinutes,
                effectiveDuration,
                userDurationMinutes == null,
                (String) row.get("note"),
                exercises
        );
    }

    private List<FlatSet> flatten(List<StrengthExercise> exercises) {
        List<FlatSet> flat = new ArrayList<>();
        int order = 1;
        for (StrengthExercise exercise : exercises) {
            for (StrengthSet set : exercise.sets()) {
                flat.add(new FlatSet(exercise.exerciseName(), new StrengthSet(order++, set.reps(), set.weightKg())));
            }
        }
        return flat;
    }

    private record FlatSet(String exerciseName, StrengthSet set) {}
}
