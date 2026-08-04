package com.openwearableinsights.api.strength.adapter.in;

import com.openwearableinsights.api.strength.application.StrengthTrainingService;
import com.openwearableinsights.api.strength.application.StrengthWorkoutRepository;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrend;
import com.openwearableinsights.api.strength.domain.StrengthExercise;
import com.openwearableinsights.api.strength.domain.StrengthSet;
import com.openwearableinsights.api.strength.domain.StrengthWorkout;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for strength training tracking — docs/product/
 * parity-matrix.md row 25: manual set/rep/weight logging plus the
 * "Strength Activity Time" trend (real Garmin-derived + manually-logged
 * minutes, disclosed separately — see {@link StrengthTrainingService}) and
 * the optional weekly-minutes goal.
 */
@RestController
@RequestMapping("/api/v1/strength")
@Tag(name = "Strength", description = "Strength workout logging, Strength Activity Time trend, and weekly goal")
public class StrengthController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;
    private static final int DEFAULT_WORKOUTS_LIMIT = 20;

    private final StrengthWorkoutRepository workoutRepository;
    private final StrengthTrainingService trainingService;

    public StrengthController(StrengthWorkoutRepository workoutRepository, StrengthTrainingService trainingService) {
        this.workoutRepository = workoutRepository;
        this.trainingService = trainingService;
    }

    @PostMapping("/workouts")
    @Operation(summary = "Log a strength workout",
            description = "Records a manually-logged workout with per-exercise sets (reps + optional weight). "
                    + "startedAt defaults to now if omitted. durationMinutes is optional — when absent, the "
                    + "trend/summary views estimate one from set count rather than measuring it (see "
                    + "StrengthTrainingService).")
    public StrengthWorkout logWorkout(@Valid @RequestBody LogWorkoutRequest request) {
        Instant startedAt = request.startedAt() != null ? request.startedAt() : Instant.now();
        List<StrengthExercise> exercises = request.exercises().stream()
                .map(e -> new StrengthExercise(
                        e.exerciseName(),
                        e.sets().stream().map(s -> new StrengthSet(0, s.reps(), s.weightKg())).toList()
                ))
                .toList();
        return workoutRepository.logWorkout(DEFAULT_ACCOUNT_ID, startedAt, request.durationMinutes(), request.note(), exercises);
    }

    @GetMapping("/workouts")
    @Operation(summary = "List recent manually-logged strength workouts",
            description = "Most recent first, up to " + DEFAULT_WORKOUTS_LIMIT + ". Empty list if none logged yet.")
    public List<StrengthWorkout> listWorkouts() {
        return workoutRepository.findRecent(DEFAULT_ACCOUNT_ID, DEFAULT_WORKOUTS_LIMIT);
    }

    @GetMapping("/workouts/{id}")
    @Operation(summary = "Get one logged strength workout, with its full exercise/set detail")
    public StrengthWorkout getWorkout(@PathVariable Long id) {
        return workoutRepository.findById(DEFAULT_ACCOUNT_ID, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No strength workout with id " + id));
    }

    @GetMapping("/trends")
    @Operation(summary = "Get the Strength Activity Time trend",
            description = "window is 'weekly' (last 7 days, daily buckets), 'monthly' (last 30 days, weekly "
                    + "buckets), or 'sixmonth' (last 180 days, monthly buckets); defaults to weekly. Combines real "
                    + "Garmin-derived session duration with manually-logged workout duration, always disclosed "
                    + "separately per bucket — see the response's limitations field for what this trend does and "
                    + "doesn't capture.")
    public StrengthActivityTrend getTrends(@RequestParam(required = false) String window) {
        return trainingService.computeTrend(DEFAULT_ACCOUNT_ID, StrengthTrainingService.Window.fromParam(window));
    }

    @GetMapping("/goal")
    @Operation(summary = "Get the optional weekly strength-minutes goal",
            description = "weeklyGoalMinutes is null if the account hasn't set one.")
    public GoalResponse getGoal() {
        return new GoalResponse(trainingService.fetchWeeklyGoalMinutes(DEFAULT_ACCOUNT_ID));
    }

    @PutMapping("/goal")
    @Operation(summary = "Set (or clear, with null) the weekly strength-minutes goal")
    public GoalResponse setGoal(@RequestBody GoalResponse request) {
        return new GoalResponse(trainingService.setWeeklyGoalMinutes(DEFAULT_ACCOUNT_ID, request.weeklyGoalMinutes()));
    }

    public record LogWorkoutRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedAt,
            @Min(1) Integer durationMinutes,
            String note,
            @NotEmpty List<ExerciseRequest> exercises
    ) {}

    public record ExerciseRequest(
            @NotBlank String exerciseName,
            @NotEmpty List<SetRequest> sets
    ) {}

    public record SetRequest(
            @Min(1) int reps,
            Double weightKg
    ) {}

    public record GoalResponse(Integer weeklyGoalMinutes) {}
}
