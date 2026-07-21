package com.openwearableinsights.api.readiness.adapter.in;

import com.openwearableinsights.api.readiness.application.ReadinessCalculator;
import com.openwearableinsights.api.readiness.domain.ReadinessInputs;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

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

    private final ReadinessCalculator calculator;

    public ReadinessController(ReadinessCalculator calculator) {
        this.calculator = calculator;
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
    @Operation(summary = "Get latest readiness (synthetic default)", description = "Returns a provisional score from synthetic data for Phase 1.")
    public ReadinessScore latest() {
        // Phase 1: return a synthetic provisional score
        ReadinessInputs synthetic = new ReadinessInputs(
                Optional.of(8.0),    // HRV above baseline
                Optional.of(-1.5),   // RHR below baseline (good)
                Optional.of(6.5),    // 6.5h sleep
                Optional.of(7.5),     // 7.5h need -> deficit
                Optional.of(280.0),   // acute load
                Optional.of(260.0),   // chronic load
                Optional.of(35.0),    // stress
                0.75,                 // completeness
                3                     // baseline days (provisional)
        );
        return calculator.calculate(synthetic);
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