package com.openwearableinsights.api.milestones.domain;

import java.util.List;

/**
 * The full milestones response. {@code milestones} is an honest empty list
 * — never fabricated filler — when no genuine new personal record was found
 * this check.
 *
 * @param milestones       real milestones found this check, most novel type
 *                          first (see {@code MilestoneService} for the fixed
 *                          check order) — empty when nothing genuinely new
 * @param algorithmVersion versioned, deterministic — see {@code
 *                          MilestoneService#ALGORITHM_VERSION}
 * @param limitations       plain-language caveats about what this does and
 *                          doesn't cover — see {@code MilestoneService}'s
 *                          class Javadoc
 */
public record MilestonesResponse(
        List<Milestone> milestones,
        String algorithmVersion,
        List<String> limitations
) {}
