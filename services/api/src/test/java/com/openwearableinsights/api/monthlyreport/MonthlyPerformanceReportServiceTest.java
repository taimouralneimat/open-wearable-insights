package com.openwearableinsights.api.monthlyreport;

import com.openwearableinsights.api.monthlyreport.application.MonthlyPerformanceReportService;
import com.openwearableinsights.api.monthlyreport.domain.MonthlyMetricSection;
import com.openwearableinsights.api.monthlyreport.domain.MonthlyMetricSection.TrendDirection;
import com.openwearableinsights.api.monthlyreport.domain.MonthlyPerformanceReport;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import com.openwearableinsights.api.sleep.application.SleepInsightService;
import com.openwearableinsights.api.sleep.domain.SleepTrendPoint;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadTrendPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MonthlyPerformanceReportService}: the per-month
 * "28 recovery scores" gate, the honest "not enough history yet" state
 * (never a partial report), the strain/sleep/recovery section breakdown
 * built from real (mocked) collaborator data, the first-half-vs-second-half
 * trend direction, and the never-inflated overall confidence.
 *
 * <p>All three collaborators ({@link ReadinessScoreHistoryRepository},
 * {@link TrainingLoadService}, {@link SleepInsightService}) are mocked
 * directly -- their own methodologies are already covered by their own unit
 * tests; this only verifies how this service composes their real outputs.
 *
 * <p>Uses only synthetic data (no real health data). A fixed month
 * (June 2026, 30 real calendar days) is used throughout so the math is
 * reproducible regardless of when the test runs.
 */
class MonthlyPerformanceReportServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final YearMonth JUNE_2026 = YearMonth.of(2026, 6);

    private ReadinessScoreHistoryRepository readinessScoreHistoryRepository;
    private TrainingLoadService trainingLoadService;
    private SleepInsightService sleepInsightService;

    @BeforeEach
    void setUp() {
        readinessScoreHistoryRepository = mock(ReadinessScoreHistoryRepository.class);
        trainingLoadService = mock(TrainingLoadService.class);
        sleepInsightService = mock(SleepInsightService.class);
    }

    private MonthlyPerformanceReportService service() {
        return new MonthlyPerformanceReportService(readinessScoreHistoryRepository, trainingLoadService, sleepInsightService);
    }

    // --- Insufficient-history gate ---

    @Test
    void computeReport_fewerThan28RecoveryScoresInMonth_returnsHonestInsufficientHistoryState() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 10, 60)); // only 10 of 30 days

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.sufficientHistory()).isFalse();
        assertThat(report.recoveryScoreCount()).isEqualTo(10);
        assertThat(report.requiredRecoveryScoreCount()).isEqualTo(28);
        assertThat(report.strain()).isNull();
        assertThat(report.sleep()).isNull();
        assertThat(report.recovery()).isNull();
        assertThat(report.overallConfidence()).isEqualTo("none");
        assertThat(report.insufficientHistoryMessage()).contains("10").contains("28").contains("2026-06");
        assertThat(report.limitations()).anyMatch(l -> l.contains("10"));
        assertThat(report.algorithmVersion()).isEqualTo(MonthlyPerformanceReportService.ALGORITHM_VERSION);
    }

    @Test
    void computeReport_exactlyAtGate_isSufficient() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 28, 60));
        stubNoStrainData();
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.sufficientHistory()).isTrue();
        assertThat(report.recoveryScoreCount()).isEqualTo(28);
    }

    @Test
    void computeReport_readinessHistoryQueryThrows_treatedAsZeroScores_returnsInsufficientHistory() {
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID))
                .thenThrow(new RuntimeException("connection lost"));

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.sufficientHistory()).isFalse();
        assertThat(report.recoveryScoreCount()).isEqualTo(0);
    }

    // --- Month filtering ---

    @Test
    void computeReport_onlyCountsScoresWithinTheRequestedMonth() {
        Map<LocalDate, Integer> scores = new HashMap<>(scoresForDays(JUNE_2026, 1, 30, 60));
        // Real scores from adjacent months must not leak into June's count or averages.
        scores.put(LocalDate.of(2026, 5, 31), 10);
        scores.put(LocalDate.of(2026, 7, 1), 10);
        stubRecoveryScores(scores);
        stubNoStrainData();
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.recoveryScoreCount()).isEqualTo(30);
        assertThat(report.recovery().averageValue()).isEqualTo(60.0);
    }

    // --- Recovery section + trend direction ---

    @Test
    void computeReport_recoverySection_computesAverageAndImprovingTrend() {
        // First half (days 1-15) score 60, second half (days 16-30) score 80 -> +33% -> IMPROVING.
        Map<LocalDate, Integer> scores = new HashMap<>();
        for (int day = 1; day <= 30; day++) {
            scores.put(JUNE_2026.atDay(day), day <= 15 ? 60 : 80);
        }
        stubRecoveryScores(scores);
        stubNoStrainData();
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        MonthlyMetricSection recovery = report.recovery();
        assertThat(recovery.name()).isEqualTo("Recovery (readiness score)");
        assertThat(recovery.averageValue()).isEqualTo(70.0);
        assertThat(recovery.unit()).isEqualTo("0-100 score");
        assertThat(recovery.daysWithData()).isEqualTo(30);
        assertThat(recovery.daysInMonth()).isEqualTo(30);
        assertThat(recovery.trendDirection()).isEqualTo(TrendDirection.IMPROVING);
        assertThat(recovery.trendDetail()).contains("60.0").contains("80.0");
        assertThat(recovery.confidence()).isEqualTo("medium");
    }

    @Test
    void computeReport_decliningTrend_whenSecondHalfLowerByMoreThanThreshold() {
        Map<LocalDate, Integer> scores = new HashMap<>();
        for (int day = 1; day <= 30; day++) {
            scores.put(JUNE_2026.atDay(day), day <= 15 ? 80 : 60);
        }
        stubRecoveryScores(scores);
        stubNoStrainData();
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.recovery().trendDirection()).isEqualTo(TrendDirection.DECLINING);
    }

    @Test
    void computeReport_steadyTrend_whenChangeBelowFivePercentThreshold() {
        Map<LocalDate, Integer> scores = new HashMap<>();
        for (int day = 1; day <= 30; day++) {
            scores.put(JUNE_2026.atDay(day), day <= 15 ? 70 : 72); // ~2.9% change
        }
        stubRecoveryScores(scores);
        stubNoStrainData();
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.recovery().trendDirection()).isEqualTo(TrendDirection.STEADY);
    }

    // --- Strain section ---

    @Test
    void computeReport_strainSection_filtersToMonthAndComputesAverage() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 28, 60));
        List<TrainingLoadTrendPoint> history = new ArrayList<>();
        // 10 real training days spread across June, plus one in May that must be excluded.
        for (int day = 1; day <= 20; day += 2) {
            history.add(new TrainingLoadTrendPoint(JUNE_2026.atDay(day).toString(), 40.0));
        }
        history.add(new TrainingLoadTrendPoint("2026-05-30", 999.0));
        when(trainingLoadService.fetchDailyLoadHistory(eq(ACCOUNT_ID), anyInt())).thenReturn(history);
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        MonthlyMetricSection strain = report.strain();
        assertThat(strain.name()).isEqualTo("Strain (training load)");
        assertThat(strain.averageValue()).isEqualTo(40.0);
        assertThat(strain.daysWithData()).isEqualTo(10);
        assertThat(strain.unit()).isEqualTo("training load (a.u.)");
        assertThat(strain.limitations()).anyMatch(l -> l.contains("not a value judgment"));
    }

    @Test
    void computeReport_strainSection_noDataThisMonth_returnsHonestEmptySection() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 28, 60));
        stubNoStrainData();
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        MonthlyMetricSection strain = report.strain();
        assertThat(strain.averageValue()).isNull();
        assertThat(strain.daysWithData()).isEqualTo(0);
        assertThat(strain.confidence()).isEqualTo("none");
        assertThat(strain.trendDirection()).isEqualTo(TrendDirection.UNKNOWN);
    }

    @Test
    void computeReport_strainSection_tooFewPointsForTrend_returnsUnknownDirectionButRealAverage() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 28, 60));
        List<TrainingLoadTrendPoint> history = List.of(
                new TrainingLoadTrendPoint(JUNE_2026.atDay(2).toString(), 30.0),
                new TrainingLoadTrendPoint(JUNE_2026.atDay(10).toString(), 50.0)
        );
        when(trainingLoadService.fetchDailyLoadHistory(eq(ACCOUNT_ID), anyInt())).thenReturn(history);
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        MonthlyMetricSection strain = report.strain();
        assertThat(strain.averageValue()).isEqualTo(40.0);
        assertThat(strain.trendDirection()).isEqualTo(TrendDirection.UNKNOWN);
        assertThat(strain.trendDetail()).contains("Not enough real data points");
    }

    @Test
    void computeReport_trainingLoadServiceThrows_strainSectionIsHonestlyEmpty_restOfReportStillGenerates() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 28, 60));
        when(trainingLoadService.fetchDailyLoadHistory(eq(ACCOUNT_ID), anyInt()))
                .thenThrow(new RuntimeException("db down"));
        stubNoSleepData();

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.sufficientHistory()).isTrue();
        assertThat(report.strain().averageValue()).isNull();
        assertThat(report.recovery()).isNotNull();
    }

    // --- Sleep section ---

    @Test
    void computeReport_sleepSection_reusesSleepScoreAndFiltersToMonth() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 28, 60));
        stubNoStrainData();
        List<SleepTrendPoint> trend = new ArrayList<>();
        for (int day = 1; day <= 30; day++) {
            trend.add(new SleepTrendPoint(JUNE_2026.atDay(day).toString(), 7.5, 1.5, 1.5, 4.0, 0.5, 75, "day"));
        }
        trend.add(new SleepTrendPoint("2026-07-02", 7.0, 1.0, 1.0, 4.0, 1.0, 50, "day"));
        when(sleepInsightService.computeTrends(eq(ACCOUNT_ID), anyInt())).thenReturn(trend);

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        MonthlyMetricSection sleep = report.sleep();
        assertThat(sleep.name()).isEqualTo("Sleep (sleep score)");
        assertThat(sleep.averageValue()).isEqualTo(75.0);
        assertThat(sleep.daysWithData()).isEqualTo(30);
        assertThat(sleep.unit()).isEqualTo("0-100 score");
        assertThat(sleep.confidence()).isEqualTo("medium");
    }

    // --- Overall confidence: capped, never inflated ---

    @Test
    void computeReport_overallConfidence_isMinimumOfSections_neverHigh() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 30, 60)); // full coverage -> recovery = medium
        stubNoStrainData(); // -> strain = none
        List<SleepTrendPoint> trend = new ArrayList<>();
        for (int day = 1; day <= 30; day++) {
            trend.add(new SleepTrendPoint(JUNE_2026.atDay(day).toString(), 7.5, 1.5, 1.5, 4.0, 0.5, 75, "day"));
        }
        when(sleepInsightService.computeTrends(eq(ACCOUNT_ID), anyInt())).thenReturn(trend); // -> sleep = medium

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        // Weakest section (strain = none) drags the overall confidence down, even though
        // the other two sections are fully healthy -- never inflated above the weakest input.
        assertThat(report.overallConfidence()).isEqualTo("none");
    }

    @Test
    void computeReport_overallConfidence_allSectionsMedium_reportsMedium_neverHigh() {
        stubRecoveryScores(scoresForDays(JUNE_2026, 1, 30, 60));
        List<TrainingLoadTrendPoint> strainHistory = new ArrayList<>();
        for (int day = 1; day <= 20; day++) {
            strainHistory.add(new TrainingLoadTrendPoint(JUNE_2026.atDay(day).toString(), 40.0));
        }
        when(trainingLoadService.fetchDailyLoadHistory(eq(ACCOUNT_ID), anyInt())).thenReturn(strainHistory);
        List<SleepTrendPoint> sleepTrend = new ArrayList<>();
        for (int day = 1; day <= 30; day++) {
            sleepTrend.add(new SleepTrendPoint(JUNE_2026.atDay(day).toString(), 7.5, 1.5, 1.5, 4.0, 0.5, 75, "day"));
        }
        when(sleepInsightService.computeTrends(eq(ACCOUNT_ID), anyInt())).thenReturn(sleepTrend);

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID, JUNE_2026);

        assertThat(report.overallConfidence()).isEqualTo("medium");
        assertThat(report.overallConfidence()).isNotEqualTo("high");
    }

    // --- Default month ---

    @Test
    void computeReport_noMonthGiven_defaultsToMostRecentlyCompleteMonth() {
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID)).thenReturn(Map.of());

        MonthlyPerformanceReport report = service().computeReport(ACCOUNT_ID);

        assertThat(report.month()).isEqualTo(YearMonth.now().minusMonths(1).toString());
    }

    // --- Helpers ---

    private void stubRecoveryScores(Map<LocalDate, Integer> scores) {
        when(readinessScoreHistoryRepository.findScoresByAccountId(ACCOUNT_ID)).thenReturn(scores);
    }

    private void stubNoStrainData() {
        when(trainingLoadService.fetchDailyLoadHistory(eq(ACCOUNT_ID), anyInt())).thenReturn(List.of());
    }

    private void stubNoSleepData() {
        when(sleepInsightService.computeTrends(eq(ACCOUNT_ID), anyInt())).thenReturn(List.of());
    }

    private Map<LocalDate, Integer> scoresForDays(YearMonth month, int fromDay, int toDay, int score) {
        Map<LocalDate, Integer> scores = new HashMap<>();
        for (int day = fromDay; day <= toDay; day++) {
            scores.put(month.atDay(day), score);
        }
        return scores;
    }
}
