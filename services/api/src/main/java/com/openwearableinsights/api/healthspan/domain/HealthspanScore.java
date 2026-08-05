package com.openwearableinsights.api.healthspan.domain;

import java.time.Instant;
import java.util.List;

/**
 * A versioned, deterministic composite wellness score for one window (30-day
 * or 6-month) — see {@code healthspan.application.HealthspanService} for the
 * full, disclosed methodology and exactly which real inputs feed it.
 *
 * <p>{@code score} is {@code null} whenever there isn't enough real history
 * across enough of the contributing metrics for this specific window — an
 * honest "not enough history yet" state (see {@code
 * HealthspanService#emptyScore}), never a fabricated or partial number.
 *
 * @param score                0-100, or {@code null} if not enough real
 *                              history yet for this window
 * @param window                {@code "30day"} or {@code "6month"}
 * @param algorithmVersion      versioned identifier for this composite's
 *                              methodology
 * @param confidence            "none" (no score), "low", or "medium" — this
 *                              composite is never "high" confidence,
 *                              regardless of inputs; see {@code
 *                              HealthspanService} class Javadoc "Confidence"
 * @param factors               every contributing metric that had enough
 *                              real data, with its own value, comparison,
 *                              weight, and contribution — factors without
 *                              enough real data are omitted here and named
 *                              in {@code missingDataTreatment} instead,
 *                              never zero-filled
 * @param missingDataTreatment  plain-language statement of which factors (if
 *                              any) were excluded and why
 * @param methodology           this app's own composite formula, plainly
 *                              stated — not a reproduction of any vendor's
 *                              proprietary biological-age formula and not
 *                              developed with or validated by any external
 *                              research institute
 * @param disclaimer            always present, verbatim: a wellness
 *                              estimate, not a medical or actuarial age
 * @param limitations           honest, user-facing caveats
 * @param computedAt            when this score was computed (computed on
 *                              demand, like every other analytics endpoint
 *                              in this app — no stored snapshot)
 */
public record HealthspanScore(
        Integer score,
        String window,
        String algorithmVersion,
        String confidence,
        List<HealthspanFactor> factors,
        String missingDataTreatment,
        String methodology,
        String disclaimer,
        List<String> limitations,
        Instant computedAt
) {
    public HealthspanScore {
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("Score must be 0-100, got: " + score);
        }
    }
}
