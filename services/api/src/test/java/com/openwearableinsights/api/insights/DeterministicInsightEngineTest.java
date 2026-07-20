package com.openwearableinsights.api.insights;

import com.openwearableinsights.api.insights.application.DeterministicInsightEngine;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.Insight;
import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.domain.ReadinessInputs;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the deterministic insight engine (fallback coach).
 *
 * <p>Verifies that the fallback produces grounded, schema-consistent output
 * without an LLM.
 */
class DeterministicInsightEngineTest {

    private final ReadinessCalculator calculator = new ReadinessCalculator();
    private final DeterministicInsightEngine engine = new DeterministicInsightEngine();

    @Test
    void generatesInsightWithAllRequiredFields() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(8.0), Optional.of(-1.5), Optional.of(6.5), Optional.of(7.5),
                Optional.of(280.0), Optional.of(260.0), Optional.of(35.0),
                0.75, 3
        ));

        Insight insight = engine.generate(score);

        assertThat(insight.headline()).isNotBlank();
        assertThat(insight.summary()).isNotBlank();
        assertThat(insight.supportingFactors()).isNotEmpty();
        assertThat(insight.recommendedActions()).isNotEmpty();
        assertThat(insight.confidence()).isNotBlank();
        assertThat(insight.cautions()).isNotEmpty();
        assertThat(insight.dataLimitations()).isNotEmpty();
        assertThat(insight.fallbackUsed()).isFalse();
    }

    @Test
    void headlineReflectsProvisionalState() {
        ReadinessScore provisional = calculator.calculate(new ReadinessInputs(
                Optional.of(5.0), Optional.of(-1.0), Optional.of(7.0), Optional.of(7.5),
                Optional.of(250.0), Optional.of(250.0), Optional.of(40.0),
                0.80, 3
        ));

        Insight insight = engine.generate(provisional);

        assertThat(insight.headline()).contains("Provisional");
    }

    @Test
    void noMedicalDiagnosisLanguage() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(10.0), Optional.of(-2.0), Optional.of(8.0), Optional.of(7.5),
                Optional.of(200.0), Optional.of(250.0), Optional.of(20.0),
                0.95, 30
        ));

        Insight insight = engine.generate(score);

        String allText = insight.headline() + " " + insight.summary()
                + " " + String.join(" ", insight.cautions())
                + " " + String.join(" ", insight.dataLimitations());

        assertThat(allText).doesNotContain("diagnos");
        assertThat(allText).doesNotContain("medication");
        assertThat(allText).doesNotContain("treatment");
    }
}