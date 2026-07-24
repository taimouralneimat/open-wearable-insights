package com.openwearableinsights.api.readiness.application;

import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.FactorContribution.Direction;
import com.openwearableinsights.api.readiness.domain.ReadinessInputs;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic readiness calculator v0.1.
 *
 * <p>This is the single source of truth for readiness scores. The LLM never
 * computes or overrides this; it only explains the result.
 *
 * <p>Algorithm (v0.1):
 * <ol>
 *   <li>Compute each factor's deviation from baseline.</li>
 *   <li>Apply fixed, documented weights (sum to 1.0).</li>
 *   <li>Map weighted sum to a 0-100 scale.</li>
 *   <li>Compute confidence/data-quality from completeness and freshness.</li>
 *   <li>Record positive and negative factor contributions.</li>
 *   <li>Record missing-data treatment (exclusion; explicitly stated).</li>
 * </ol>
 *
 * <p>Weights (v0.1, documented, not empirically tuned):
 * <ul>
 *   <li>HRV deviation: 0.30</li>
 *   <li>RHR deviation: 0.20</li>
 *   <li>Sleep duration vs. need: 0.25</li>
 *   <li>Training load (ACWR): 0.10</li>
 *   <li>Stress: 0.10</li>
 *   <li>Data completeness: 0.05</li>
 * </ul>
 */
@Service
public class ReadinessCalculator {

    private static final double W_HRV = 0.30;
    private static final double W_RHR = 0.20;
    private static final double W_SLEEP = 0.25;
    private static final double W_LOAD = 0.10;
    private static final double W_STRESS = 0.10;
    private static final double W_COMPLETENESS = 0.05;

    private static final int PROVISIONAL_THRESHOLD_DAYS = 7;

