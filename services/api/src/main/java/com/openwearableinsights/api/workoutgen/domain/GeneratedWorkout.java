package com.openwearableinsights.api.workoutgen.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A structured, deterministically-generated workout — the response of
 * {@code application.WorkoutGeneratorService#generate}.
 *
 * @param algorithmVersion         versioned, deterministic — see {@code
 *                                 WorkoutGeneratorService#ALGORITHM_VERSION}
 * @param source                   which engine produced this — always
 *                                 {@link WorkoutSource#DETERMINISTIC_TEMPLATE}
 *                                 today, see that enum's Javadoc
 * @param goal                     the (possibly defaulted) goal actually used
 * @param equipmentConsidered      the (possibly defaulted) equipment set
 *                                 actually used
 * @param injuryLimitationsConsidered the (possibly defaulted) limitation set
 *                                 actually used
 * @param durationMinutesRequested the (possibly defaulted/clamped) requested
 *                                 duration
 * @param durationMinutesEstimated the generator's own estimate of the actual
 *                                 plan's total time, from the fixed
 *                                 per-exercise minute constants — not a
 *                                 measurement, see {@code limitations}
 * @param warmup                   warmup block, in order
 * @param main                     main block, in order
 * @param cooldown                 cooldown block, in order
 * @param intensityAdjustment      non-null only when a real recent
 *                                 training-load signal caused this plan to
 *                                 be lightened — states what changed and why,
 *                                 citing the real ACWR loadStatus; {@code
 *                                 null} whenever no adjustment was made
 *                                 (including whenever training-load data
 *                                 wasn't available) — never a fabricated
 *                                 explanation
 * @param disclaimer               always present, verbatim — states this is
 *                                 a suggestion, not a prescription or medical/
 *                                 personal-training advice, and discloses the
 *                                 {@code source}. Same "always-present, real
 *                                 API field, not just documentation"
 *                                 convention as {@code healthspan.application.
 *                                 HealthspanService#DISCLAIMER}
 * @param limitations               disclosed caveats about this generator
 *                                 itself (curated-not-exhaustive library,
 *                                 fixed templates, filtering coverage) — same
 *                                 field name/convention as {@code
 *                                 TrainingLoadSummary#limitations}/{@code
 *                                 HealthspanScore#limitations}
 * @param generatedAt              when this was computed — always fresh, no
 *                                 stored/cached workouts (same "compute on
 *                                 demand" convention as {@code
 *                                 TrainingLoadService}/{@code
 *                                 HealthspanService})
 */
public record GeneratedWorkout(
        String algorithmVersion,
        WorkoutSource source,
        Goal goal,
        Set<Equipment> equipmentConsidered,
        Set<Limitation> injuryLimitationsConsidered,
        int durationMinutesRequested,
        int durationMinutesEstimated,
        List<WorkoutExercise> warmup,
        List<WorkoutExercise> main,
        List<WorkoutExercise> cooldown,
        String intensityAdjustment,
        String disclaimer,
        List<String> limitations,
        Instant generatedAt
) {}
