package com.openwearableinsights.api.readiness.domain;

/**
 * A single factor's contribution to a readiness score.
 *
 * @param name        factor name (e.g., "HRV deviation")
 * @param value       the factor's value
 * @param unit        unit of the value
 * @param direction    positive (improves readiness), negative (reduces), neutral
 * @param contribution signed contribution to the score (e.g., +12 or -8)
 * @param source       data source (raw/vendor/app)
 */
public record FactorContribution(
        String name,
        double value,
        String unit,
        Direction direction,
        double contribution,
        String source
) {
    public enum Direction {
        POSITIVE, NEGATIVE, NEUTRAL
    }
}