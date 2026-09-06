package com.openwearableinsights.api.coach.adapter.in;

import com.openwearableinsights.api.insights.application.DeterministicInsightEngine;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.HabitCue;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.Insight;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.WhyAnswer;
import com.openwearableinsights.api.insights.application.LlmInsightService;
import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.application.CurrentMetricsService;
import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import com.openwearableinsights.api.readiness.application.ScoreDiffService;
import com.openwearableinsights.api.readiness.domain.CurrentMetrics;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import com.openwearableinsights.api.readiness.domain.ScoreDiff;
import com.openwearableinsights.api.shared.LocalDayClock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;

/**
 * REST controller for the daily coach.
 *
 * <p>Every endpoint's facts (score, factors, cited metrics, confidence) are
 * always grounded in the computed readiness score ({@code
 * DeterministicInsightEngine}). When {@code owi.llm.enabled} is true, {@link
 * LlmInsightService} optionally rephrases the one piece of free text each
 * endpoint returns (the insight's summary, the why-answer's answer, the
 * habit-cue's reasoning) via a local Ollama model — see that class's javadoc
 * for exactly what it's allowed to change (never a fact) and this session's
 * live-verification results. Any LLM failure falls back to the deterministic
 * text automatically; the app remains fully usable without the LLM.
 */
