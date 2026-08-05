package com.openwearableinsights.api.healthspan;

import com.openwearableinsights.api.healthspan.application.HealthspanService;
import com.openwearableinsights.api.healthspan.domain.HealthspanFactor;
import com.openwearableinsights.api.healthspan.domain.HealthspanScore;
import com.openwearableinsights.api.healthspan.domain.HealthspanSummary;
import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.sleep.application.SleepInsightService;
import com.openwearableinsights.api.sleep.domain.SleepConsistency;
import com.openwearableinsights.api.strength.application.StrengthTrainingService;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrend;
import com.openwearableinsights.api.strength.domain.StrengthActivityTrendPoint;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadSummary;
import com.openwearableinsights.api.vo2max.application.Vo2MaxService;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxEstimate;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxTrendPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HealthspanService}: the "recent vs. your own prior
 * history" composite formula, the honest empty state when too few of the
 * five factors have real data, the confidence cap (never "high"), and the
 * fact that the 30-day and 6-month windows dispatch to different underlying
 * windows on {@link Vo2MaxService}/{@link StrengthTrainingService}.
 *
 * <p>All five collaborating services ({@link Vo2MaxService}, {@link
 * StrengthTrainingService}, {@link SleepInsightService}, {@link
 * TrainingLoadService}, {@link BaselineService}) are mocked directly —
 * their own methodologies are already covered by their own unit tests; this
 * only verifies how {@link HealthspanService} composes their real outputs.
 * {@link JdbcTemplate} is mocked only for this service's own new
 * prior-period resting-heart-rate query.
 *
 * <p>Uses only synthetic data (no real health data).
 */
class HealthspanServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    private JdbcTemplate jdbc;
    private Vo2MaxService vo2MaxService;
    private StrengthTrainingService strengthTrainingService;
    private SleepInsightService sleepInsightService;
    private TrainingLoadService trainingLoadService;
    private BaselineService baselineService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        vo2MaxService = mock(Vo2MaxService.class);
        strengthTrainingService = mock(StrengthTrainingService.class);
        sleepInsightService = mock(SleepInsightService.class);
        trainingLoadService = mock(TrainingLoadService.class);
        baselineService = mock(BaselineService.class);
    }

    private HealthspanService service() {
        return new HealthspanService(jdbc, vo2MaxService, strengthTrainingService, sleepInsightService,
                trainingLoadService, baselineService);
    }

    // --- Shared "everything present and healthy" stubs, reused/overridden per test ---

    private void stubVo2MaxTrend(YearMonth[] months, double[] values) {
        List<Vo2MaxTrendPoint> points = new ArrayList<>();
        for (int i = 0; i < months.length; i++) {
            points.add(new Vo2MaxTrendPoint(months[i].toString(), values[i]));
        }
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(points);
        when(vo2MaxService.computeEstimate(ACCOUNT_ID)).thenReturn(new Vo2MaxEstimate(
                42.0, 180, 60.0, "src", "src", Vo2MaxService.ALGORITHM_VERSION, "medium", "methodology",
                List.of(), Instant.now()));
    }

    private void stubStrengthTrend(StrengthTrainingService.Window window, double[] minutes) {
        List<StrengthActivityTrendPoint> points = new ArrayList<>();
        for (int i = 0; i < minutes.length; i++) {
            points.add(new StrengthActivityTrendPoint(
                    "2026-0" + (i + 1) + "-01", "2026-0" + (i + 1) + "-28", minutes[i] / 2, minutes[i] / 2, false, minutes[i]));
        }
        when(strengthTrainingService.computeTrend(eq(ACCOUNT_ID), eq(window)))
                .thenReturn(new StrengthActivityTrend(window.name(), points, null, List.of()));
    }

    private void stubBaselineRhr(double rhr, boolean provisional) {
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(new PersonalBaseline(
                Map.of("rhr", rhr), Map.of("rhr", 20), provisional ? 3 : 28, "medium",
                provisional ? "3-day provisional" : "28-day rolling"));
    }

    private void stubPriorRhr(double avg, int count) {
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(List.of(Map.of("avg_value", avg, "sample_count", count)));
    }

    private void stubSleepConsistency(int score, String confidence) {
        when(sleepInsightService.computeSleepConsistency(ACCOUNT_ID)).thenReturn(new SleepConsistency(
                score, "23:00", "07:00", 10.0, 10.0, 14, confidence, List.of()));
    }

    private void stubTrainingLoad(String loadStatus, Double acwr, String confidence) {
        when(trainingLoadService.computeSummary(ACCOUNT_ID)).thenReturn(new TrainingLoadSummary(
                50.0, 45.0, acwr, loadStatus, "trainingload-v1", confidence, List.of()));
    }

    private void stubAllFactorsHealthy() {
        YearMonth now = YearMonth.now();
        stubVo2MaxTrend(
                new YearMonth[]{now.minusMonths(3), now.minusMonths(2), now.minusMonths(1), now},
                new double[]{40.0, 40.0, 44.0, 44.0});
        stubStrengthTrend(StrengthTrainingService.Window.SIXMONTH, new double[]{100, 100, 140, 140});
        stubStrengthTrend(StrengthTrainingService.Window.MONTHLY, new double[]{60, 80, 100});
        stubBaselineRhr(55.0, false);
        stubPriorRhr(60.0, 20);
        stubSleepConsistency(90, "medium");
        stubTrainingLoad("optimal", 1.0, "medium");
    }

    // --- Composite formula, six-month window ---

    @Test
    void computeScore_sixMonth_allFactorsPresent_computesWeightedComposite() {
        stubAllFactorsHealthy();

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        // vo2max: prior avg 40 -> recent avg 44 = +10% -> raw 20 * weight 0.30 = 6.0
        // strength: prior avg 100 -> recent avg 140 = +40% -> raw clamps to 40 * weight 0.20 = 8.0
        // rhr: recent 55 vs prior 60 = 8.33% favorable -> raw 16.667 * weight 0.20 = 3.333
        // sleep: (90-50)*0.8 = 32 * weight 0.15 = 4.8
        // trainingload: optimal = +30 * weight 0.15 = 4.5
        // sum = 26.633 -> 50 + 26.633 = 76.633 -> rounds to 77
        assertThat(score.score()).isEqualTo(77);
        assertThat(score.window()).isEqualTo("6month");
        assertThat(score.algorithmVersion()).isEqualTo(HealthspanService.ALGORITHM_VERSION);
        assertThat(score.confidence()).isEqualTo("medium");
        assertThat(score.factors()).hasSize(5);
        assertThat(score.missingDataTreatment()).isEqualTo("All factors present.");
        assertThat(score.disclaimer()).contains("wellness estimate, not a medical or actuarial age");
        assertThat(score.methodology()).contains("This app's own original composite");

        HealthspanFactor vo2maxFactor = factorNamed(score, "Cardiovascular fitness");
        assertThat(vo2maxFactor.weight()).isEqualTo(0.30);
        assertThat(vo2maxFactor.contribution()).isCloseTo(6.0, offsetOf());
        assertThat(vo2maxFactor.direction()).isEqualTo(HealthspanFactor.Direction.FAVORABLE);

        HealthspanFactor strengthFactor = factorNamed(score, "Resistance-training");
        assertThat(strengthFactor.contribution()).isCloseTo(8.0, offsetOf());

        HealthspanFactor rhrFactor = factorNamed(score, "Resting-heart-rate");
        assertThat(rhrFactor.contribution()).isCloseTo(3.333, org.assertj.core.data.Offset.offset(0.01));
        assertThat(rhrFactor.direction()).isEqualTo(HealthspanFactor.Direction.FAVORABLE);

        // Verifies the six-month window dispatches to StrengthTrainingService.Window.SIXMONTH.
        verify(strengthTrainingService).computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.SIXMONTH);
    }

    @Test
    void computeScore_thirtyDay_dispatchesNarrowerUnderlyingWindows() {
        stubAllFactorsHealthy();

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.THIRTY_DAY);

        assertThat(score.score()).isNotNull();
        assertThat(score.window()).isEqualTo("30day");
        // Verifies the 30-day window dispatches to StrengthTrainingService.Window.MONTHLY, not SIXMONTH.
        verify(strengthTrainingService).computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.MONTHLY);
    }

    @Test
    void computeSummary_returnsBothWindowsWithDistinctLabels() {
        stubAllFactorsHealthy();

        HealthspanSummary summary = service().computeSummary(ACCOUNT_ID);

        assertThat(summary.thirtyDay().window()).isEqualTo("30day");
        assertThat(summary.sixMonth().window()).isEqualTo("6month");
        assertThat(summary.thirtyDay().score()).isNotNull();
        assertThat(summary.sixMonth().score()).isNotNull();
        assertThat(summary.thirtyDay().disclaimer()).isEqualTo(HealthspanService.DISCLAIMER);
        assertThat(summary.sixMonth().disclaimer()).isEqualTo(HealthspanService.DISCLAIMER);
    }

    // --- Honest empty state ---

    @Test
    void computeScore_fewerThanThreeFactors_returnsHonestEmptyScore() {
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(List.of());
        when(strengthTrainingService.computeTrend(eq(ACCOUNT_ID), any(StrengthTrainingService.Window.class))).thenReturn(
                new StrengthActivityTrend("sixmonth", List.of(), null, List.of()));
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(
                new PersonalBaseline(Map.of(), Map.of(), 0, "low", "no baseline data"));
        when(sleepInsightService.computeSleepConsistency(ACCOUNT_ID)).thenReturn(
                new SleepConsistency(null, null, null, null, null, 2, "none", List.of()));
        stubTrainingLoad("unknown", null, "none");

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.score()).isNull();
        assertThat(score.confidence()).isEqualTo("none");
        assertThat(score.factors()).isEmpty();
        assertThat(score.missingDataTreatment()).contains("Cardiovascular fitness")
                .contains("Resistance-training").contains("Resting-heart-rate")
                .contains("Sleep consistency").contains("Training-load balance");
        assertThat(score.limitations()).anyMatch(l -> l.contains("Not enough real history yet"));
        assertThat(score.disclaimer()).contains("wellness estimate, not a medical or actuarial age");
    }

    // --- Confidence: capped, never inflated ---

    @Test
    void computeScore_confidence_neverHigh_evenWhenAllFactorsHealthy() {
        stubAllFactorsHealthy();

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.confidence()).isNotEqualTo("high");
        assertThat(score.confidence()).isEqualTo("medium");
    }

    @Test
    void computeScore_twoOrMoreFactorsMissing_downgradesConfidenceToLow_evenIfPresentFactorsAreMedium() {
        stubAllFactorsHealthy();
        // Knock out two of the five factors: sleep and training load.
        when(sleepInsightService.computeSleepConsistency(ACCOUNT_ID)).thenReturn(
                new SleepConsistency(null, null, null, null, null, 2, "none", List.of()));
        stubTrainingLoad("unknown", null, "none");

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.score()).isNotNull(); // 3 of 5 factors still present, meets MIN_FACTORS_REQUIRED
        assertThat(score.factors()).hasSize(3);
        assertThat(score.confidence()).isEqualTo("low");
    }

    @Test
    void computeScore_oneFactorMissing_keepsMediumConfidenceIfRestAreMedium() {
        stubAllFactorsHealthy();
        stubTrainingLoad("unknown", null, "none");

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.factors()).hasSize(4);
        assertThat(score.missingDataTreatment()).contains("Training-load balance");
        assertThat(score.confidence()).isEqualTo("medium");
    }

    // --- Per-factor honest omission ---

    @Test
    void computeScore_rhrOmitted_whenBaselineHasNoRhr() {
        stubAllFactorsHealthy();
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(
                new PersonalBaseline(Map.of(), Map.of(), 10, "low", "10-day rolling"));

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.missingDataTreatment()).contains("Resting-heart-rate trend");
        assertThat(score.factors()).noneMatch(f -> f.name().contains("Resting-heart-rate"));
    }

    @Test
    void computeScore_rhrPriorQueryFails_omitsFactorRatherThanThrowing() {
        stubAllFactorsHealthy();
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), any(Timestamp.class), any(Timestamp.class)))
                .thenThrow(new RuntimeException("connection lost"));

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.factors()).noneMatch(f -> f.name().contains("Resting-heart-rate"));
        assertThat(score.missingDataTreatment()).contains("Resting-heart-rate trend");
    }

    @Test
    void computeScore_strengthOmitted_whenOnlyOnePeriodOfHistory() {
        stubAllFactorsHealthy();
        // Override with a single-point trend — not enough to split recent vs. prior.
        List<StrengthActivityTrendPoint> onePoint = List.of(
                new StrengthActivityTrendPoint("2026-06-01", "2026-06-30", 30, 30, false, 60));
        when(strengthTrainingService.computeTrend(ACCOUNT_ID, StrengthTrainingService.Window.SIXMONTH))
                .thenReturn(new StrengthActivityTrend("sixmonth", onePoint, null, List.of()));

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.factors()).noneMatch(f -> f.name().contains("Resistance-training"));
        assertThat(score.missingDataTreatment()).contains("Resistance-training consistency");
    }

    @Test
    void computeScore_trainingLoadOmitted_whenStatusUnknown() {
        stubAllFactorsHealthy();
        stubTrainingLoad("unknown", null, "none");

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.factors()).noneMatch(f -> f.name().contains("Training-load"));
        assertThat(score.missingDataTreatment()).contains("Training-load balance");
    }

    @Test
    void computeScore_vo2maxOmitted_whenTrendEmpty() {
        stubAllFactorsHealthy();
        when(vo2MaxService.computeTrend(ACCOUNT_ID)).thenReturn(List.of());

        HealthspanScore score = service().computeScore(ACCOUNT_ID, HealthspanService.Window.SIX_MONTH);

        assertThat(score.factors()).noneMatch(f -> f.name().contains("Cardiovascular"));
        assertThat(score.missingDataTreatment()).contains("Cardiovascular fitness");
    }

    // --- Helpers ---

    private HealthspanFactor factorNamed(HealthspanScore score, String substring) {
        return score.factors().stream()
                .filter(f -> f.name().contains(substring))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No factor found containing '" + substring + "' in " + score.factors()));
    }

    private org.assertj.core.data.Offset<Double> offsetOf() {
        return org.assertj.core.data.Offset.offset(0.001);
    }
}
