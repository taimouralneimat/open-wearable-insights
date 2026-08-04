package com.openwearableinsights.api.strength.domain;

import java.util.List;

/**
 * One exercise within a logged {@link StrengthWorkout}, with its sets in
 * performed order. {@code exerciseName} is free text — no canonical
 * exercise taxonomy, same "curated suggestions, not an enforced list"
 * convention as {@code journal_entries.behavior} and
 * {@code BiomarkerCsvParser}'s biomarker names.
 */
public record StrengthExercise(
        String exerciseName,
        List<StrengthSet> sets
) {}
