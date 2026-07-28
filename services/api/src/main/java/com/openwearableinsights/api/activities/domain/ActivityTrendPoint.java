package com.openwearableinsights.api.activities.domain;

/**
 * A single day's step count within a trend view. Calories/active minutes
 * aren't tracked in the current data model — see ActivitySummary.
 */
public record ActivityTrendPoint(
        String date,
        int steps
) {}