@RestController
@RequestMapping("/api/v1/coach")
@Tag(name = "Coach", description = "Daily coach — grounded in computed metrics")
public class CoachController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final ReadinessCalculator readinessCalculator;
    private final BaselineService baselineService;
    private final CurrentMetricsService currentMetricsService;
    private final ScoreDiffService scoreDiffService;
    private final ReadinessScoreHistoryRepository scoreHistoryRepository;
    private final DeterministicInsightEngine insightEngine;
    private final LlmInsightService llmInsightService;
    private final boolean llmEnabled;
    private final LocalDayClock localDayClock;

    public CoachController(
            ReadinessCalculator readinessCalculator,
            BaselineService baselineService,
            CurrentMetricsService currentMetricsService,
            ScoreDiffService scoreDiffService,
            ReadinessScoreHistoryRepository scoreHistoryRepository,
            DeterministicInsightEngine insightEngine,
            LlmInsightService llmInsightService,
            @Value("${owi.llm.enabled:false}") boolean llmEnabled,
            LocalDayClock localDayClock
    ) {
        this.readinessCalculator = readinessCalculator;
        this.baselineService = baselineService;
        this.currentMetricsService = currentMetricsService;
        this.scoreDiffService = scoreDiffService;
        this.scoreHistoryRepository = scoreHistoryRepository;
        this.insightEngine = insightEngine;
        this.llmInsightService = llmInsightService;
        this.llmEnabled = llmEnabled;
        this.localDayClock = localDayClock;
    }

    @GetMapping("/insight")
    @Operation(summary = "Get daily insight",
            description = "Deterministic fallback always available. When owi.llm.enabled=true and a local Ollama "
                    + "model is reachable, the summary sentence is optionally rephrased in a warmer coaching voice "
                    + "by the LLM — every other field (score, factors, confidence, cautions, limitations) always "
                    + "comes from the deterministic engine, never the LLM. llmUsed=true means summary was genuinely "
                    + "LLM-rephrased; fallbackUsed=true means an LLM attempt was made and failed (timeout, "
                    + "unreachable, malformed response) — the two are mutually exclusive, and both false means the "
                    + "LLM was never attempted (disabled).")
    public Insight getInsight() {
        ReadinessScore score = calculateCurrent();

        // Always compute the deterministic insight first — this is the
        // ground truth for every field, and the fallback if the LLM step
        // below is disabled, unavailable, or fails in any way.
        Insight insight = insightEngine.generate(score);

        if (llmEnabled) {
            Optional<String> rephrased = llmInsightService.tryRephraseSummary(score, insight);
            if (rephrased.isPresent()) {
                return withSummary(insight, rephrased.get());
            }
            return withFallbackUsed(insight);
        }

        return insight;
    }

    /** Same insight, LLM-rephrased summary substituted in — every other field stays deterministic. */
    private Insight withSummary(Insight insight, String summary) {
        return new Insight(
                insight.headline(), summary, insight.supportingFactors(), insight.recommendedActions(),
                insight.confidence(), insight.cautions(), insight.dataLimitations(), false, true
        );
    }

    /** Same deterministic insight, but marked as having been reached via a failed LLM attempt, not by config. */
    private Insight withFallbackUsed(Insight insight) {
        return new Insight(
                insight.headline(), insight.summary(), insight.supportingFactors(), insight.recommendedActions(),
                insight.confidence(), insight.cautions(), insight.dataLimitations(), true, false
        );
    }

    @GetMapping("/why")
    @Operation(summary = "Ask why the readiness score is what it is",
            description = "Answers 'why is my readiness what it is' and, when a prior day's score exists, 'why did "
                    + "it change' — always citing the actual computed factors, never generic advice. Works fully "
                    + "without the LLM. When owi.llm.enabled=true, the answer text is optionally rephrased in a "
                    + "warmer coaching voice — citedMetrics/confidence/limitations always come from the "
                    + "deterministic engine, never the LLM. llmUsed=true means answer was genuinely LLM-rephrased; "
                    + "fallbackUsed=true means an LLM attempt was made and failed — mutually exclusive, and both "
                    + "false means the LLM was never attempted (disabled).")
    public WhyAnswer why() {
        ReadinessScore score = calculateCurrent();

        LocalDate today = localDayClock.today();
        Optional<ReadinessScore> priorScore =
                scoreHistoryRepository.findByDate(DEFAULT_ACCOUNT_ID, today.minusDays(1));

        Optional<ScoreDiff> diff = priorScore.map(prior ->
                scoreDiffService.computeDiff(score, prior, "yesterday"));

        WhyAnswer answer = insightEngine.explainReadiness(score, diff);

        if (llmEnabled) {
            Optional<String> rephrased = llmInsightService.tryRephraseWhyAnswer(score, answer);
            if (rephrased.isPresent()) {
                return new WhyAnswer(rephrased.get(), answer.citedMetrics(), answer.confidence(), answer.limitations(), false, true);
            }
            return new WhyAnswer(answer.answer(), answer.citedMetrics(), answer.confidence(), answer.limitations(), true, false);
        }

        return answer;
    }

    @GetMapping("/habit-cue")
    @Operation(summary = "Get today's habit cue",
            description = "One specific journal behavior suggested from today's worst real readiness factor — the "
                    + "cue in a cue/response/reward loop, triggered by an actual wearable signal rather than a "
                    + "fixed time or generic tip list. present=false when there's honestly nothing to suggest. When "
                    + "owi.llm.enabled=true, the reasoning text is optionally rephrased in a warmer coaching voice "
                    + "— every other field always comes from the deterministic engine, never the LLM. llmUsed=true "
                    + "means reasoning was genuinely LLM-rephrased; fallbackUsed=true means an LLM attempt was made "
                    + "and failed — mutually exclusive, and both false means the LLM was never attempted "
                    + "(disabled, or no cue is present so no attempt was made at all).")
    public HabitCue getHabitCue() {
        ReadinessScore score = calculateCurrent();
        HabitCue cue = insightEngine.suggestHabitCue(score);

        if (llmEnabled && cue.present()) {
            Optional<String> rephrased = llmInsightService.tryRephraseHabitCue(cue);
            if (rephrased.isPresent()) {
                return new HabitCue(cue.present(), cue.triggerFactor(), cue.triggerContribution(),
                        cue.suggestedCategory(), cue.suggestedBehavior(), rephrased.get(), cue.confidence(), false, true);
            }
            return new HabitCue(cue.present(), cue.triggerFactor(), cue.triggerContribution(),
                    cue.suggestedCategory(), cue.suggestedBehavior(), cue.reasoning(), cue.confidence(), true, false);
        }

        return cue;
    }

    @GetMapping("/status")
    @Operation(summary = "Get LLM status",
            description = "enabled reflects owi.llm.enabled. Does not live-probe whether the Ollama server is "
                    + "actually reachable right now; a per-request failure there still falls back automatically "
                    + "on every LLM-optional endpoint (see each one's own fallbackUsed field).")
    public LlmStatus getStatus() {
        boolean available = llmEnabled && llmInsightService.isAvailable();
        return new LlmStatus(available, available ? "available" : "fallback");
    }

    private ReadinessScore calculateCurrent() {
        PersonalBaseline baseline = baselineService.computeBaseline();
        CurrentMetrics current = currentMetricsService.fetchCurrent();
        ReadinessScore score = readinessCalculator.calculate(current, baseline);
        scoreHistoryRepository.upsertToday(DEFAULT_ACCOUNT_ID, localDayClock.today(), score);
        return score;
    }

    public record LlmStatus(boolean enabled, String mode) {}
}
