package com.openwearableinsights.api.workoutgen.application;

import com.openwearableinsights.api.workoutgen.domain.Equipment;
import com.openwearableinsights.api.workoutgen.domain.ExerciseCategory;
import com.openwearableinsights.api.workoutgen.domain.Goal;
import com.openwearableinsights.api.workoutgen.domain.Limitation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The curated, versioned exercise catalog {@link WorkoutGeneratorService}
 * selects from — this app's own hand-picked list, not a reproduction of any
 * vendor's proprietary exercise database and not an exhaustive one. Same
 * "curated suggestions, not an enforced/exhaustive taxonomy" spirit as
 * {@code journal.application.JournalService}'s behavior categories and
 * {@code strength.domain.StrengthExercise}'s free-text convention — except
 * here the list has to be structured (name + category + goals + equipment +
 * contraindications), not free text, because the generator mechanically
 * filters and selects from it. See {@link #LIBRARY_VERSION}.
 *
 * <p><b>Warmup and cooldown entries are goal-agnostic</b> ({@code goals =
 * EnumSet.allOf(Goal.class)}) — a dynamic-mobility warmup or a static
 * cooldown stretch is reasonable ahead of/after any of the four goals this
 * generator supports, so goal filtering only meaningfully narrows the MAIN
 * block.
 *
 * <p><b>Contraindications are this app's own conservative, documented
 * judgment</b> (e.g. excluding jumping movements for a stated knee
 * limitation, excluding overhead pressing for a stated shoulder limitation)
 * — not a clinical or physical-therapy assessment. See {@code
 * GeneratedWorkout#disclaimer}.
 */
final class WorkoutExerciseLibrary {

    /** Bumped whenever an exercise is added, removed, or re-tagged. */
    static final String LIBRARY_VERSION = "workoutgen-library-v1";

    private WorkoutExerciseLibrary() {}

    private static final Set<Goal> ALL_GOALS = EnumSet.allOf(Goal.class);
    private static final Set<Limitation> NONE = Set.of();

    /**
     * Declaration order is the selection order (see {@code
     * WorkoutGeneratorService#selectCandidates}) — insertion order is
     * preserved by {@link List#of}, and selection never reorders or
     * shuffles, which is what makes the same request always return the same
     * workout.
     */
    static final List<ExerciseDefinition> LIBRARY = List.of(

            // --- Warmup (goal-agnostic) ---
            new ExerciseDefinition("Arm Circles", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Cat-Cow Stretch", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, NONE),
            new ExerciseDefinition("Leg Swings", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Bodyweight Squat to Stand", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE, Limitation.LOWER_BACK)),
            new ExerciseDefinition("Walking High Knees", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Wrist Circles and Stretch", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.WRIST)),
            new ExerciseDefinition("Standing Torso Twists", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Jumping Jacks", ExerciseCategory.WARMUP, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE, Limitation.SHOULDER)),

            // --- Main: strength, bodyweight ---
            new ExerciseDefinition("Push-Up", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.BODYWEIGHT, Set.of(Limitation.SHOULDER, Limitation.WRIST)),
            new ExerciseDefinition("Bodyweight Squat", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Walking Lunge", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Plank", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.BODYWEIGHT, Set.of(Limitation.LOWER_BACK, Limitation.WRIST)),
            new ExerciseDefinition("Glute Bridge", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.BODYWEIGHT, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Pike Push-Up", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.BODYWEIGHT, Set.of(Limitation.SHOULDER, Limitation.WRIST)),

            // --- Main: strength, dumbbells ---
            new ExerciseDefinition("Dumbbell Goblet Squat", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.DUMBBELLS, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Dumbbell Romanian Deadlift", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.DUMBBELLS, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Dumbbell Bench Press", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.DUMBBELLS, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Dumbbell Row", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.DUMBBELLS, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Dumbbell Shoulder Press", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.DUMBBELLS, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Dumbbell Bicep Curl", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.DUMBBELLS, Set.of(Limitation.WRIST)),

            // --- Main: strength, kettlebell ---
            new ExerciseDefinition("Kettlebell Goblet Squat", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.KETTLEBELL, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Kettlebell Deadlift", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.KETTLEBELL, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Kettlebell Swing", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.ENDURANCE, Goal.GENERAL_FITNESS),
                    Equipment.KETTLEBELL, Set.of(Limitation.LOWER_BACK, Limitation.SHOULDER)),

            // --- Main: strength, barbell ---
            new ExerciseDefinition("Barbell Back Squat", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.BARBELL, Set.of(Limitation.KNEE, Limitation.LOWER_BACK)),
            new ExerciseDefinition("Barbell Deadlift", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.BARBELL, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Barbell Bench Press", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.BARBELL, Set.of(Limitation.SHOULDER, Limitation.WRIST)),
            new ExerciseDefinition("Barbell Overhead Press", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.BARBELL, Set.of(Limitation.SHOULDER)),

            // --- Main: strength, resistance band ---
            new ExerciseDefinition("Band Pull-Apart", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.RESISTANCE_BAND, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Band Squat", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.RESISTANCE_BAND, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Band Row", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH, Goal.GENERAL_FITNESS),
                    Equipment.RESISTANCE_BAND, Set.of(Limitation.LOWER_BACK)),

            // --- Main: strength, full gym ---
            new ExerciseDefinition("Leg Press Machine", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.FULL_GYM, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Lat Pulldown Machine", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.FULL_GYM, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Chest Press Machine", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.FULL_GYM, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Seated Row Machine", ExerciseCategory.MAIN, EnumSet.of(Goal.STRENGTH),
                    Equipment.FULL_GYM, Set.of(Limitation.LOWER_BACK)),

            // --- Main: endurance ---
            new ExerciseDefinition("Jump Squats", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Mountain Climbers", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE, Goal.GENERAL_FITNESS),
                    Equipment.BODYWEIGHT, Set.of(Limitation.WRIST, Limitation.SHOULDER)),
            new ExerciseDefinition("Burpees", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE, Limitation.SHOULDER, Limitation.WRIST)),
            new ExerciseDefinition("Step-Ups", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE, Goal.GENERAL_FITNESS),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Dumbbell Thrusters", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE),
                    Equipment.DUMBBELLS, Set.of(Limitation.KNEE, Limitation.SHOULDER)),
            new ExerciseDefinition("Renegade Rows", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE),
                    Equipment.DUMBBELLS, Set.of(Limitation.WRIST, Limitation.SHOULDER)),
            new ExerciseDefinition("Rowing Machine Intervals", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE),
                    Equipment.FULL_GYM, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Stationary Bike Intervals", ExerciseCategory.MAIN, EnumSet.of(Goal.ENDURANCE),
                    Equipment.FULL_GYM, Set.of(Limitation.KNEE)),

            // --- Main: mobility / recovery ---
            new ExerciseDefinition("World's Greatest Stretch", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Hip Flexor Stretch", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Thoracic Spine Rotation", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.BODYWEIGHT, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Standing Hamstring Stretch", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.BODYWEIGHT, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Seated Figure-4 Stretch", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Wall Shoulder Stretch", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.BODYWEIGHT, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Ankle Mobility Drill", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Band-Assisted Hamstring Stretch", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.RESISTANCE_BAND, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Band Shoulder Dislocates", ExerciseCategory.MAIN, EnumSet.of(Goal.MOBILITY_RECOVERY),
                    Equipment.RESISTANCE_BAND, Set.of(Limitation.SHOULDER, Limitation.WRIST)),

            // --- Cooldown (goal-agnostic) ---
            new ExerciseDefinition("Standing Quad Stretch", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Seated Forward Fold", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Cross-Body Shoulder Stretch", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.SHOULDER)),
            new ExerciseDefinition("Downward Dog Stretch", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.SHOULDER, Limitation.WRIST)),
            new ExerciseDefinition("Figure-4 Glute Stretch", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.KNEE)),
            new ExerciseDefinition("Supine Spinal Twist", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.LOWER_BACK)),
            new ExerciseDefinition("Wrist Flexor Stretch", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, Set.of(Limitation.WRIST)),
            new ExerciseDefinition("Box Breathing (deep breathing)", ExerciseCategory.COOLDOWN, ALL_GOALS,
                    Equipment.BODYWEIGHT, NONE)
    );

    /**
     * One curated exercise's tags. Package-private — only {@link
     * WorkoutGeneratorService} reads this catalog; nothing outside this
     * module needs it.
     */
    record ExerciseDefinition(
            String name,
            ExerciseCategory category,
            Set<Goal> goals,
            Equipment requiredEquipment,
            Set<Limitation> contraindications
    ) {}
}
