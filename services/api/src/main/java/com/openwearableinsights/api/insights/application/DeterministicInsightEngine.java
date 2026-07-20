package com.openwearableinsights.api.insights.application;

import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.FactorContribution.Direction;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic template-based insight engine.
 *
 * <p>This is the fallback when the LLM is unavailable or produces malformed
 * output. It produces a plain-language summary grounded in the computed
 * readiness score and its factor contributions. No LLM is required.
 *
 * <p>The LLM may explain computed results, identify patterns, summarize
 * trends, and produce coaching language — it never invents or overrides
 * a score. This engine does the same without an LLM.
 */
@Service
public class DeterministicInsightEngine {

    public Insight generate(ReadinessScore score) {
        String headline = buildHeadline(score);
        String summary = buildSummary(score);
        List<FactorSummary> supportingFactors = buildFactors(score);
        List<String> recommendedActions = buildActions(score);
        String confidence = score.confidence();
        List<String> cautions = buildCautions(score);
        List<String> dataLimitations = buildLimitations(score);

        return new Insight(
                headline,
                summary,
                supportingFactors,
                recommendedActions,
                confidence,
                cautions,
                dataLimitations,
                false  // fallbackUsed = false (this IS the fallback)
        );
    }

    private String buildHeadline(ReadinessScore score) {
        if (score.provisional()) {
            return "Provisional readiness: " + score.score() + "/100 (baseline building)";
        }
        if (score.score() >= 75) return "Good readiness: " + score.score() + "/100";
        if (score.score() >= 50) return "Moderate readiness: " + score.score() + "/100";
        return "Low readiness: " + score.score() + "/100";
    }

    private String buildSummary(ReadinessScore score) {
        StringBuilder sb = new StringBuilder();
        sb.append(score.explanation()).append(" ");
        sb.append("Algorithm v").append(score.algorithmVersion()).append(". ");

        Optional<FactorContribution> topPos = score.factors().stream()
                .filter(f -> f.direction() == Direction.POSITIVE)
                .max((a, b) -> Double.compare(a.contribution(), b.contribution()));
        Optional<FactorContribution> topNeg = score.factors().stream()
                .filter(f -> f.direction() == Direction.NEGATIVE)
                .min((a, b) -> Double.compare(a.contribution(), b.contribution()));

        topPos.ifPresent(f -> sb.append("Your strongest factor is ")
                .append(f.name()).append(". "));
        topNeg.ifPresent(f -> sb.append("Your biggest drag is ")
                .append(f.name()).append(". "));

        return sb.toString().trim();
    }

    private List<FactorSummary> buildFactors(ReadinessScore score) {
        return score.factors().stream()
                .map(f -> new FactorSummary(
                        f.name(),
                        String.format("%.1f %s", f.value(), f.unit()),
                        f.direction().name().toLowerCase()
                ))
                .toList();
    }

    private List<String> buildActions(ReadinessScore score) {
        if (score.score() >= 75) {
            return List.of("Consider a harder session today.", "You appear well-recovered.");
        }
        if (score.score() >= 50) {
            return List.of("Consider an easy or moderate session.", "Monitor how you feel during warm-up.");
        }
        return List.of("Consider rest or active recovery.", "Prioritize sleep tonight.");
    }

    private List<String> buildCautions(ReadinessScore score) {
        List<String> cautions = new ArrayList<>();
        cautions.add("This is a wellness metric, not a medical assessment.");
        if (score.provisional()) {
            cautions.add("Score is provisional; baseline is still building.");
        }
        if ("low".equals(score.confidence())) {
            cautions.add("Confidence is low due to incomplete data.");
        }
        return cautions;
    }

    private List<String> buildLimitations(ReadinessScore score) {
        return List.of(
                "v0.1 weights are initial estimates, not empirically tuned.",
                "Does not reproduce any vendor's proprietary formula.",
                "Not a medical device; does not diagnose or treat."
        );
    }

    public record Insight(
            String headline,
            String summary,
            List<FactorSummary> supportingFactors,
            List<String> recommendedActions,
            String confidence,
            List<String> cautions,
            List<String> dataLimitations,
            boolean fallbackUsed
    ) {}

    public record FactorSummary(
            String name,
            String value,
            String direction
    ) {}
}