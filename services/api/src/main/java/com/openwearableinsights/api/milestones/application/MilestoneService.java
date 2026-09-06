package com.openwearableinsights.api.milestones.application;

import com.openwearableinsights.api.activities.application.ActivityInsightService;
import com.openwearableinsights.api.activities.domain.ActivityTrendPoint;
import com.openwearableinsights.api.journal.application.JournalService;
import com.openwearableinsights.api.journal.domain.HabitStreak;
import com.openwearableinsights.api.milestones.domain.Milestone;
import com.openwearableinsights.api.milestones.domain.MilestonesResponse;
import com.openwearableinsights.api.strength.application.StrengthTrainingService;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrend;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrendPoint;
import com.openwearableinsights.api.vo2max.application.Vo2MaxService;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxTrendPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Detects genuine "Personal Records / Milestones" — an original, non-parity
 * delight feature, not a competitor-parity row. Every milestone this service
 * returns is a real comparison between an account's own current computed
 * value and its own real historical values, using data other modules in
 * this app already compute. Following this app's "never fabricate, say so
 * honestly" philosophy (see {@code Vo2MaxService}/{@code
 * HealthspanService}/{@code StrengthTrainingService}), an honest empty list
 * is returned whenever nothing genuinely new has happened recently — never
 * filled with generic, unearned praise.
 *
 * <p><b>No LLM involvement:</b> titles/descriptions are plain, deterministic
 * templating over real numbers, same convention as {@code
 * DeterministicInsightEngine} — never an invented claim about something that
 * didn't actually happen.
 *
 * <p><b>Stateless — no persistence of past milestones:</b> like {@code
 * HealthspanService}/{@code TrainingLoadService}, this composes other
 * modules' own existing query methods fresh on every request rather than
 * remembering which milestones were already shown. This is a deliberate,
 * documented tradeoff for a first version: a genuinely-still-true condition
 * (e.g. "you are still riding your own longest-ever streak for this habit")
 * can honestly reappear on more than one request rather than firing exactly
 * once — that is a real, true fact each time, not a fabricated repeat.
 * Building a "milestones already shown" table (to fire each one exactly
 * once) is a reasonable future enhancement, but every comparison here is
 * fully answerable from the other modules' existing, already-computed
 * outputs without it, so a first version does not need the extra storage
 * and staleness-tracking complexity.
 *
 * <p><b>Four milestone types, chosen for a clean, honest "this is genuinely
 * a new best" framing (see each check method's own Javadoc for the exact
 * comparison and thresholds):</b>
 * <ul>
 *   <li><b>Habit streak</b> ({@code habit_streak}) — reuses {@link
 *   JournalService#getStreaks}.</li>
 *   <li><b>Best tracked week of steps</b> ({@code steps_week}) — reuses
 *   {@link ActivityInsightService#computeStepTrends}.</li>
 *   <li><b>New VO2max high</b> ({@code vo2max_month}) — reuses {@link
 *   Vo2MaxService#computeTrend}.</li>
 *   <li><b>Most strength training in any tracked period</b> ({@code
 *   strength_period}) — reuses {@link StrengthTrainingService#computeTrend}.</li>
 * </ul>
 *
 * <p><b>Deliberately excluded — sleep consistency:</b> {@code
 * SleepInsightService#computeSleepConsistency} exposes a single fixed
 * 14-night rolling score, not a series of independent past periods to
 * compare against. Unlike a cumulative weekly/monthly total, "today's
 * consistency score" recomputed over a sliding window doesn't have a clean,
 * honest "new personal best" framing — the same 14 real nights mostly
 * overlap from one day to the next, so a marginal score change is really
 * the same rolling window shifting by a day, not a new distinct
 * achievement. Rather than force a misleading "record" onto a metric that
 * doesn't cleanly support one, this is left out of the first version.
 */
@Service
public class MilestoneService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);

    /** Versioned, deterministic — see class Javadoc for the full set of checks. */
    public static final String ALGORITHM_VERSION = "milestones-v1";

    // Habit streak: minimum meaningful "record" length. A 1- or 2-day streak
    // tying a 1- or 2-day "record" isn't a meaningful achievement — 5
    // consecutive days is long enough to represent a real, established
    // pattern rather than a coincidental couple of overlapping days, while
    // still being reachable soon after an account starts logging a
    // behavior at all (this app's own documented judgment call, not derived
    // from any external habit-formation study).
    public static final int MIN_STREAK_FOR_MILESTONE = 5;

    // Steps: the window handed to ActivityInsightService#computeStepTrends
    // to get weekly-bucketed points. That service only returns "week"
    // granularity for windows > WEEKLY_ROLLUP_THRESHOLD_DAYS (31) and <=
    // MONTHLY_ROLLUP_THRESHOLD_DAYS (120) — see its own class Javadoc. 120
    // is therefore the widest window that still yields weekly (rather than
    // monthly) buckets on the existing endpoint, without recomputing that
    // service's own rollup logic here.
    public static final int STEPS_TREND_WINDOW_DAYS = 120;
    public static final int MIN_WEEKS_FOR_STEPS_MILESTONE = 2;

    // VO2max: need at least one real prior month plus the current one to
    // have anything to compare against.
    public static final int MIN_MONTHS_FOR_VO2MAX_MILESTONE = 2;

    // Strength: the 6-month window's monthly buckets are used as "any
    // tracked period" — a real, meaningful period, unlike the 7-day/daily
    // or 30-day/weekly windows which would make "the current, still
    // in-progress period" trivially likely to be compared against very
    // little history.
    public static final int MIN_PERIODS_FOR_STRENGTH_MILESTONE = 2;

    private static final List<String> BASE_LIMITATIONS = List.of(
            "Every milestone here compares your own real, already-computed values against your own real "
                    + "historical values - never a fabricated or population-based claim, and never LLM-generated.",
            "The steps milestone compares weekly averages within your last " + STEPS_TREND_WINDOW_DAYS
                    + " days of tracked history (the widest window the existing activity-trends endpoint still "
                    + "buckets weekly) - not necessarily your entire account history if you have more than that.",
            "The most recent period in each check (this week's steps, this month's VO2max, this training period) "
                    + "may still be in progress when this runs, so a milestone can reflect a partial period that "
                    + "would otherwise have looked even better once complete.",
            "This module does not remember which milestones were already shown - a condition that is still "
                    + "genuinely true (e.g. you are still on your own longest-ever streak for a habit) can honestly "
                    + "reappear on more than one check rather than firing exactly once.",
            "Sleep consistency isn't included as a milestone type - its fixed rolling window of recent nights "
                    + "doesn't have a clean 'new personal best' framing the way a cumulative streak, week, month, "
                    + "or training period does."
    );

    private final JournalService journalService;
    private final ActivityInsightService activityInsightService;
    private final Vo2MaxService vo2MaxService;
    private final StrengthTrainingService strengthTrainingService;

    public MilestoneService(
            JournalService journalService,
            ActivityInsightService activityInsightService,
            Vo2MaxService vo2MaxService,
            StrengthTrainingService strengthTrainingService
    ) {
        this.journalService = journalService;
        this.activityInsightService = activityInsightService;
        this.vo2MaxService = vo2MaxService;
        this.strengthTrainingService = strengthTrainingService;
    }

    /**
     * Every genuine milestone found right now for this account. Honest
     * empty list — never fabricated filler — when nothing is a real new
     * record. See class Javadoc for exactly which four checks run and why
     * sleep consistency is deliberately excluded.
     */
    public MilestonesResponse computeMilestones(Long accountId) {
        List<Milestone> milestones = new ArrayList<>();

        milestones.addAll(checkHabitStreaks(accountId));
        checkStepsMilestone(accountId).ifPresent(milestones::add);
        checkVo2MaxMilestone(accountId).ifPresent(milestones::add);
        checkStrengthMilestone(accountId).ifPresent(milestones::add);

        return new MilestonesResponse(milestones, ALGORITHM_VERSION, BASE_LIMITATIONS);
    }

    /**
     * A behavior is a genuine milestone when its live current streak has
     * reached (or ties) the longest streak this account has ever logged for
     * that exact behavior, and that streak is at least {@value
     * #MIN_STREAK_FOR_MILESTONE} days — see {@link
     * JournalService#getStreaks} for how {@code currentStreak}/{@code
     * longestStreak} are computed. {@code previousBest} is intentionally
     * {@code null}: {@code longestStreak} already IS the single historical
     * maximum across this behavior's entire logged history, so once
     * {@code currentStreak == longestStreak} there is no distinct, separate
     * "second-best" figure this app tracks to show as a different number —
     * the milestone itself is exactly "this ties/sets your longest streak
     * ever," not "this beats a specific different number."
     */
    private List<Milestone> checkHabitStreaks(Long accountId) {
        try {
            List<HabitStreak> streaks = journalService.getStreaks(accountId);
            List<Milestone> found = new ArrayList<>();
            for (HabitStreak s : streaks) {
                if (s.currentStreak() >= MIN_STREAK_FOR_MILESTONE && s.currentStreak() == s.longestStreak()) {
                    found.add(new Milestone(
                            "habit_streak",
                            "Longest streak yet: " + s.behavior(),
                            String.format(
                                    "%s (%s): a %d-day streak - your longest one on record for this behavior.",
                                    s.behavior(), s.category(), s.currentStreak()),
                            s.currentStreak(),
                            null,
                            "days",
                            "as of " + s.lastLoggedDate(),
                            Instant.now()
                    ));
                }
            }
            return found;
        } catch (Exception e) {
            log.warn("Failed to check habit-streak milestones for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * A genuine milestone when the most recent real tracked week's average
     * steps/day is at least as high as every other real week returned by
     * {@link ActivityInsightService#computeStepTrends} for {@value
     * #STEPS_TREND_WINDOW_DAYS} days (that service buckets weekly for this
     * window size — see its own class Javadoc). Requires at least {@value
     * #MIN_WEEKS_FOR_STEPS_MILESTONE} real weeks so there is a genuine prior
     * week to compare against, never a trivial "first week is automatically
     * the best week" result.
     */
    private Optional<Milestone> checkStepsMilestone(Long accountId) {
        try {
            List<ActivityTrendPoint> points = activityInsightService.computeStepTrends(accountId, STEPS_TREND_WINDOW_DAYS);
            List<ActivityTrendPoint> weekly = points.stream()
                    .filter(p -> "week".equals(p.granularity()))
                    .sorted(Comparator.comparing(ActivityTrendPoint::date))
                    .toList();
            if (weekly.size() < MIN_WEEKS_FOR_STEPS_MILESTONE) {
                return Optional.empty();
            }

            ActivityTrendPoint current = weekly.get(weekly.size() - 1);
            List<ActivityTrendPoint> prior = weekly.subList(0, weekly.size() - 1);
            int previousBest = prior.stream().mapToInt(ActivityTrendPoint::steps).max().orElse(0);

            if (current.steps() < previousBest) {
                return Optional.empty();
            }

            return Optional.of(new Milestone(
                    "steps_week",
                    "Best tracked week of steps",
                    String.format(
                            "Averaged %,d steps/day for the week of %s - your highest weekly average across the "
                                    + "%d real tracked weeks in this window (previous best: %,d/day).",
                            current.steps(), current.date(), weekly.size(), previousBest),
                    current.steps(),
                    prior.isEmpty() ? null : (double) previousBest,
                    "steps/day",
                    "week of " + current.date(),
                    Instant.now()
            ));
        } catch (Exception e) {
            log.warn("Failed to check steps milestone for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * A genuine milestone when the most recent real calendar month's VO2max
     * estimate is at least as high as every other real month returned by
     * {@link Vo2MaxService#computeTrend}. Requires at least {@value
     * #MIN_MONTHS_FOR_VO2MAX_MILESTONE} real months so there is a genuine
     * prior month to compare against.
     */
    private Optional<Milestone> checkVo2MaxMilestone(Long accountId) {
        try {
            List<Vo2MaxTrendPoint> trend = vo2MaxService.computeTrend(accountId);
            List<Vo2MaxTrendPoint> sorted = trend.stream()
                    .sorted(Comparator.comparing(Vo2MaxTrendPoint::month))
                    .toList();
            if (sorted.size() < MIN_MONTHS_FOR_VO2MAX_MILESTONE) {
                return Optional.empty();
            }

            Vo2MaxTrendPoint current = sorted.get(sorted.size() - 1);
            List<Vo2MaxTrendPoint> prior = sorted.subList(0, sorted.size() - 1);
            double previousBest = prior.stream().mapToDouble(Vo2MaxTrendPoint::vo2Max).max().orElse(0);

            if (current.vo2Max() < previousBest) {
                return Optional.empty();
            }

            return Optional.of(new Milestone(
                    "vo2max_month",
                    "New VO2max high",
                    String.format(
                            "Your estimated VO2max reached %.1f mL/kg/min in %s - your highest recorded estimate "
                                    + "across %d tracked months (previous best: %.1f mL/kg/min).",
                            current.vo2Max(), current.month(), sorted.size(), previousBest),
                    current.vo2Max(),
                    previousBest,
                    "mL/kg/min",
                    current.month(),
                    Instant.now()
            ));
        } catch (Exception e) {
            log.warn("Failed to check VO2max milestone for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * A genuine milestone when the most recent real monthly period in
     * {@link StrengthTrainingService}'s {@code SIXMONTH} trend has at least
     * as many total strength-training minutes as every other real period in
     * that same trend. Uses the 6-month (monthly-bucketed) window
     * specifically as "any tracked period," rather than the weekly/daily or
     * monthly/weekly windows, so the comparison is across real, meaningful
     * calendar months rather than very short buckets. Requires at least
     * {@value #MIN_PERIODS_FOR_STRENGTH_MILESTONE} real periods (only
     * periods with at least one contributing session/workout are returned
     * at all — see {@link StrengthActivityTrendPoint}) so there is a genuine
     * prior period to compare against.
     */
    private Optional<Milestone> checkStrengthMilestone(Long accountId) {
        try {
            StrengthActivityTrend trend = strengthTrainingService.computeTrend(accountId, StrengthTrainingService.Window.SIXMONTH);
            List<StrengthActivityTrendPoint> points = trend.points();
            if (points.size() < MIN_PERIODS_FOR_STRENGTH_MILESTONE) {
                return Optional.empty();
            }

            StrengthActivityTrendPoint current = points.get(points.size() - 1);
            List<StrengthActivityTrendPoint> prior = points.subList(0, points.size() - 1);
            double previousBest = prior.stream().mapToDouble(StrengthActivityTrendPoint::totalMinutes).max().orElse(0);

            if (current.totalMinutes() < previousBest) {
                return Optional.empty();
            }

            return Optional.of(new Milestone(
                    "strength_period",
                    "Most strength training yet",
                    String.format(
                            "Logged %.0f minutes of strength training from %s to %s - your most in any tracked "
                                    + "period across %d real periods (previous best: %.0f min).",
                            current.totalMinutes(), current.periodStart(), current.periodEnd(),
                            points.size(), previousBest),
                    current.totalMinutes(),
                    previousBest,
                    "minutes",
                    current.periodStart() + " to " + current.periodEnd(),
                    Instant.now()
            ));
        } catch (Exception e) {
            log.warn("Failed to check strength milestone for account {}: {}", accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
