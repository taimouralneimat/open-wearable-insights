package com.openwearableinsights.api.sleep.domain;

/**
 * A single day's sleep summary within a trend view.
 */
public record SleepTrendPoint(
        String date,
        double totalHours,
        double deepHours,
        double remHours,
        double lightHours,
        double awakeHours,
        int sleepScore
) {}
