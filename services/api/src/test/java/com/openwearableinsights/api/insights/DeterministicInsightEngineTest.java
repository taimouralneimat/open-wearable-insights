package com.openwearableinsights.api.insights;

import com.openwearableinsights.api.insights.application.DeterministicInsightEngine;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.Insight;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.WhyAnswer;
import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.domain.ReadinessInputs;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import com.openwearableinsights.api.readiness.domain.ScoreDiff;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
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

    @Test
    void explainReadiness_citesActualFactorsNotGenericAdvice() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(8.0), Optional.of(-1.5), Optional.of(6.5), Optional.of(7.5),
                Optional.of(280.0), Optional.of(260.0), Optional.of(35.0),
                0.75, 3
        ));

        WhyAnswer why = engine.explainReadiness(score, Optional.empty());

        assertThat(why.answer()).isNotBlank();
        assertThat(why.citedMetrics()).isNotEmpty();
        // Every cited metric must correspond to a real factor from the score
        List<String> factorNames = score.factors().stream().map(f -> f.name()).toList();
        why.citedMetrics().forEach(m -> assertThat(factorNames).contains(m.name()));
        assertThat(why.confidence()).isEqualTo(score.confidence());
        assertThat(why.limitations()).isNotEmpty();
    }

    @Test
    void explainReadiness_withNoPriorData_saysSoExplicitly() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(8.0), Optional.of(-1.5), Optional.of(6.5), Optional.of(7.5),
                Optional.of(280.0), Optional.of(260.0), Optional.of(35.0),
                0.75, 3
        ));

        WhyAnswer why = engine.explainReadiness(score, Optional.empty());

        assertThat(why.answer()).contains("No prior day's score is recorded yet");
    }

    @Test
    void explainReadiness_withPriorData_citesTheChange() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(8.0), Optional.of(-1.5), Optional.of(6.5), Optional.of(7.5),
                Optional.of(280.0), Optional.of(260.0), Optional.of(35.0),
                0.75, 3
        ));

        ScoreDiff diff = new ScoreDiff(
                score.score(), score.score() - 5, 5,
                List.of(), "test", "HRV deviation", null, "yesterday"
        );

        WhyAnswer why = engine.explainReadiness(score, Optional.of(diff));

        assertThat(why.answer()).contains("Compared to yesterday");
        assertThat(why.answer()).contains("improved by 5 points");
        assertThat(why.answer()).contains("HRV deviation");
        assertThat(why.answer()).doesNotContain("No prior day's score is recorded yet");
    }

    @Test
    void explainReadiness_citedMetricsSortedByAbsoluteImpact() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(8.0), Optional.of(-1.5), Optional.of(6.5), Optional.of(7.5),
                Optional.of(280.0), Optional.of(260.0), Optional.of(35.0),
                0.75, 3
        ));

        WhyAnswer why = engine.explainReadiness(score, Optional.empty());

        List<Double> contributions = why.citedMetrics().stream()
                .map(m -> Math.abs(Double.parseDouble(m.contribution())))
                .toList();
        List<Double> sorted = contributions.stream().sorted(Comparator.reverseOrder()).toList();
        assertThat(contributions).isEqualTo(sorted);
    }
}