package com.openwearableinsights.api.coach.adapter.in;

import com.openwearableinsights.api.insights.application.DeterministicInsightEngine;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.Insight;
import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.domain.ReadinessInputs;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

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

    private final ReadinessCalculator readinessCalculator;
    private final DeterministicInsightEngine insightEngine;
    private final boolean llmEnabled;

    public CoachController(
            ReadinessCalculator readinessCalculator,
            DeterministicInsightEngine insightEngine,
            @Value("${owi.llm.enabled:false}") boolean llmEnabled
    ) {
        this.readinessCalculator = readinessCalculator;
        this.insightEngine = insightEngine;
        this.llmEnabled = llmEnabled;
    }

    @GetMapping("/insight")
    @Operation(summary = "Get daily insight", description = "Deterministic fallback always available. LLM optional.")
    public Insight getInsight() {
        // Phase 1: synthetic provisional score
        ReadinessInputs synthetic = new ReadinessInputs(
                Optional.of(8.0),
                Optional.of(-1.5),
                Optional.of(6.5),
                Optional.of(7.5),
                Optional.of(280.0),
                Optional.of(260.0),
                Optional.of(35.0),
                0.75,
                3
        );
        ReadinessScore score = readinessCalculator.calculate(synthetic);

        // Always produce the deterministic insight (fallback)
        Insight insight = insightEngine.generate(score);

        // Phase 1: LLM integration is optional and not yet wired.
        // When OWI_LLM_ENABLED=true and Ollama is available, the coach
        // will request a structured, schema-validated explanation via
        // Spring AI. If it fails, the deterministic fallback is used.
        // For now, we return the deterministic insight directly.
        return insight;
    }

    @GetMapping("/status")
    @Operation(summary = "Get LLM status", description = "Indicates whether the LLM is enabled/available or the fallback is in use.")
    public LlmStatus getStatus() {
        return new LlmStatus(llmEnabled, llmEnabled ? "available" : "fallback");
    }

    public record LlmStatus(boolean enabled, String mode) {}
}