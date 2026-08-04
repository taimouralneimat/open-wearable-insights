package com.openwearableinsights.api.strength.domain;

import java.util.List;

/**
 * A manually-logged strength workout, as returned to API clients.
 *
 * @param startedAt         ISO-8601 instant the workout began (user-supplied,
 *                          defaults to server time-of-logging if omitted)
 * @param userDurationMinutes the duration the user actually entered, or
 *                          {@code null} if they didn't provide one
 * @param durationMinutes   the effective duration used everywhere this
 *                          workout feeds a total (e.g. the strength-time
 *                          trend): {@code userDurationMinutes} when present,
 *                          otherwise {@link #durationIsEstimated} is
 *                          {@code true} and this is
 *                          {@code StrengthTrainingService}'s set-count
 *                          estimate — never a fabricated measurement
 * @param durationIsEstimated whether {@code durationMinutes} is a real
 *                          user-reported duration ({@code false}) or an
 *                          estimate derived from set count ({@code true})
 * @param note              optional free-text note
 * @param exercises         logged exercises in performed order
 */
public record StrengthWorkout(
        Long id,
        String startedAt,
        Integer userDurationMinutes,
        int durationMinutes,
        boolean durationIsEstimated,
        String note,
        List<StrengthExercise> exercises
) {}
