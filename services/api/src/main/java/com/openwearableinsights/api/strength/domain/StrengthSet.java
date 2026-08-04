package com.openwearableinsights.api.strength.domain;

/**
 * A single logged set within a {@link StrengthExercise} — the rep-level
 * detail {@code activities} has no columns for (see V09 migration).
 *
 * @param setOrder position of this set within the whole workout (1-based,
 *                 across all exercises, in the order they were logged) —
 *                 lets the UI show sets in the order they were performed
 * @param reps     repetitions performed, always required
 * @param weightKg external load used, or {@code null} for a bodyweight set
 *                 — never a fabricated 0, which would misrepresent a
 *                 bodyweight exercise as "0 kg"
 */
public record StrengthSet(
        int setOrder,
        int reps,
        Double weightKg
) {}
