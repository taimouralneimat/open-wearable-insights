/**
 * Workout generator application services — {@code WorkoutGeneratorService}
 * (the deterministic template engine) and {@code WorkoutExerciseLibrary}
 * (the curated exercise catalog it selects from). Not exposed as a Spring
 * Modulith {@code @NamedInterface}: nothing outside this module depends on
 * these types today. Depends on {@code trainingload.application} (already a
 * {@code @NamedInterface} from earlier work this session) for the real
 * ACWR-based training-load check — see {@code WorkoutGeneratorService}'s
 * class Javadoc "Training-load awareness".
 */
package com.openwearableinsights.api.workoutgen.application;
