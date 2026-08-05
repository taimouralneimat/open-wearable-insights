package com.openwearableinsights.api.workoutgen.domain;

/**
 * Curated, not exhaustive, equipment categories the exercise library tags
 * exercises against. {@code BODYWEIGHT} is always treated as available
 * regardless of what a request selects — see {@code
 * application.WorkoutGeneratorService#isAvailable} — since no equipment is
 * ever a valid real-world constraint, not an error state.
 */
public enum Equipment {
    BODYWEIGHT,
    DUMBBELLS,
    KETTLEBELL,
    BARBELL,
    RESISTANCE_BAND,
    FULL_GYM
}
