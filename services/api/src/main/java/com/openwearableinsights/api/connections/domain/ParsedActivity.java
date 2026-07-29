package com.openwearableinsights.api.connections.domain;

import java.time.Instant;
import java.util.List;

/**
 * A single workout/activity session extracted from a wearable data source
 * (e.g. a FIT SessionMesg) — distinct from {@link ParsedMeasurement}, which
 * models continuous point-in-time telemetry rather than a bounded session
 * with a sport, duration, and aggregate stats.
 *
 * @param hrZoneSeconds seconds spent in each HR zone, index = zone number
 *                       (device-defined zone boundaries); empty if the
 *                       source file didn't report zone data
 */
public record ParsedActivity(
        Instant startTime,
        Instant endTime,
        String sport,
        double durationSeconds,
        Double distanceMeters,
        Double avgSpeedMps,
        Double maxSpeedMps,
        Integer avgHeartRate,
        Integer maxHeartRate,
        Integer calories,
        List<Double> hrZoneSeconds
) {}