    public ReadinessScore calculate(ReadinessInputs inputs) {
        List<FactorContribution> factors = new ArrayList<>();
        double weightedSum = 0.0;
        List<String> missing = new ArrayList<>();

        // HRV deviation (positive deviation = good)
        if (inputs.hrvDeviationMs().isPresent()) {
            double hrvDev = inputs.hrvDeviationMs().get();
            double hrvScore = clamp(hrvDev * 2.5, -40, 40);
            weightedSum += W_HRV * hrvScore;
            factors.add(new FactorContribution(
                    "HRV deviation", hrvDev, "ms",
                    hrvScore > 0 ? Direction.POSITIVE : (hrvScore < 0 ? Direction.NEGATIVE : Direction.NEUTRAL),
                    W_HRV * hrvScore, "app_computed"));
        } else {
            missing.add("HRV deviation");
        }

        // RHR deviation (negative deviation = good, i.e., lower RHR than baseline)
        if (inputs.rhrDeviationBpm().isPresent()) {
            double rhrDev = inputs.rhrDeviationBpm().get();
            double rhrScore = clamp(-rhrDev * 12.5, -40, 40);
            weightedSum += W_RHR * rhrScore;
            factors.add(new FactorContribution(
                    "RHR deviation", rhrDev, "bpm",
                    rhrScore > 0 ? Direction.POSITIVE : (rhrScore < 0 ? Direction.NEGATIVE : Direction.NEUTRAL),
                    W_RHR * rhrScore, "app_computed"));
        } else {
            missing.add("RHR deviation");
        }

        // Sleep duration vs. need
        if (inputs.sleepDurationHours().isPresent() && inputs.sleepNeedHours().isPresent()) {
            double sleepDur = inputs.sleepDurationHours().get();
            double sleepNeed = inputs.sleepNeedHours().get();
            double sleepDeficit = sleepDur - sleepNeed;
            double sleepScore = clamp(sleepDeficit * 30.0, -40, 30);
            weightedSum += W_SLEEP * sleepScore;
            factors.add(new FactorContribution(
                    "Sleep duration vs. need", sleepDeficit, "hours",
                    sleepScore > 0 ? Direction.POSITIVE : (sleepScore < 0 ? Direction.NEGATIVE : Direction.NEUTRAL),
                    W_SLEEP * sleepScore, "app_computed"));
        } else {
            missing.add("Sleep duration vs. need");
        }

        // Training load (ACWR)
        if (inputs.acuteLoad().isPresent() && inputs.chronicLoad().isPresent()) {
            double acute = inputs.acuteLoad().get();
            double chronic = inputs.chronicLoad().get();
            double acwr = chronic > 0 ? acute / chronic : 1.0;
            double loadScore = clamp((1.0 - acwr) * 50.0, -30, 30);
            weightedSum += W_LOAD * loadScore;
            factors.add(new FactorContribution(
                    "Training load (ACWR)", acwr, "ratio",
                    loadScore > 0 ? Direction.POSITIVE : (loadScore < 0 ? Direction.NEGATIVE : Direction.NEUTRAL),
                    W_LOAD * loadScore, "app_computed"));
        } else {
            missing.add("Training load (ACWR)");
        }

        // Stress (lower is better)
        if (inputs.stressScore().isPresent()) {
            double stress = inputs.stressScore().get();
            double stressScore = clamp((50.0 - stress) * 0.75, -25, 25);
            weightedSum += W_STRESS * stressScore;
            factors.add(new FactorContribution(
                    "Stress", stress, "score",
                    stressScore > 0 ? Direction.POSITIVE : (stressScore < 0 ? Direction.NEGATIVE : Direction.NEUTRAL),
                    W_STRESS * stressScore, "app_computed"));
        } else {
            missing.add("Stress");
        }

        // Data completeness
        double completeness = inputs.dataCompleteness();
        double completenessScore = (completeness - 0.5) * 50.0;
        weightedSum += W_COMPLETENESS * completenessScore;
        factors.add(new FactorContribution(
                "Data completeness", completeness, "fraction",
                completenessScore > 0 ? Direction.POSITIVE : Direction.NEUTRAL,
                W_COMPLETENESS * completenessScore, "app_computed"));

        int score = (int) Math.round(clamp(50 + weightedSum, 0, 100));

        boolean provisional = inputs.baselineDays() < PROVISIONAL_THRESHOLD_DAYS;

        String confidence = computeConfidence(completeness, missing.size(), inputs.baselineDays());
        String dataQuality = computeDataQuality(completeness, missing.size());

        String missingTreatment = missing.isEmpty()
                ? "All factors present."
                : "Excluded from weighted sum: " + String.join(", ", missing) + ".";

        String explanation = buildExplanation(score, provisional, factors, missing);

        String limitations = "v0.1 weights are initial estimates, not empirically tuned. "
                + "Not a medical measure. Does not reproduce any vendor's proprietary formula.";

        return new ReadinessScore(
                score,
                ReadinessScore.ALGORITHM_VERSION,
                provisional,
                inputs.baselineDays() + "-day rolling",
                confidence,
                dataQuality,
                factors,
                missingTreatment,
                explanation,
                limitations,
                Instant.now()
        );
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String computeConfidence(double completeness, int missingCount, int baselineDays) {
        if (baselineDays < PROVISIONAL_THRESHOLD_DAYS || completeness < 0.5 || missingCount >= 3) {
            return "low";
        }
        if (completeness >= 0.8 && missingCount <= 1) {
            return "high";
        }
        return "medium";
    }

    private static String computeDataQuality(double completeness, int missingCount) {
        if (completeness >= 0.9 && missingCount == 0) return "good";
        if (completeness >= 0.6) return "fair";
        return "poor";
    }

    private static String buildExplanation(int score, boolean provisional,
                                           List<FactorContribution> factors, List<String> missing) {
        StringBuilder sb = new StringBuilder();
        if (provisional) {
            sb.append("Your readiness is provisional (baseline period is insufficient). ");
        }
        sb.append("Readiness is ").append(score).append("/100. ");

        Optional<FactorContribution> topPos = factors.stream()
                .filter(f -> f.direction() == Direction.POSITIVE)
                .max((a, b) -> Double.compare(a.contribution(), b.contribution()));
        Optional<FactorContribution> topNeg = factors.stream()
                .filter(f -> f.direction() == Direction.NEGATIVE)
                .min((a, b) -> Double.compare(a.contribution(), b.contribution()));

        topPos.ifPresent(f -> sb.append("Positive: ").append(f.name())
                .append(" (").append(String.format("%.1f", f.contribution())).append("). "));
        topNeg.ifPresent(f -> sb.append("Negative: ").append(f.name())
                .append(" (").append(String.format("%.1f", f.contribution())).append("). "));

        if (!missing.isEmpty()) {
            sb.append("Missing: ").append(String.join(", ", missing)).append(".");
        }

        return sb.toString().trim();
    }
}