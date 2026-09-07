package com.openwearableinsights.api.milestones;

import com.openwearableinsights.api.activities.application.ActivityInsightService;
import com.openwearableinsights.api.activities.domain.ActivityTrendPoint;
import com.openwearableinsights.api.journal.application.JournalService;
import com.openwearableinsights.api.journal.domain.HabitStreak;
import com.openwearableinsights.api.milestones.application.MilestoneService;
import com.openwearableinsights.api.milestones.domain.Milestone;
import com.openwearableinsights.api.milestones.domain.MilestonesResponse;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import com.openwearableinsights.api.strength.application.StrengthTrainingService;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrend;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrendPoint;
import com.openwearableinsights.api.vo2max.application.Vo2MaxService;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxTrendPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MilestoneService}: every one of the five checks
 * (readiness score, habit streak, best tracked week of steps, VO2max high,
 * strength-training period) fires only on a genuine "current beats/ties
 * every real prior value" comparison, and the honest empty-list result when
 * nothing qualifies. All five collaborating services are mocked directly —
 * their own methodologies are already covered by their own unit tests; this
 * only verifies how {@link MilestoneService} composes their real outputs.
 *
 * <p>Uses only synthetic data (no real health data).
 */
class MilestoneServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    private JournalService journalService;
    private ActivityInsightService activityInsightService;
    private Vo2MaxService vo2MaxService;
    private StrengthTrainingService strengthTrainingService;
    private ReadinessScoreHistoryRepository readinessScoreHistoryRepository;

    @BeforeEach
    void setUp() {
        journalService = mock(JournalService.class);
        activityInsightService = mock(ActivityInsightService.class);
        vo2MaxService = mock(Vo2MaxService.class);
        strengthTrainingService = mock(StrengthTrainingService.class);
        readinessScoreHistoryRepository = mock(ReadinessScoreHistoryRepository.class);

        // Neutral defaults: nothing qualifies anywhere unless a test overrides it.
        when(journalService.getStreaks(ACCOUNT_ID)).thenReturn(List.of());
        when(activityInsightService.computeStepTrends(eq(ACCOUNT_ID), anyInt())).thenReturn(List.of());
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(List.of());
        when(strengthTrainingService.computeTrend(eq(ACCOUNT_ID), eq(StrengthTrainingService.Window.SIXMONTH)))
                .thenReturn(new StrengthActivityTrend("sixmonth", List.of(), null, List.of()));
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID)).thenReturn(Map.of());
    }

    private MilestoneService service() {
        return new MilestoneService(journalService, activityInsightService, vo2MaxService, strengthTrainingService,
                readinessScoreHistoryRepository);
    }

    // --- Empty state ---

    @Test
    void returnsEmptyListWhenNothingQualifies() {
        MilestonesResponse response = service().computeMilestones(ACCOUNT_ID);

        assertThat(response.milestones()).isEmpty();
        assertThat(response.algorithmVersion()).isEqualTo(MilestoneService.ALGORITHM_VERSION);
        assertThat(response.limitations()).isNotEmpty();
    }

    // --- Readiness score ---

    @Test
    void readinessScoreMilestoneFiresWhenMostRecentDayIsHighestOfAllRealDays() {
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID)).thenReturn(Map.of(
                LocalDate.of(2026, 9, 4), 60,
                LocalDate.of(2026, 9, 5), 55,
                LocalDate.of(2026, 9, 6), 78
        ));

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).hasSize(1);
        Milestone m = milestones.get(0);
        assertThat(m.type()).isEqualTo("readiness_score");
        assertThat(m.value()).isEqualTo(78.0);
        assertThat(m.previousBest()).isEqualTo(60.0);
        assertThat(m.period()).isEqualTo("2026-09-06");
    }

    @Test
    void readinessScoreMilestoneAllowsATieWithThePriorBest() {
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID)).thenReturn(Map.of(
                LocalDate.of(2026, 9, 5), 70,
                LocalDate.of(2026, 9, 6), 70
        ));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).hasSize(1);
    }

    @Test
    void readinessScoreMilestoneDoesNotFireWhenMostRecentDayIsLowerThanAnEarlierOne() {
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID)).thenReturn(Map.of(
                LocalDate.of(2026, 9, 5), 80,
                LocalDate.of(2026, 9, 6), 60
        ));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void readinessScoreMilestoneDoesNotFireWithFewerThanTwoRealDays() {
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID))
                .thenReturn(Map.of(LocalDate.of(2026, 9, 6), 80));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void readinessScoreMilestoneUsesTheLatestRealDateNotMapIterationOrder() {
        // A LocalDate-keyed Map has no guaranteed iteration order — this
        // confirms the check explicitly finds the max date, not just
        // whichever entry happens to iterate last.
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID)).thenReturn(Map.of(
                LocalDate.of(2026, 9, 1), 90,
                LocalDate.of(2026, 8, 15), 50
        ));

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).hasSize(1);
        assertThat(milestones.get(0).period()).isEqualTo("2026-09-01");
    }

    // --- Habit streak ---

    @Test
    void habitStreakMilestoneFiresWhenCurrentTiesLongestAndMeetsMinimum() {
        when(journalService.getStreaks(ACCOUNT_ID)).thenReturn(List.of(
                new HabitStreak("Recovery", "Stretching/mobility work", 7, 7, "2026-09-06")
        ));

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).hasSize(1);
        Milestone m = milestones.get(0);
        assertThat(m.type()).isEqualTo("habit_streak");
        assertThat(m.value()).isEqualTo(7);
        assertThat(m.previousBest()).isNull();
        assertThat(m.unit()).isEqualTo("days");
        assertThat(m.description()).contains("Stretching/mobility work").contains("7-day streak");
    }

    @Test
    void habitStreakMilestoneDoesNotFireWhenCurrentIsBelowLongest() {
        when(journalService.getStreaks(ACCOUNT_ID)).thenReturn(List.of(
                new HabitStreak("Sleep", "Consistent bedtime", 3, 10, "2026-09-06")
        ));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void habitStreakMilestoneDoesNotFireBelowMinimumEvenWhenTied() {
        when(journalService.getStreaks(ACCOUNT_ID)).thenReturn(List.of(
                new HabitStreak("Nutrition", "Hydration goal met", 2, 2, "2026-09-06")
        ));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void multipleQualifyingStreaksEachProduceTheirOwnMilestone() {
        when(journalService.getStreaks(ACCOUNT_ID)).thenReturn(List.of(
                new HabitStreak("Recovery", "Stretching/mobility work", 7, 7, "2026-09-06"),
                new HabitStreak("Sleep", "Consistent bedtime", 6, 6, "2026-09-06"),
                new HabitStreak("Nutrition", "Hydration goal met", 2, 2, "2026-09-06") // below minimum, excluded
        ));

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).extracting(Milestone::type).containsExactly("habit_streak", "habit_streak");
    }

    // --- Steps ---

    @Test
    void stepsMilestoneFiresWhenMostRecentWeekIsHighestOfAllRealWeeks() {
        List<ActivityTrendPoint> weekly = List.of(
                new ActivityTrendPoint("2026-08-10", 8000, "week"),
                new ActivityTrendPoint("2026-08-17", 9000, "week"),
                new ActivityTrendPoint("2026-08-24", 12000, "week")
        );
        when(activityInsightService.computeStepTrends(ACCOUNT_ID, MilestoneService.STEPS_TREND_WINDOW_DAYS))
                .thenReturn(weekly);

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).hasSize(1);
        Milestone m = milestones.get(0);
        assertThat(m.type()).isEqualTo("steps_week");
        assertThat(m.value()).isEqualTo(12000);
        assertThat(m.previousBest()).isEqualTo(9000.0);
        assertThat(m.period()).isEqualTo("week of 2026-08-24");
    }

    @Test
    void stepsMilestoneDoesNotFireWhenMostRecentWeekIsNotTheHighest() {
        List<ActivityTrendPoint> weekly = List.of(
                new ActivityTrendPoint("2026-08-10", 12000, "week"),
                new ActivityTrendPoint("2026-08-17", 9000, "week"),
                new ActivityTrendPoint("2026-08-24", 8000, "week")
        );
        when(activityInsightService.computeStepTrends(ACCOUNT_ID, MilestoneService.STEPS_TREND_WINDOW_DAYS))
                .thenReturn(weekly);

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void stepsMilestoneDoesNotFireWithFewerThanTwoRealWeeks() {
        when(activityInsightService.computeStepTrends(ACCOUNT_ID, MilestoneService.STEPS_TREND_WINDOW_DAYS))
                .thenReturn(List.of(new ActivityTrendPoint("2026-08-24", 12000, "week")));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void stepsMilestoneIgnoresNonWeeklyGranularityPoints() {
        // Below the 31-day rollup threshold the endpoint returns daily
        // points instead — those shouldn't be treated as "weeks" at all.
        when(activityInsightService.computeStepTrends(ACCOUNT_ID, MilestoneService.STEPS_TREND_WINDOW_DAYS))
                .thenReturn(List.of(
                        new ActivityTrendPoint("2026-09-01", 10000, "day"),
                        new ActivityTrendPoint("2026-09-02", 15000, "day")
                ));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    // --- VO2max ---

    @Test
    void vo2MaxMilestoneFiresWhenMostRecentMonthIsHighestOfAllRealMonths() {
        List<Vo2MaxTrendPoint> trend = List.of(
                new Vo2MaxTrendPoint("2026-06", 40.0),
                new Vo2MaxTrendPoint("2026-07", 41.5),
                new Vo2MaxTrendPoint("2026-08", 44.0)
        );
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(trend);

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).hasSize(1);
        Milestone m = milestones.get(0);
        assertThat(m.type()).isEqualTo("vo2max_month");
        assertThat(m.value()).isEqualTo(44.0);
        assertThat(m.previousBest()).isEqualTo(41.5);
        assertThat(m.period()).isEqualTo("2026-08");
    }

    @Test
    void vo2MaxMilestoneAllowsATieWithThePriorBest() {
        List<Vo2MaxTrendPoint> trend = List.of(
                new Vo2MaxTrendPoint("2026-07", 45.0),
                new Vo2MaxTrendPoint("2026-08", 45.0)
        );
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(trend);

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).hasSize(1);
    }

    @Test
    void vo2MaxMilestoneDoesNotFireWhenMostRecentMonthIsLowerThanAnEarlierOne() {
        List<Vo2MaxTrendPoint> trend = List.of(
                new Vo2MaxTrendPoint("2026-07", 45.0),
                new Vo2MaxTrendPoint("2026-08", 40.0)
        );
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(trend);

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void vo2MaxMilestoneDoesNotFireWithFewerThanTwoRealMonths() {
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(List.of(new Vo2MaxTrendPoint("2026-08", 45.0)));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    // --- Strength ---

    @Test
    void strengthMilestoneFiresWhenMostRecentPeriodIsHighestOfAllRealPeriods() {
        List<StrengthActivityTrendPoint> points = new ArrayList<>(List.of(
                new StrengthActivityTrendPoint("2026-06-01", "2026-06-30", 60, 40, false, 100),
                new StrengthActivityTrendPoint("2026-07-01", "2026-07-31", 80, 40, false, 120),
                new StrengthActivityTrendPoint("2026-08-01", "2026-08-31", 90, 60, false, 150)
        ));
        when(strengthTrainingService.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.SIXMONTH))
                .thenReturn(new StrengthActivityTrend("sixmonth", points, null, List.of()));

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).hasSize(1);
        Milestone m = milestones.get(0);
        assertThat(m.type()).isEqualTo("strength_period");
        assertThat(m.value()).isEqualTo(150.0);
        assertThat(m.previousBest()).isEqualTo(120.0);
        assertThat(m.period()).isEqualTo("2026-08-01 to 2026-08-31");
    }

    @Test
    void strengthMilestoneDoesNotFireWhenMostRecentPeriodIsNotTheHighest() {
        List<StrengthActivityTrendPoint> points = List.of(
                new StrengthActivityTrendPoint("2026-07-01", "2026-07-31", 90, 60, false, 150),
                new StrengthActivityTrendPoint("2026-08-01", "2026-08-31", 60, 40, false, 100)
        );
        when(strengthTrainingService.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.SIXMONTH))
                .thenReturn(new StrengthActivityTrend("sixmonth", points, null, List.of()));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    @Test
    void strengthMilestoneDoesNotFireWithFewerThanTwoRealPeriods() {
        List<StrengthActivityTrendPoint> points = List.of(
                new StrengthActivityTrendPoint("2026-08-01", "2026-08-31", 90, 60, false, 150)
        );
        when(strengthTrainingService.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.SIXMONTH))
                .thenReturn(new StrengthActivityTrend("sixmonth", points, null, List.of()));

        assertThat(service().computeMilestones(ACCOUNT_ID).milestones()).isEmpty();
    }

    // --- Resilience ---

    @Test
    void aFailureInOneCollaboratorDoesNotBlockOtherMilestoneChecks() {
        when(journalService.getStreaks(ACCOUNT_ID)).thenThrow(new RuntimeException("boom"));
        List<Vo2MaxTrendPoint> trend = List.of(
                new Vo2MaxTrendPoint("2026-07", 40.0),
                new Vo2MaxTrendPoint("2026-08", 44.0)
        );
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(trend);

        List<Milestone> milestones = service().computeMilestones(ACCOUNT_ID).milestones();

        assertThat(milestones).hasSize(1);
        assertThat(milestones.get(0).type()).isEqualTo("vo2max_month");
    }
}
