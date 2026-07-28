package com.openwearableinsights.api.readiness.domain;

import java.util.Map;
import java.util.Optional;

/**
 * A personalized rolling baseline computed from the user own measurement history.
 * Replaces the Phase 1 hardcoded/provisional baseline with real per-user baselines.
 * Sample size and confidence are exposed honestly.
 */
public record PersonalBaseline(
        Map<String, Double> metricBaselines,
        Map<String, Integer> sampleSizes,
        int baselineDays,
        String confidence,
        String windowDescription
) {
    public Optional<Double> baselineFor(String metricType) {
        return Optional.ofNullable(metricBaselines.get(metricType));
    }

    public int sampleSizeFor(String metricType) {
        return sampleSizes.getOrDefault(metricType, 0);
    }

    public boolean isProvisional() {
        return baselineDays < 7;
    }
}
