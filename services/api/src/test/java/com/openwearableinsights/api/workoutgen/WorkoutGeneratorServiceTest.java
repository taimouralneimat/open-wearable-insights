package com.openwearableinsights.api.workoutgen;

import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadSummary;
import com.openwearableinsights.api.workoutgen.application.WorkoutGeneratorService;
import com.openwearableinsights.api.workoutgen.domain.Equipment;
import com.openwearableinsights.api.workoutgen.domain.GeneratedWorkout;
import com.openwearableinsights.api.workoutgen.domain.Goal;
import com.openwearableinsights.api.workoutgen.domain.Limitation;
import com.openwearableinsights.api.workoutgen.domain.WorkoutExercise;
import com.openwearableinsights.api.workoutgen.domain.WorkoutGenerationRequest;
import com.openwearableinsights.api.workoutgen.domain.WorkoutSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkoutGeneratorService}: real assertions on the
 * deterministic template output — exact exercise selection, exact
 * sets/reps/rest per goal, equipment/limitation filtering, the honest
 * shortfall disclosure when a block can't be filled, real determinism
 * (identical requests produce identical plans), and the real
 * training-load-aware intensity reduction.
 *
 * <p>{@link TrainingLoadService} is mocked — its own ACWR methodology is
 * covered by its own unit tests; this only verifies how {@link
 * WorkoutGeneratorService} reacts to a real "high" load status.
 *
 * <p>Uses only synthetic data (no real health data).
 */
class WorkoutGeneratorServiceTest {

    private TrainingLoadService trainingLoadService;

    @BeforeEach
    void setUp() {
        trainingLoadService = mock(TrainingLoadService.class);
    }

    private WorkoutGeneratorService service() {
        return new WorkoutGeneratorService(trainingLoadService);
    }

    @Test
    void defaultRequest_producesGeneralFitnessBodyweightWorkout() {
        GeneratedWorkout workout = service().generate(
                new WorkoutGenerationRequest(null, null, null, null, null));

        assertThat(workout.goal()).isEqualTo(Goal.GENERAL_FITNESS);
        assertThat(workout.source()).isEqualTo(WorkoutSource.DETERMINISTIC_TEMPLATE);
        assertThat(workout.algorithmVersion()).isEqualTo(WorkoutGeneratorService.ALGORITHM_VERSION);
        assertThat(workout.disclaimer()).isEqualTo(WorkoutGeneratorService.DISCLAIMER);
        assertThat(workout.disclaimer()).contains("not a prescription").contains("not an AI/LLM");
        assertThat(workout.durationMinutesRequested()).isEqualTo(30); // default
        assertThat(workout.warmup()).hasSize(2);
        assertThat(workout.cooldown()).hasSize(2);
        assertThat(workout.intensityAdjustment()).isNull();

        // Never calls the training-load collaborator when accountId is absent.
        assertThat(mockingDetails(trainingLoadService).getInvocations()).isEmpty();
    }

    @Test
    void sameRequest_producesIdenticalPlan_realDeterminism() {
        WorkoutGenerationRequest request = new WorkoutGenerationRequest(
                Goal.STRENGTH, Set.of(Equipment.DUMBBELLS), Set.of(Limitation.KNEE), 45, null);

        GeneratedWorkout first = service().generate(request);
        GeneratedWorkout second = service().generate(request);

        assertThat(second.warmup()).isEqualTo(first.warmup());
        assertThat(second.main()).isEqualTo(first.main());
        assertThat(second.cooldown()).isEqualTo(first.cooldown());
        assertThat(second.durationMinutesEstimated()).isEqualTo(first.durationMinutesEstimated());
        assertThat(second.goal()).isEqualTo(first.goal());
    }

    @Test
    void strengthGoal_appliesStrengthIntensityTemplate() {
        GeneratedWorkout workout = service().generate(
                new WorkoutGenerationRequest(Goal.STRENGTH, null, null, 30, null));

        assertThat(workout.main()).isNotEmpty();
        for (WorkoutExercise exercise : workout.main()) {
            assertThat(exercise.sets()).isEqualTo(4);
            assertThat(exercise.repsOrDuration()).isEqualTo("6-8 reps");
            assertThat(exercise.restSeconds()).isEqualTo(90);
        }
    }

    @Test
    void endurance_appliesEnduranceIntensityTemplate() {
        GeneratedWorkout workout = service().generate(
                new WorkoutGenerationRequest(Goal.ENDURANCE, null, null, 30, null));

        for (WorkoutExercise exercise : workout.main()) {
            assertThat(exercise.sets()).isEqualTo(3);
            assertThat(exercise.repsOrDuration()).isEqualTo("15-20 reps");
            assertThat(exercise.restSeconds()).isEqualTo(30);
        }
    }

    @Test
    void equipmentAndLimitations_expandAndFilterCandidatePool() {
        // Bodyweight strength exercises with SHOULDER/WRIST contraindications
        // (Push-Up, Plank, Pike Push-Up) are excluded; the remaining
        // bodyweight candidates (Bodyweight Squat, Walking Lunge, Glute
        // Bridge) fill 3 of the 4 default-duration main slots, and the 4th
        // is backfilled by the first available dumbbell exercise in library
        // order (Dumbbell Goblet Squat) since DUMBBELLS was supplied.
        GeneratedWorkout workout = service().generate(new WorkoutGenerationRequest(
                Goal.STRENGTH, Set.of(Equipment.DUMBBELLS),
                Set.of(Limitation.SHOULDER, Limitation.WRIST), 30, null));

        assertThat(workout.main()).hasSize(4);
        assertThat(workout.main().stream().map(WorkoutExercise::name)).containsExactly(
                "Bodyweight Squat", "Walking Lunge", "Glute Bridge", "Dumbbell Goblet Squat");
        assertThat(workout.main()).noneMatch(e ->
                e.name().equals("Push-Up") || e.name().equals("Plank") || e.name().equals("Pike Push-Up"));
    }

