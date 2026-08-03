package com.openwearableinsights.api.readiness;

import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.domain.CurrentMetrics;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the personalized rolling baseline feature (Phase 3 EC1).
 *
 * Tests that the calculator correctly computes deviations from a personal
 * baseline, that baseline sample size and confidence are exposed honestly,
 * and that provisional status is correctly determined.
 */
class PersonalBaselineTest {

    private final ReadinessCalculator calculator = new ReadinessCalculator();

    @Test
    void withPersonalBaseline_computesDeviationsFromBaseline() {
        // Baseline: HRV=45ms, RHR=55bpm, stress=40, sleep=7.0h
        PersonalBaseline baseline = new PersonalBaseline(
                Map.of("hrv", 45.0, "rhr", 55.0, "stress", 40.0, "sleep_duration", 7.0),
                Map.of("hrv", 100, "rhr", 100, "stress", 100, "sleep_duration", 28),
                28, "high", "28-day rolling"
        );

        // Current: HRV=53ms (above baseline = good), RHR=53bpm (below = good)
        CurrentMetrics current = new CurrentMetrics(
                Optional.of(53.0),    // HRV: 53-45 = +8ms deviation
                Optional.of(53.0),    // RHR: 53-55 = -2bpm deviation (good)
                Optional.of(7.5),     // sleep duration
                Optional.empty(),     // sleep need will come from baseline (7.0h)
                Optional.of(250.0),  // acute load
                Optional.of(260.0),  // chronic load
                Optional.of(30.0),    // stress
                Optional.empty(),     // body battery low
                0.85                  // completeness
        );

        ReadinessScore score = calculator.calculate(current, baseline);

        assertThat(score.score()).isBetween(0, 100);
        assertThat(score.provisional()).isFalse(); // 28 days >= 7
        assertThat(score.baselinePeriod()).isEqualTo("28-day rolling");
        assertThat(score.confidence()).isEqualTo("high");
        assertThat(score.factors()).isNotEmpty();
        // HRV deviation should be positive (current > baseline)
        assertThat(score.factors()).anyMatch(f -> f.name().contains("HRV") && f.direction() == FactorContribution.Direction.POSITIVE);
    }

    @Test
    void withProvisionalBaseline_marksAsProvisional() {
        PersonalBaseline baseline = new PersonalBaseline(
                Map.of("hrv", 45.0, "rhr", 55.0),
                Map.of("hrv", 10, "rhr", 10),
                3, "low", "3-day provisional"
        );

        CurrentMetrics current = new CurrentMetrics(
                Optional.of(50.0), Optional.of(54.0),
                Optional.of(6.5), Optional.of(7.5),
                Optional.of(250.0), Optional.of(260.0),
                Optional.of(35.0), Optional.empty(), 0.75
        );

        ReadinessScore score = calculator.calculate(current, baseline);

        assertThat(score.provisional()).isTrue();
        assertThat(score.baselinePeriod()).isEqualTo("3-day provisional");
        assertThat(score.confidence()).isEqualTo("low");
    }

    @Test
    void withEmptyBaseline_fallsBackToNoDeviations() {
        PersonalBaseline baseline = new PersonalBaseline(
                Map.of(), Map.of(), 0, "low", "no baseline data"
        );

        CurrentMetrics current = new CurrentMetrics(
                Optional.empty(), Optional.empty(),
                Optional.of(6.5), Optional.of(7.5),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0.0
        );

        ReadinessScore score = calculator.calculate(current, baseline);

        assertThat(score.score()).isBetween(0, 100);
        // With no baseline, HRV and RHR deviations are missing
        assertThat(score.missingDataTreatment()).contains("HRV");
        assertThat(score.missingDataTreatment()).contains("RHR");
    }

    @Test
    void baselineSampleSizeExposedInConfidence() {
        // 7-day baseline with 4 metrics = medium confidence
        PersonalBaseline baseline = new PersonalBaseline(
                Map.of("hrv", 45.0, "rhr", 55.0, "stress", 40.0, "sleep_duration", 7.0),
                Map.of("hrv", 28, "rhr", 28, "stress", 28, "sleep_duration", 7),
                7, "medium", "7-day rolling"
        );

        CurrentMetrics current = new CurrentMetrics(
                Optional.of(50.0), Optional.of(54.0),
                Optional.of(7.0), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.of(35.0), Optional.empty(), 0.6
        );

        ReadinessScore score = calculator.calculate(current, baseline);

        assertThat(score.confidence()).isEqualTo("medium");
        assertThat(score.baselinePeriod()).isEqualTo("7-day rolling");
    }

    @Test
    void sleepNeedFallsBackToBaselineDuration() {
        PersonalBaseline baseline = new PersonalBaseline(
                Map.of("sleep_duration", 7.5),
                Map.of("sleep_duration", 28),
                28, "high", "28-day rolling"
        );

        CurrentMetrics current = new CurrentMetrics(
                Optional.empty(), Optional.empty(),
                Optional.of(6.0),   // slept 6h
                Optional.empty(),   // no explicit sleep need -> use baseline (7.5h)
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0.2
        );

        ReadinessScore score = calculator.calculate(current, baseline);

        // Sleep deficit = 6.0 - 7.5 = -1.5h (should show as negative)
        assertThat(score.factors()).anyMatch(f -> f.name().contains("Sleep") && f.direction() == FactorContribution.Direction.NEGATIVE);
    }

    @Test
    void bodyBatteryLow_presentWithBaseline_computesDeviationAsAPositiveFactor() {
        // Baseline body_battery_low = 20 (typically drains to 20 by end of day)
        PersonalBaseline baseline = new PersonalBaseline(
                Map.of("body_battery_low", 20.0),
                Map.of("body_battery_low", 14),
                14, "medium", "14-day rolling"
        );

        // Today's low was 35 — less depleted than usual, should read as positive
        CurrentMetrics current = new CurrentMetrics(
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(35.0), 0.2
        );

        ReadinessScore score = calculator.calculate(current, baseline);

        assertThat(score.factors()).anyMatch(f ->
                f.name().contains("Body Battery") && f.direction() == FactorContribution.Direction.POSITIVE);
    }

    @Test
    void bodyBatteryLow_absent_isNotCountedAsAMissingFactor() {
        // No Garmin Connect sync ever ran — Body Battery just isn't in the
        // picture. This must not penalize confidence/data-quality the way a
        // genuinely missing core metric (HRV, RHR, etc.) would.
        PersonalBaseline baseline = new PersonalBaseline(
                Map.of("hrv", 45.0, "rhr", 55.0, "stress", 40.0, "sleep_duration", 7.0),
                Map.of("hrv", 100, "rhr", 100, "stress", 100, "sleep_duration", 28),
                28, "high", "28-day rolling"
        );
        CurrentMetrics current = new CurrentMetrics(
                Optional.of(53.0), Optional.of(53.0),
                Optional.of(7.5), Optional.empty(),
                Optional.of(250.0), Optional.of(260.0),
                Optional.of(30.0), Optional.empty(), 0.95
        );

        ReadinessScore score = calculator.calculate(current, baseline);

        assertThat(score.factors()).noneMatch(f -> f.name().contains("Body Battery"));
        assertThat(score.missingDataTreatment()).doesNotContain("Body Battery");
        assertThat(score.dataQuality()).isEqualTo("good");
    }
}
