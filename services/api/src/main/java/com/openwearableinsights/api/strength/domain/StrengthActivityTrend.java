package com.openwearableinsights.api.strength.domain;

import java.util.List;

/**
 * The full "Strength Activity Time" trend response for one window.
 *
 * @param window            {@code "weekly"} (last 7 days, daily buckets),
 *                          {@code "monthly"} (last 30 days, weekly buckets),
 *                          or {@code "sixmonth"} (last 180 days, monthly
 *                          buckets) — see {@code StrengthTrainingService}
 * @param points            trend buckets, oldest first
 * @param weeklyGoalMinutes the account's optional weekly strength-minutes
 *                          goal (see {@code accounts.weekly_strength_minutes_goal}),
 *                          or {@code null} if unset — always the same
 *                          weekly figure regardless of {@code window}; the
 *                          client compares it against the most recent
 *                          period(s) itself
 * @param limitations       plain-language caveats about what this trend
 *                          does and doesn't capture (see class Javadoc on
 *                          {@code StrengthTrainingService})
 */
public record StrengthActivityTrend(
        String window,
        List<StrengthActivityTrendPoint> points,
        Integer weeklyGoalMinutes,
        List<String> limitations
) {}
