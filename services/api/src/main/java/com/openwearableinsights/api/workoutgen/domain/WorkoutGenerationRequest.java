package com.openwearableinsights.api.workoutgen.domain;

import java.util.Set;

/**
 * Inputs to {@code application.WorkoutGeneratorService#generate}. Every
 * field is optional and defaulted by the service — see its class Javadoc —
 * so a bare request (no goal, no equipment, no limitations) still produces a
 * real, honest bodyweight general-fitness workout rather than an error.
 *
 * @param goal            stated training goal; defaults to {@link
 *                         Goal#GENERAL_FITNESS} when {@code null}
 * @param equipment        available equipment; {@link Equipment#BODYWEIGHT}
 *                         is always implicitly available regardless of this
 *                         set's contents (see {@code WorkoutGeneratorService
 *                         #isAvailable}); {@code null}/empty means
 *                         bodyweight-only
 * @param limitations      physical limitations/areas to work around;
 *                         {@code null}/empty means none stated
 * @param durationMinutes  target total session length; clamped into a
 *                          sane range and defaulted when {@code null} — see
 *                          {@code WorkoutGeneratorService}'s duration
 *                          constants
 * @param accountId        optional; when present, enables the real
 *                          recent-training-load-aware intensity check (see
 *                          {@code WorkoutGeneratorService}'s "Training-load
 *                          awareness" Javadoc section) — when {@code null},
 *                          the generator produces the same template output
 *                          with no training-load adjustment, never a
 *                          fabricated one
 */
public record WorkoutGenerationRequest(
        Goal goal,
        Set<Equipment> equipment,
        Set<Limitation> limitations,
        Integer durationMinutes,
        Long accountId
) {}
