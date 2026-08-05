package com.openwearableinsights.api.workoutgen.domain;

/**
 * Curated, not exhaustive, set of physical limitations/areas of concern the
 * generator can filter exercises against. Deliberately a fixed, structured
 * list (not free text like {@code strength.domain.StrengthExercise}'s
 * {@code exerciseName}) — filtering requires matching against each curated
 * exercise's own {@code contraindications} tagging, which only works against
 * a shared, finite vocabulary, unlike a free-text log entry that's never
 * mechanically compared to anything else.
 *
 * <p>Selecting none of these values (an empty set) means "no known
 * limitation to work around" — this is honestly not the same as a medical
 * clearance, and every generated workout's disclaimer says so.
 */
public enum Limitation {
    KNEE,
    SHOULDER,
    LOWER_BACK,
    WRIST
}
