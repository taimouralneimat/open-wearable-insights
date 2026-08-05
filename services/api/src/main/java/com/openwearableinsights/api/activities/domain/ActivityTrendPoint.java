package com.openwearableinsights.api.activities.domain;

/**
 * A single point within a step-count trend view. Calories/active minutes
 * aren't tracked in the current data model — see ActivitySummary.
 *
 * @param date        the bucket's start date (a real calendar day for
 *                     {@code granularity="day"}, or the Monday/1st for a
 *                     week/month bucket)
 * @param steps       for {@code granularity="day"}, that day's real step
 *                     count; for "week"/"month", the average steps/day
 *                     across the real days with data in that bucket (never
 *                     zero-filled) — see ActivityInsightService#computeStepTrends
 * @param granularity "day", "week", or "month" — disclosed explicitly so a
 *                     large window's averaged points are never mistaken for
 *                     a single real day's count
 */
public record ActivityTrendPoint(
        String date,
        int steps,
        String granularity
) {}
