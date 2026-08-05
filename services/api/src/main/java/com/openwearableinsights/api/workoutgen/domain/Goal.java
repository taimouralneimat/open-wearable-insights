package com.openwearableinsights.api.workoutgen.domain;

/**
 * Curated, not exhaustive, set of stated training goals the generator can
 * target — same "small, hand-picked list" convention {@code
 * insights.application.DeterministicInsightEngine}'s {@code SUGGESTIONS} map
 * uses, rather than free text the generator couldn't reliably act on.
 */
public enum Goal {
    STRENGTH,
    ENDURANCE,
    MOBILITY_RECOVERY,
    GENERAL_FITNESS
}
