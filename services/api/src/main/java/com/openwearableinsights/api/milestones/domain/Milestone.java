package com.openwearableinsights.api.milestones.domain;

import java.time.Instant;

/**
 * One genuine personal record — a real comparison between the account's own
 * current computed value and its own real historical values. Never
 * fabricated: {@code value} and {@code previousBest} are always two real
 * numbers already computed elsewhere in this app (see {@code
 * MilestoneService} for exactly which source and comparison backs each
 * {@code type}).
 *
 * @param type         a short, stable machine identifier for which kind of
 *                      milestone this is (e.g. {@code "habit_streak"},
 *                      {@code "steps_week"}, {@code "vo2max_month"},
 *                      {@code "strength_period"}) — see {@code
 *                      MilestoneService} for the full list
 * @param title         short, celebratory, human-readable label
 * @param description   a full sentence stating the real achieved value and
 *                      (when one exists) the real prior-best value it beats,
 *                      with no LLM involvement — plain deterministic
 *                      templating, same convention as {@code
 *                      DeterministicInsightEngine}
 * @param value         the real, current achieved value
 * @param previousBest  the real prior-best value this ties or beats, or
 *                      {@code null} when this milestone type doesn't have a
 *                      distinct prior number to show (see {@code
 *                      MilestoneService}'s habit-streak check, where
 *                      "current streak equals longest streak" is itself the
 *                      whole record — there is no separate, distinct
 *                      second-best streak length this app tracks)
 * @param unit          unit for {@code value}/{@code previousBest} (e.g.
 *                      {@code "days"}, {@code "steps/day"},
 *                      {@code "mL/kg/min"}, {@code "minutes"})
 * @param period        human-readable label for which real period/date this
 *                      milestone covers (e.g. {@code "week of 2026-08-25"},
 *                      {@code "2026-08"})
 * @param detectedAt    when this check ran (not when the record was
 *                      actually set — this module doesn't persist history,
 *                      see class Javadoc on {@code MilestoneService})
 */
public record Milestone(
        String type,
        String title,
        String description,
        double value,
        Double previousBest,
        String unit,
        String period,
        Instant detectedAt
) {}
