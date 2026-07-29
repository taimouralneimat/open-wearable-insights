package com.openwearableinsights.api.activities.domain;

import java.util.List;

/**
 * A discrete workout/activity session (e.g. a run or ride) — distinct from
 * {@link ActivitySummary}, which is a day-level step rollup. Parsed from
 * source-device session data (FIT SessionMesg via GarminFitConnector; see
 * ADR-0006) and stored in the {@code activities} table.
 *
 * @param avgPaceSecPerKm derived from avgSpeedMps, not stored raw — null
 *                        when speed is zero/unavailable, so the UI never
 *                        divides by zero or shows a fabricated pace
 */
public record ActivitySession(
        Long id,
        String sport,
        String startTime,
        String endTime,
        double durationSeconds,
        Double distanceMeters,
        Double avgSpeedMps,
        Double maxSpeedMps,
        Double avgPaceSecPerKm,
        Integer avgHeartRate,
        Integer maxHeartRate,
        Integer calories,
        List<Double> hrZoneSeconds
) {}
