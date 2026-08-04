package com.openwearableinsights.api.vo2max.domain;

/**
 * One month's VO2max estimate for the trend view (see
 * {@code Vo2MaxService#computeTrend}). Computed independently per calendar
 * month from that month's own real activity max-heart-rate and average
 * resting heart rate — months with insufficient real data for either input
 * are simply omitted, never zero-filled or interpolated.
 *
 * @param month  calendar month this point covers, formatted {@code yyyy-MM}
 * @param vo2Max estimated VO2max in mL/kg/min for that month
 */
public record Vo2MaxTrendPoint(
        String month,
        double vo2Max
) {}
