package com.openwearableinsights.api.readiness.adapter.in;

import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.application.CurrentMetricsService;
import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import com.openwearableinsights.api.readiness.application.ScoreDiffService;
import com.openwearableinsights.api.readiness.domain.CurrentMetrics;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.readiness.domain.ReadinessInputs;
import com.openwearableinsights.api.readiness.domain.ScoreDiff;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for readiness scores.
 *
 * <p>Phase 1: accepts inputs directly (synthetic data). Phase 2+ will read
 * from the database via the normalization module.
 */
@RestController
@RequestMapping("/api/v1/readiness")
@Tag(name = "Readiness", description = "Versioned, deterministic readiness scores")
public class ReadinessController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final ReadinessCalculator calculator;
    private final BaselineService baselineService;
    private final CurrentMetricsService currentMetricsService;
    private final ScoreDiffService scoreDiffService;
    private final ReadinessScoreHistoryRepository scoreHistoryRepository;

    public ReadinessController(
            ReadinessCalculator calculator,
            BaselineService baselineService,
            CurrentMetricsService currentMetricsService,
            ScoreDiffService scoreDiffService,
            ReadinessScoreHistoryRepository scoreHistoryRepository
    ) {
        this.calculator = calculator;
        this.baselineService = baselineService;
        this.currentMetricsService = currentMetricsService;
        this.scoreDiffService = scoreDiffService;
        this.scoreHistoryRepository = scoreHistoryRepository;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate readiness from inputs", description = "Deterministic, versioned. The LLM never computes this.")
    public ReadinessScore calculate(@Valid @RequestBody ReadinessRequest request) {
        ReadinessInputs inputs = new ReadinessInputs(
                Optional.ofNullable(request.hrvDeviationMs()),
                Optional.ofNullable(request.rhrDeviationBpm()),
                Optional.ofNullable(request.sleepDurationHours()),
                Optional.ofNullable(request.sleepNeedHours()),
                Optional.ofNullable(request.acuteLoad()),
                Optional.ofNullable(request.chronicLoad()),
                Optional.ofNullable(request.stressScore()),
                request.dataCompleteness(),
                request.baselineDays()
        );
        return calculator.calculate(inputs);
    }

    @GetMapping("/latest")
    @Operation(summary = "Get latest readiness with personalized baseline",
            description = "Computes readiness from the user own measurement history using a personalized rolling baseline. Falls back to provisional if no data exists.")
    public ReadinessScore latest() {
        PersonalBaseline baseline = baselineService.computeBaseline();
        CurrentMetrics current = currentMetricsService.fetchCurrent();
        ReadinessScore score = calculator.calculate(current, baseline);
        scoreHistoryRepository.upsertToday(DEFAULT_ACCOUNT_ID, LocalDate.now(ZoneOffset.UTC), score);
        return score;
    }

    @GetMapping("/baseline")
    @Operation(summary = "Get personalized baseline",
            description = "Returns the user personalized rolling baseline with per-metric values, sample sizes, and honest confidence assessment.")
    public PersonalBaseline baseline() {
        return baselineService.computeBaseline();
    }

    @GetMapping("/diff")
    @Operation(summary = "Get score diff vs prior day",
            description = "Returns a structured diff between today and yesterday actual persisted readiness score: which factors moved, by how much, in which direction. If no prior day has been recorded yet, says so explicitly rather than fabricating a comparison.")
    public ScoreDiff diff() {
        PersonalBaseline baseline = baselineService.computeBaseline();
        CurrentMetrics current = currentMetricsService.fetchCurrent();
        ReadinessScore today = calculator.calculate(current, baseline);
        LocalDate todayDate = LocalDate.now(ZoneOffset.UTC);
        scoreHistoryRepository.upsertToday(DEFAULT_ACCOUNT_ID, todayDate, today);

        Optional<ReadinessScore> priorScore =
                scoreHistoryRepository.findByDate(DEFAULT_ACCOUNT_ID, todayDate.minusDays(1));

        if (priorScore.isPresent()) {
            return scoreDiffService.computeDiff(today, priorScore.get(), "yesterday");
        }

        // No real prior day recorded yet — say so honestly rather than
        // fabricating a comparison against the baseline average.
        return new ScoreDiff(
                today.score(),
                today.score(),
                0,
                List.of(),
                "No prior day's score has been recorded yet — check back tomorrow for a comparison.",
                null,
                null,
                "no_prior_data"
        );
    }

    public record ReadinessRequest(
            Double hrvDeviationMs,
            Double rhrDeviationBpm,
            Double sleepDurationHours,
            Double sleepNeedHours,
            Double acuteLoad,
            Double chronicLoad,
            Double stressScore,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") double dataCompleteness,
            @Min(0) int baselineDays
    ) {}
}