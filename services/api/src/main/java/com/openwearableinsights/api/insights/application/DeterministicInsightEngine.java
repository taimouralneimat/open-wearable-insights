package com.openwearableinsights.api.insights.application;

import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.FactorContribution.Direction;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import com.openwearableinsights.api.readiness.domain.ScoreDiff;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
                false,  // fallbackUsed = false (this IS the fallback)
                false   // llmUsed = false (this engine never calls the LLM)
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

    /**
     * Unlike the old version of this method, cites the actual factor behind
     * the recommendation — the same rankedFactors approach explainReadiness
     * uses — rather than a fixed pair of generic strings keyed only on which
     * score bucket the number falls into. A recommendation with no reason
     * attached isn't meaningfully different from a fixed rule; the point of
     * this engine is that every output traces back to a real computed factor.
     */
    private List<String> buildActions(ReadinessScore score) {
        List<FactorContribution> rankedFactors = score.factors().stream()
                .sorted(Comparator.comparingDouble((FactorContribution f) -> Math.abs(f.contribution())).reversed())
                .toList();
        Optional<FactorContribution> topPositive = rankedFactors.stream()
                .filter(f -> f.direction() == Direction.POSITIVE).findFirst();
        Optional<FactorContribution> topNegative = rankedFactors.stream()
                .filter(f -> f.direction() == Direction.NEGATIVE).findFirst();

        List<String> actions = new ArrayList<>();
        if (score.score() >= 75) {
            actions.add("Consider a harder session today.");
            actions.add(topPositive
                    .map(f -> "You appear well-recovered — " + f.name() + " (" + formatValue(f) + ") is your strongest factor.")
                    .orElse("You appear well-recovered."));
        } else if (score.score() >= 50) {
            actions.add("Consider an easy or moderate session.");
            actions.add(topNegative
                    .map(f -> "Monitor how you feel during warm-up — " + f.name() + " (" + formatValue(f) + ") is holding you back a bit.")
                    .orElse("Monitor how you feel during warm-up."));
        } else {
            actions.add("Consider rest or active recovery.");
            actions.add(topNegative
                    .map(f -> f.name() + " (" + formatValue(f) + ") is today's biggest drag — prioritize sleep tonight.")
                    .orElse("Prioritize sleep tonight."));
        }
        return actions;
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
                "Not a medical device; does not identify or manage medical conditions."
        );
    }

    /**
     * Answer "why is my readiness what it is" and, when available, "why did
     * it change" — always grounded in the actual computed factors, never
     * generic advice. Works fully without an LLM; this IS the deterministic
     * fallback the LLM's answer (when enabled) must match or beat.
     *
     * @param diff the score-diff vs. a prior day, if one has been computed
     *             (see ScoreDiffService) — omit if not available
     */
    public WhyAnswer explainReadiness(ReadinessScore score, Optional<ScoreDiff> diff) {
        StringBuilder answer = new StringBuilder();
        answer.append("Your readiness is ").append(score.score()).append("/100. ");

        List<FactorContribution> rankedFactors = score.factors().stream()
                .sorted(Comparator.comparingDouble((FactorContribution f) -> Math.abs(f.contribution())).reversed())
                .toList();

        rankedFactors.stream().findFirst().ifPresent(top -> {
            String verb = top.direction() == Direction.POSITIVE ? "helping most" : "hurting most";
            answer.append(top.name()).append(" is ").append(verb)
                    .append(" (").append(formatValue(top)).append("). ");
        });

        if (score.provisional()) {
            answer.append("This is still provisional — your baseline (")
                    .append(score.baselinePeriod()).append(") needs more days of data " +
                            "before this is fully reliable. ");
        }

        if (diff.isPresent() && "yesterday".equals(diff.get().comparedAgainst())) {
            ScoreDiff d = diff.get();
            if (d.scoreDelta() != 0) {
                answer.append("Compared to yesterday, your score ")
                        .append(d.scoreDelta() > 0 ? "improved" : "dropped")
                        .append(" by ").append(Math.abs(d.scoreDelta())).append(" points");
                if (d.biggestNegative() != null && d.scoreDelta() < 0) {
                    answer.append(", mainly because of ").append(d.biggestNegative());
                } else if (d.biggestPositive() != null && d.scoreDelta() > 0) {
                    answer.append(", mainly thanks to ").append(d.biggestPositive());
                }
                answer.append(". ");
            } else {
                answer.append("That's unchanged from yesterday. ");
            }
        } else {
            answer.append("No prior day's score is recorded yet, so a day-over-day " +
                    "comparison isn't available. ");
        }

        List<CitedMetric> citedMetrics = rankedFactors.stream()
                .map(f -> new CitedMetric(f.name(), formatValue(f),
                        String.format("%.1f", f.contribution()), f.direction().name().toLowerCase()))
                .toList();

        return new WhyAnswer(
                answer.toString().trim(),
                citedMetrics,
                score.confidence(),
                buildLimitations(score),
                false,
                false
        );
    }

    private String formatValue(FactorContribution f) {
        return String.format("%.1f %s", f.value(), f.unit());
    }

    /**
     * Maps a readiness factor to a specific journal behavior worth logging
     * today — the "cue" half of a cue/craving/response/reward loop (Atomic
     * Habits framing), except the cue here is a real physiological signal
     * from the wearable, not a fixed time or location. See
     * journal.application.JournalService's taxonomy for the exact category
     * names these must match.
     *
     * <p>Only fires on the single worst NEGATIVE factor (most negative
     * contribution) — one specific, actionable suggestion, not a checklist.
     * Returns {@code present = false} rather than guessing when there's no
     * negative factor or not enough data to trust one, consistent with this
     * engine's "never fabricate, say so honestly" convention elsewhere.
     */
    public HabitCue suggestHabitCue(ReadinessScore score) {
        Optional<FactorContribution> worst = score.factors().stream()
                .filter(f -> f.direction() == Direction.NEGATIVE)
                .min(Comparator.comparingDouble(FactorContribution::contribution));

        if (worst.isEmpty()) {
            return new HabitCue(false, null, 0, null, null,
                    "No specific cue right now — nothing stands out as the clear factor to act on today.",
                    score.confidence(), false, false);
        }

        FactorContribution f = worst.get();
        Suggestion suggestion = SUGGESTIONS.getOrDefault(f.name(),
                new Suggestion("Recovery", "Full rest day"));

        String reasoning = String.format(
                "%s (%s) is pulling your score down the most today — %s might help.",
                f.name(), formatValue(f), suggestion.behavior().toLowerCase()
        );

        return new HabitCue(true, f.name(), f.contribution(),
                suggestion.category(), suggestion.behavior(), reasoning, score.confidence(), false, false);
    }

    /**
     * Deliberately small and hand-picked, not exhaustive — one reasonable
     * suggestion per factor. Category/behavior strings must exist in
     * journal.application.JournalService's TAXONOMY (not enforced by a
     * shared type across modules; keep them in sync by hand, same as the
     * journal taxonomy itself is a suggested/free-text list, not an enum).
     */
    private static final java.util.Map<String, Suggestion> SUGGESTIONS = java.util.Map.of(
            "HRV deviation", new Suggestion("Mental wellbeing", "Meditation/mindfulness"),
            "RHR deviation", new Suggestion("Recovery", "Active recovery session"),
            "Sleep duration vs. need", new Suggestion("Sleep", "Consistent bedtime"),
            "Training load (ACWR)", new Suggestion("Recovery", "Full rest day"),
            "Stress", new Suggestion("Mental wellbeing", "Time in nature")
    );

    private record Suggestion(String category, String behavior) {}

    /**
     * A single, specific habit suggestion triggered by today's worst real
     * readiness factor — not a static tip list. {@code present = false}
     * means there's honestly nothing to suggest right now.
     */
    /** See {@link Insight}'s field-level javadoc for what {@code fallbackUsed}/{@code llmUsed} distinguish. */
    public record HabitCue(
            boolean present,
            String triggerFactor,
            double triggerContribution,
            String suggestedCategory,
            String suggestedBehavior,
            String reasoning,
            String confidence,
            boolean fallbackUsed,
            boolean llmUsed
    ) {}

    /**
     * A grounded answer to a "why" question, citing the actual factors that
     * produced it. Never generic advice.
     */
    public record WhyAnswer(
            String answer,
            List<CitedMetric> citedMetrics,
            String confidence,
            List<String> limitations,
            boolean fallbackUsed,
            boolean llmUsed
    ) {}

    public record CitedMetric(
            String name,
            String value,
            String contribution,
            String direction
    ) {}

    /**
     * @param fallbackUsed an LLM rephrase was attempted (owi.llm.enabled=true)
     *                     and failed, so this is the deterministic text —
     *                     distinct from the LLM simply never being attempted
     * @param llmUsed      {@code summary} was genuinely produced by the local
     *                     LLM (see {@code LlmInsightService}), not the
     *                     deterministic engine — mutually exclusive with
     *                     {@code fallbackUsed}; both false means the LLM was
     *                     never attempted (disabled)
     */
    public record Insight(
            String headline,
            String summary,
            List<FactorSummary> supportingFactors,
            List<String> recommendedActions,
            String confidence,
            List<String> cautions,
            List<String> dataLimitations,
            boolean fallbackUsed,
            boolean llmUsed
    ) {}

    public record FactorSummary(
            String name,
            String value,
            String direction
    ) {}
}