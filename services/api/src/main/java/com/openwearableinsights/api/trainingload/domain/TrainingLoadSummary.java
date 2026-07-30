package com.openwearableinsights.api.trainingload.domain;

import java.util.List;

/**
 * Current training-load snapshot: acute (7d) / chronic (28d) average daily
 * load and the derived ACWR (acute:chronic workload ratio).
 *
 * <p>{@code acwr} is computed with the exact same formula
 * {@code readiness.application.ReadinessCalculator} uses for the "Training
 * load (ACWR)" factor ({@code acuteLoad / chronicLoad}) — this endpoint does
 * not invent a separate ratio.
 *
 * <p>{@code acuteLoad}/{@code chronicLoad}/{@code acwr} are {@code null} when
 * there isn't at least one activity in both the 7-day and 28-day windows —
 * the same presence check {@code ReadinessCalculator} uses to decide whether
 * to include the ACWR factor in the readiness score at all, so "no data
 * here" always lines up with "factor excluded there".
 *
 * @param loadStatus  a plain-language wellness heuristic ("unknown", "low",
 *                     "optimal", "elevated", "high") derived from
 *                     {@code acwr} — see
 *                     {@code TrainingLoadService#classifyLoadStatus} for the
 *                     documented thresholds. Not a medical or clinical
 *                     assessment; see {@code limitations}.
 * @param confidence   "none" when there's no data to compute a ratio,
 *                     "medium" otherwise — this is a single-source figure
 *                     (one metric, one table), so it never claims "high".
 */
public record TrainingLoadSummary(
        Double acuteLoad,
        Double chronicLoad,
        Double acwr,
        String loadStatus,
        String algorithmVersion,
        String confidence,
        List<String> limitations
) {}
