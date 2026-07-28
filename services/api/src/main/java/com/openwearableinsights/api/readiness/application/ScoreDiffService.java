package com.openwearableinsights.api.readiness.application;

import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import com.openwearableinsights.api.readiness.domain.ScoreDiff;
import com.openwearableinsights.api.readiness.domain.ScoreDiff.FactorDiff;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes structured diffs between two readiness scores.
 *
 * Given today and a prior day score, returns which factors moved,
 * by how much, and in which direction. This is the transparency
 * feature that makes the product feel explainable.
 */
@Service
public class ScoreDiffService {

    /**
     * Compute the diff between today and a real prior-day readiness score.
     *
     * @param comparedAgainst label describing what "prior" actually is — the
     *                        caller decides this (e.g. "yesterday"); this
     *                        service only computes the numeric diff.
     */
    public ScoreDiff computeDiff(ReadinessScore today, ReadinessScore prior, String comparedAgainst) {
        int scoreDelta = today.score() - prior.score();

        // Build a map of prior factors by name for lookup
        Map<String, FactorContribution> priorFactors = prior.factors().stream()
                .collect(Collectors.toMap(FactorContribution::name, f -> f, (a, b) -> a));

        List<FactorDiff> factorDiffs = new ArrayList<>();

        for (FactorContribution todayFactor : today.factors()) {
            FactorContribution priorFactor = priorFactors.get(todayFactor.name());

            if (priorFactor != null) {
                double valueDelta = todayFactor.value() - priorFactor.value();
                double contributionDelta = todayFactor.contribution() - priorFactor.contribution();

                String direction;
                if (Math.abs(contributionDelta) < 0.01) {
                    direction = "unchanged";
                } else if (contributionDelta > 0) {
                    direction = "improved";
                } else {
                    direction = "worsened";
                }

                factorDiffs.add(new FactorDiff(
                        todayFactor.name(),
                        todayFactor.value(),
                        priorFactor.value(),
                        valueDelta,
                        todayFactor.contribution(),
                        priorFactor.contribution(),
                        contributionDelta,
                        direction
                ));
            } else {
                // Factor exists today but not prior — new factor
                factorDiffs.add(new FactorDiff(
                        todayFactor.name(),
                        todayFactor.value(),
                        0,
                        todayFactor.value(),
                        todayFactor.contribution(),
                        0,
                        todayFactor.contribution(),
                        "improved"
                ));
            }
        }

        // Check for factors that existed prior but not today (removed)
        Map<String, FactorContribution> todayFactors = today.factors().stream()
                .collect(Collectors.toMap(FactorContribution::name, f -> f, (a, b) -> a));
        for (FactorContribution priorFactor : prior.factors()) {
            if (!todayFactors.containsKey(priorFactor.name())) {
                factorDiffs.add(new FactorDiff(
                        priorFactor.name(),
                        0,
                        priorFactor.value(),
                        -priorFactor.value(),
                        0,
                        priorFactor.contribution(),
                        -priorFactor.contribution(),
                        "worsened"
                ));
            }
        }

        // Sort by absolute contribution delta (biggest movers first)
        factorDiffs.sort(Comparator.comparingDouble(
                (FactorDiff fd) -> Math.abs(fd.contributionDelta())
        ).reversed());

        String biggestPositive = factorDiffs.stream()
                .filter(fd -> fd.contributionDelta() > 0.01)
                .max(Comparator.comparingDouble(FactorDiff::contributionDelta))
                .map(FactorDiff::name)
                .orElse(null);

        String biggestNegative = factorDiffs.stream()
                .filter(fd -> fd.contributionDelta() < -0.01)
                .min(Comparator.comparingDouble(FactorDiff::contributionDelta))
                .map(FactorDiff::name)
                .orElse(null);

        String summary = buildSummary(scoreDelta, biggestPositive, biggestNegative);

        return new ScoreDiff(
                today.score(),
                prior.score(),
                scoreDelta,
                factorDiffs,
                summary,
                biggestPositive,
                biggestNegative,
                comparedAgainst,
                today.confidence()
        );
    }

    private String buildSummary(int scoreDelta, String biggestPositive, String biggestNegative) {
        StringBuilder sb = new StringBuilder();

        if (scoreDelta > 0) {
            sb.append("Your readiness improved by ").append(scoreDelta).append(" points. ");
        } else if (scoreDelta < 0) {
            sb.append("Your readiness dropped by ").append(Math.abs(scoreDelta)).append(" points. ");
        } else {
            sb.append("Your readiness is unchanged. ");
        }

        if (biggestPositive != null && biggestNegative != null) {
            sb.append("Biggest improvement: ").append(biggestPositive)
              .append(". Biggest drag: ").append(biggestNegative).append(".");
        } else if (biggestPositive != null) {
            sb.append("Biggest improvement: ").append(biggestPositive).append(".");
        } else if (biggestNegative != null) {
            sb.append("Biggest drag: ").append(biggestNegative).append(".");
        }

        return sb.toString().trim();
    }
}