    @Test
    void notEnoughMatchingExercises_reportsHonestShortfall_neverPadsOrFabricates() {
        // Mobility/recovery bodyweight-only pool with every limitation
        // stated leaves very few candidates; at a long duration the main
        // block genuinely can't be filled to the computed target.
        GeneratedWorkout workout = service().generate(new WorkoutGenerationRequest(
                Goal.MOBILITY_RECOVERY, null,
                Set.of(Limitation.KNEE, Limitation.SHOULDER, Limitation.LOWER_BACK, Limitation.WRIST), 90, null));

        // Bodyweight MOBILITY_RECOVERY candidates with none of those four
        // contraindications: none (every bodyweight entry in that goal is
        // tagged with at least one of KNEE/SHOULDER/LOWER_BACK) -> honest
        // empty main block, not a fabricated one.
        assertThat(workout.main()).isEmpty();
        assertThat(workout.limitations()).anyMatch(l -> l.contains("Only found 0 of the usual"));
    }

    @Test
    void emptyButNonNullEquipmentAndLimitations_doNotThrow() {
        // A JSON client sending "equipment": [] / "limitations": [] produces
        // an empty (not null) Set — EnumSet.copyOf throws on an empty
        // non-EnumSet collection, so this must be handled as "none stated",
        // same as null, rather than blowing up the request.
        GeneratedWorkout workout = service().generate(new WorkoutGenerationRequest(
                Goal.GENERAL_FITNESS, Set.of(), Set.of(), 30, null));

        assertThat(workout.main()).isNotEmpty();
        assertThat(workout.equipmentConsidered()).isEmpty();
        assertThat(workout.injuryLimitationsConsidered()).isEmpty();
    }

    @Test
    void highTrainingLoad_lightensThePlan_withRealCitedAcwr() {
        when(trainingLoadService.computeSummary(1L)).thenReturn(new TrainingLoadSummary(
                120.0, 80.0, 1.8, "high", "trainingload-v1", "medium", List.of()));

        GeneratedWorkout baseline = service().generate(
                new WorkoutGenerationRequest(Goal.STRENGTH, null, null, 30, null));
        GeneratedWorkout adjusted = service().generate(
                new WorkoutGenerationRequest(Goal.STRENGTH, null, null, 30, 1L));

        assertThat(adjusted.main()).hasSizeLessThan(baseline.main().size());
        assertThat(adjusted.main()).allMatch(e -> e.sets() == baseline.main().get(0).sets() - 1);
        assertThat(adjusted.intensityAdjustment()).isNotNull();
        assertThat(adjusted.intensityAdjustment()).contains("high").contains("1.80");
    }

    @Test
    void elevatedTrainingLoad_doesNotChangeThePlan() {
        when(trainingLoadService.computeSummary(1L)).thenReturn(new TrainingLoadSummary(
                100.0, 80.0, 1.25, "elevated", "trainingload-v1", "medium", List.of()));

        GeneratedWorkout baseline = service().generate(
                new WorkoutGenerationRequest(Goal.STRENGTH, null, null, 30, null));
        GeneratedWorkout withAccount = service().generate(
                new WorkoutGenerationRequest(Goal.STRENGTH, null, null, 30, 1L));

        assertThat(withAccount.main()).isEqualTo(baseline.main());
        assertThat(withAccount.intensityAdjustment()).isNull();
    }

    @Test
    void trainingLoadLookupFailure_degradesGracefully_noFabricatedAdjustment() {
        when(trainingLoadService.computeSummary(1L)).thenThrow(new RuntimeException("no data"));

        GeneratedWorkout workout = service().generate(
                new WorkoutGenerationRequest(Goal.STRENGTH, null, null, 30, 1L));

        assertThat(workout.intensityAdjustment()).isNull();
        assertThat(workout.main()).isNotEmpty();
    }

    @Test
    void durationIsClampedIntoSaneRange() {
        GeneratedWorkout tooShort = service().generate(
                new WorkoutGenerationRequest(Goal.GENERAL_FITNESS, null, null, 1, null));
        GeneratedWorkout tooLong = service().generate(
                new WorkoutGenerationRequest(Goal.GENERAL_FITNESS, null, null, 500, null));

        assertThat(tooShort.durationMinutesRequested()).isEqualTo(10);
        assertThat(tooLong.durationMinutesRequested()).isEqualTo(90);
        assertThat(tooLong.main().size()).isGreaterThan(tooShort.main().size());
    }

    @Test
    void durationMinutesEstimated_reflectsActualSelectedCounts() {
        GeneratedWorkout workout = service().generate(
                new WorkoutGenerationRequest(Goal.GENERAL_FITNESS, null, null, 30, null));

        int expected = workout.warmup().size() * 2 + workout.main().size() * 6 + workout.cooldown().size() * 2;
        assertThat(workout.durationMinutesEstimated()).isEqualTo(expected);
    }
}
