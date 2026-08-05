package com.openwearableinsights.api.healthspan.domain;

/**
 * A single metric's contribution to a {@link HealthspanScore} — same
 * transparency shape as {@code readiness.domain.FactorContribution}
 * (every score in this app cites its own inputs, never a black box; see
 * {@code readiness.application.ReadinessCalculator} class Javadoc), adapted
 * for this composite's own "recent vs. your own prior history" comparison
 * style rather than a single-day baseline deviation.
 *
 * @param name        plain-language factor name (e.g. "VO2max trend")
 * @param value        the real underlying value this factor is based on
 *                      (units vary by factor — see {@code unit})
 * @param unit         unit of {@code value}
 * @param comparison   plain-language description of what this factor's
 *                      real recent data was compared against and what was
 *                      found (never a fabricated comparison)
 * @param direction     FAVORABLE (improves the composite), UNFAVORABLE
 *                      (reduces it), or NEUTRAL
 * @param weight        this factor's weight in the composite (0-1); see
 *                      {@code HealthspanService} class Javadoc for why
 *                      each weight was chosen
 * @param contribution signed contribution to the 0-100 score
 *                      ({@code weight * rawScore})
 * @param confidence   this specific factor's own honest confidence
 *                      ("none"/"low"/"medium" — never "high", same
 *                      discipline as every other estimate in this app)
 * @param source       which module/service this factor's real data came
 *                      from
 */
public record HealthspanFactor(
        String name,
        Double value,
        String unit,
        String comparison,
        Direction direction,
        double weight,
        double contribution,
        String confidence,
        String source
) {
    public enum Direction {
        FAVORABLE, UNFAVORABLE, NEUTRAL
    }
}
