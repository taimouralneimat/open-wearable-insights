package com.openwearableinsights.api.trainingload.domain;

/**
 * A single day's total training load (sum of that day's session loads —
 * see {@code TrainingLoadService#computeSessionLoad}). Only days with at
 * least one activity row appear; rest days are omitted rather than
 * zero-filled or interpolated (see {@code TrainingLoadService}'s
 * active-days-only averaging convention, which this mirrors).
 */
public record TrainingLoadTrendPoint(
        String date,
        double load
) {}
