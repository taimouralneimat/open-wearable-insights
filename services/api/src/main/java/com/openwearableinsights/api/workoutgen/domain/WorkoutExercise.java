package com.openwearableinsights.api.workoutgen.domain;

/**
 * One exercise within a generated workout block, in performed order.
 *
 * @param name           curated exercise name, from {@code
 *                        application.WorkoutExerciseLibrary} — never free
 *                        text, unlike {@code strength.domain.
 *                        StrengthExercise#exerciseName}, since this is a
 *                        suggestion drawn from a fixed catalog, not a user's
 *                        own log entry
 * @param sets           number of sets, already adjusted for any real
 *                        recent-training-load intensity reduction — see
 *                        {@code GeneratedWorkout#intensityAdjustment}
 * @param repsOrDuration plain-language target, e.g. {@code "6-8 reps"} or
 *                        {@code "30-45 sec hold"} — deliberately a
 *                        description, not a single number, since this is a
 *                        template suggestion, not a measured prescription
 * @param restSeconds    rest between sets
 * @param equipment      the equipment this specific exercise uses
 */
public record WorkoutExercise(
        String name,
        int sets,
        String repsOrDuration,
        int restSeconds,
        Equipment equipment
) {}
