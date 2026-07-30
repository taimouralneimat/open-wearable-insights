package com.openwearableinsights.api.journal.domain;

/**
 * A specific logged behavior's consecutive-day streak — the "don't break
 * the chain" mechanic. {@code currentStreak} is 0 once a day is missed,
 * even if {@code longestStreak} was much higher before.
 *
 * @param lastLoggedDate ISO date (yyyy-MM-dd) of the most recent entry
 */
public record HabitStreak(
        String category,
        String behavior,
        int currentStreak,
        int longestStreak,
        String lastLoggedDate
) {}
