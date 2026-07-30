package com.openwearableinsights.api.coach.adapter.in;

import com.openwearableinsights.api.insights.application.DeterministicInsightEngine;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.HabitCue;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.Insight;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.WhyAnswer;
import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.application.CurrentMetricsService;
import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import com.openwearableinsights.api.readiness.application.ScoreDiffService;
import com.openwearableinsights.api.readiness.domain.CurrentMetrics;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import com.openwearableinsights.api.readiness.domain.ScoreDiff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * REST controller for the daily coach.
 *
 * <p>Produces a deterministic insight grounded in the computed readiness score.
 * If Ollama is enabled and available, an optional LLM explanation may be
 * produced. If the LLM is unavailable or malformed, the deterministic
 * fallback is used. The app remains fully usable without the LLM.
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
    private final boolean llmEnabled;

    public CoachController(
            ReadinessCalculator readinessCalculator,
            BaselineService baselineService,
            CurrentMetricsService currentMetricsService,
            ScoreDiffService scoreDiffService,
            ReadinessScoreHistoryRepository scoreHistoryRepository,
            DeterministicInsightEngine insightEngine,
            @Value("${owi.llm.enabled:false}") boolean llmEnabled
    ) {
        this.readinessCalculator = readinessCalculator;
        this.baselineService = baselineService;
        this.currentMetricsService = currentMetricsService;
        this.scoreDiffService = scoreDiffService;
        this.scoreHistoryRepository = scoreHistoryRepository;
        this.insightEngine = insightEngine;
        this.llmEnabled = llmEnabled;
    }

    @GetMapping("/insight")
    @Operation(summary = "Get daily insight", description = "Deterministic fallback always available. LLM optional.")
    public Insight getInsight() {
        ReadinessScore score = calculateCurrent();

        // Always produce the deterministic insight (fallback)
        Insight insight = insightEngine.generate(score);

        // Phase 1: LLM integration is optional and not yet wired.
        // When OWI_LLM_ENABLED=true and Ollama is available, the coach
        // will request a structured, schema-validated explanation via
        // Spring AI. If it fails, the deterministic fallback is used.
        // For now, we return the deterministic insight directly.
        return insight;
    }

    @GetMapping("/why")
    @Operation(summary = "Ask why the readiness score is what it is",
            description = "Answers 'why is my readiness what it is' and, when a prior day's score exists, 'why did it change' — always citing the actual computed factors, never generic advice. Works fully without the LLM.")
    public WhyAnswer why() {
        ReadinessScore score = calculateCurrent();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Optional<ReadinessScore> priorScore =
                scoreHistoryRepository.findByDate(DEFAULT_ACCOUNT_ID, today.minusDays(1));

        Optional<ScoreDiff> diff = priorScore.map(prior ->
                scoreDiffService.computeDiff(score, prior, "yesterday"));

        return insightEngine.explainReadiness(score, diff);
    }

    @GetMapping("/habit-cue")
    @Operation(summary = "Get today's habit cue",
            description = "One specific journal behavior suggested from today's worst real readiness factor — the cue in a cue/response/reward loop, triggered by an actual wearable signal rather than a fixed time or generic tip list. present=false when there's honestly nothing to suggest.")
    public HabitCue getHabitCue() {
        ReadinessScore score = calculateCurrent();
        return insightEngine.suggestHabitCue(score);
    }

    @GetMapping("/status")
    @Operation(summary = "Get LLM status", description = "Indicates whether the LLM is enabled/available or the fallback is in use.")
    public LlmStatus getStatus() {
        return new LlmStatus(llmEnabled, llmEnabled ? "available" : "fallback");
    }

    private ReadinessScore calculateCurrent() {
        PersonalBaseline baseline = baselineService.computeBaseline();
        CurrentMetrics current = currentMetricsService.fetchCurrent();
        ReadinessScore score = readinessCalculator.calculate(current, baseline);
        scoreHistoryRepository.upsertToday(DEFAULT_ACCOUNT_ID, LocalDate.now(ZoneOffset.UTC), score);
        return score;
    }

    public record LlmStatus(boolean enabled, String mode) {}
}
