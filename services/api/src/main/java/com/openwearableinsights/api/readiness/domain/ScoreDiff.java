package com.openwearableinsights.api.readiness.domain;

import java.util.List;

/**
 * A structured diff between two readiness scores.
 *
 * Shows which factors moved, by how much, and in which direction.
 * This is the transparency feature that makes the product feel
 * explainable rather than a black-box number.
 *
 * @param todayScore       today readiness score
 * @param priorScore       prior day readiness score
 * @param scoreDelta       change in score (today - prior)
 * @param factorDiffs      per-factor changes (sorted by absolute impact)
 * @param summary          human-readable summary of what changed
 * @param biggestPositive  name of the factor that improved most (or null)
 * @param biggestNegative  name of the factor that worsened most (or null)
 * @param comparedAgainst  "yesterday" when compared against a real persisted
 *                         prior-day score, or "no_prior_data" when no prior
 *                         day's score has been recorded yet — never silently
 *                         substitutes a synthetic stand-in for a real day.
 * @param confidence       today's score confidence — a diff inherits the
 *                         uncertainty of the score it's built from, so this
 *                         isn't computed separately.
 */
public record ScoreDiff(
        int todayScore,
        int priorScore,
        int scoreDelta,
        List<FactorDiff> factorDiffs,
        String summary,
        String biggestPositive,
        String biggestNegative,
        String comparedAgainst,
        String confidence
) {
    /**
     * A single factor change between two scores.
     *
     * @param name         factor name
     * @param todayValue   today value
     * @param priorValue   prior value
     * @param valueDelta   change in value (today - prior)
     * @param todayContribution  today contribution to score
     * @param priorContribution  prior contribution to score
     * @param contributionDelta  change in contribution (today - prior)
     * @param direction    "improved", "worsened", or "unchanged"
     */
    public record FactorDiff(
            String name,
            double todayValue,
            double priorValue,
            double valueDelta,
            double todayContribution,
            double priorContribution,
            double contributionDelta,
            String direction
    ) {}
}
