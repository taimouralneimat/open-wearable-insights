package com.openwearableinsights.api.sleep.domain;

/**
 * A single point within a sleep trend view.
 *
 * @param date        the bucket's start date (a real calendar night for
 *                     {@code granularity="day"}, or the Monday/1st for a
 *                     week/month bucket)
 * @param granularity "day", "week", or "month" — for "week"/"month", every
 *                     numeric field is the average across the real nights
 *                     with data in that bucket (never zero-filled), so an
 *                     averaged figure is never mistaken for one real
 *                     night's value — see SleepInsightService#computeTrends
 */
public record SleepTrendPoint(
        String date,
        double totalHours,
        double deepHours,
        double remHours,
        double lightHours,
        double awakeHours,
        int sleepScore,
        String granularity
) {}
