package com.openwearableinsights.api.connections.domain;

import java.time.Instant;

/**
 * A single measurement extracted from a wearable data source, in the same
 * canonical shape the `measurements` table already uses (see V01 schema):
 * metric_type/value/unit/time. Every connector produces these regardless
 * of the vendor-specific format it read.
 */
public record ParsedMeasurement(
        Instant time,
        String metricType,
        double value,
        String unit
) {}
