package com.openwearableinsights.api.readiness;

import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.domain.ReadinessInputs;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test for the readiness calculator v0.1.
 *
 * <p>Pins known-good outputs for known inputs. Failures require an ADR-level
 * methodology change, not a silent rebaseline.
 */
class ReadinessCalculatorGoldenTest {

    private final ReadinessCalculator calculator = new ReadinessCalculator();

    @Test
    void syntheticProvisionalScore_isProvisionalWithLowConfidence() {
        ReadinessInputs inputs = new ReadinessInputs(
                Optional.of(8.0), Optional.of(-1.5), Optional.of(6.5), Optional.of(7.5),
                Optional.of(280.0), Optional.of(260.0), Optional.of(35.0),
                0.75, 3
        );

        ReadinessScore score = calculator.calculate(inputs);

        assertThat(score.algorithmVersion()).isEqualTo("0.2");
        assertThat(score.provisional()).isTrue();
        assertThat(score.confidence()).isEqualTo("low");
        assertThat(score.score()).isBetween(0, 100);
        assertThat(score.factors()).isNotEmpty();
        assertThat(score.missingDataTreatment()).contains("All factors present");
        assertThat(score.explanation()).isNotBlank();
        assertThat(score.limitations()).isNotBlank();
    }

    @Test
    void allFactorsPositive_yieldsHighScore() {
        ReadinessInputs inputs = new ReadinessInputs(
                Optional.of(15.0), Optional.of(-3.0), Optional.of(8.0), Optional.of(7.5),
                Optional.of(200.0), Optional.of(250.0), Optional.of(20.0),
                0.95, 30
        );

        ReadinessScore score = calculator.calculate(inputs);

        assertThat(score.provisional()).isFalse();
        assertThat(score.score()).isGreaterThanOrEqualTo(75);
        assertThat(score.confidence()).isEqualTo("high");
        assertThat(score.dataQuality()).isEqualTo("good");
    }

    @Test
    void allFactorsNegative_yieldsLowScore() {
        ReadinessInputs inputs = new ReadinessInputs(
                Optional.of(-10.0), Optional.of(3.0), Optional.of(5.0), Optional.of(7.5),
                Optional.of(350.0), Optional.of(250.0), Optional.of(80.0),
                0.95, 30
        );

        ReadinessScore score = calculator.calculate(inputs);

        assertThat(score.provisional()).isFalse();
        assertThat(score.score()).isLessThanOrEqualTo(40);
    }

    @Test
    void missingFactors_areExplicitlyReported() {
        ReadinessInputs inputs = new ReadinessInputs(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                0.25, 3
        );

        ReadinessScore score = calculator.calculate(inputs);

        assertThat(score.provisional()).isTrue();
        assertThat(score.confidence()).isEqualTo("low");
        assertThat(score.dataQuality()).isEqualTo("poor");
        assertThat(score.missingDataTreatment()).contains("HRV deviation");
        assertThat(score.missingDataTreatment()).contains("RHR deviation");
        assertThat(score.missingDataTreatment()).contains("Stress");
    }

    @Test
    void scoreIsDeterministic_sameInputsYieldSameScore() {
        ReadinessInputs inputs = new ReadinessInputs(
                Optional.of(5.0), Optional.of(-1.0), Optional.of(7.0), Optional.of(7.5),
                Optional.of(250.0), Optional.of(250.0), Optional.of(40.0),
                0.80, 14
        );

        ReadinessScore score1 = calculator.calculate(inputs);
        ReadinessScore score2 = calculator.calculate(inputs);

        assertThat(score1.score()).isEqualTo(score2.score());
        assertThat(score1.factors()).hasSameSizeAs(score2.factors());
    }
}