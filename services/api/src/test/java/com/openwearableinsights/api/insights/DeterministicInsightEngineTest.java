package com.openwearableinsights.api.insights;

import com.openwearableinsights.api.insights.application.DeterministicInsightEngine;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.HabitCue;
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
    void recommendedActions_citesTheActualDominantFactor_notJustAGenericScoreBucket() {
        // Sleep deficit dominates: -3h vs need, dwarfing the small HRV/RHR/stress deviations.
        ReadinessScore lowScore = calculator.calculate(new ReadinessInputs(
                Optional.of(1.0), Optional.of(0.5), Optional.of(4.0), Optional.of(7.5),
                Optional.of(250.0), Optional.of(250.0), Optional.of(52.0),
                0.9, 30
        ));
        assertThat(lowScore.score()).isLessThan(50);

        Insight insight = engine.generate(lowScore);

        assertThat(insight.recommendedActions()).anySatisfy(action ->
                assertThat(action).contains("Sleep duration vs. need"));
    }

    @Test
    void recommendedActions_highScore_citesTheStrongestPositiveFactor() {
        ReadinessScore highScore = calculator.calculate(new ReadinessInputs(
                Optional.of(20.0), Optional.of(-4.0), Optional.of(8.5), Optional.of(7.5),
                Optional.of(200.0), Optional.of(250.0), Optional.of(15.0),
                0.95, 30
        ));
        assertThat(highScore.score()).isGreaterThanOrEqualTo(75);

        Insight insight = engine.generate(highScore);

        assertThat(insight.recommendedActions()).anySatisfy(action ->
                assertThat(action).contains("strongest factor"));
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
                List.of(), "test", "HRV deviation", null, "yesterday", score.confidence()
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

    @Test
    void suggestHabitCue_noNegativeFactors_reportsHonestlyNotPresent() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(15.0), Optional.of(-3.0), Optional.of(8.0), Optional.of(7.5),
                Optional.of(200.0), Optional.of(250.0), Optional.of(20.0),
                0.95, 30
        ));

        HabitCue cue = engine.suggestHabitCue(score);

        assertThat(cue.present()).isFalse();
        assertThat(cue.triggerFactor()).isNull();
        assertThat(cue.reasoning()).isNotBlank();
    }

    @Test
    void suggestHabitCue_worstNegativeFactor_drivesTheSuggestion() {
        // Training load (ACWR) driven strongly negative: acute far above chronic.
        // Every other input is at or above baseline (zero or positive
        // contribution) so ACWR is unambiguously the single worst factor.
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(5.0), Optional.of(-1.0), Optional.of(7.5), Optional.of(7.5),
                Optional.of(500.0), Optional.of(200.0), Optional.of(10.0),
                0.95, 30
        ));

        HabitCue cue = engine.suggestHabitCue(score);

        assertThat(cue.present()).isTrue();
        assertThat(cue.triggerFactor()).isEqualTo("Training load (ACWR)");
        assertThat(cue.triggerContribution()).isNegative();
        assertThat(cue.suggestedCategory()).isNotBlank();
        assertThat(cue.suggestedBehavior()).isNotBlank();
        assertThat(cue.reasoning()).contains("Training load (ACWR)");
    }

    @Test
    void suggestHabitCue_allFactorsNegative_picksTheSingleWorstOne() {
        ReadinessScore score = calculator.calculate(new ReadinessInputs(
                Optional.of(-10.0), Optional.of(3.0), Optional.of(5.0), Optional.of(7.5),
                Optional.of(350.0), Optional.of(250.0), Optional.of(80.0),
                0.95, 30
        ));

        HabitCue cue = engine.suggestHabitCue(score);

        assertThat(cue.present()).isTrue();
        double worstContribution = score.factors().stream()
                .filter(f -> f.direction() == com.openwearableinsights.api.readiness.domain.FactorContribution.Direction.NEGATIVE)
                .mapToDouble(com.openwearableinsights.api.readiness.domain.FactorContribution::contribution)
                .min().orElseThrow();
        assertThat(cue.triggerContribution()).isEqualTo(worstContribution);
    }
}