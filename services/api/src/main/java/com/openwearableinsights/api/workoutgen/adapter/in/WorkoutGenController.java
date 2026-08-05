package com.openwearableinsights.api.workoutgen.adapter.in;

import com.openwearableinsights.api.workoutgen.application.WorkoutGeneratorService;
import com.openwearableinsights.api.workoutgen.domain.Equipment;
import com.openwearableinsights.api.workoutgen.domain.GeneratedWorkout;
import com.openwearableinsights.api.workoutgen.domain.Goal;
import com.openwearableinsights.api.workoutgen.domain.Limitation;
import com.openwearableinsights.api.workoutgen.domain.WorkoutGenerationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * REST controller for the deterministic workout generator — docs/product/
 * parity-matrix.md row 26. See {@link WorkoutGeneratorService}'s class
 * Javadoc for the full algorithm; this controller is a thin delegate (same
 * "controllers are thin, logic lives in the service" convention every other
 * controller in this app follows) that only maps the request DTO onto
 * {@link WorkoutGenerationRequest} and defaults the account to this
 * single-user app's account 1.
 */
@RestController
@RequestMapping("/api/v1/workout-gen")
@Tag(name = "Workout Generator", description = "Deterministic, template-based custom workout generation")
public class WorkoutGenController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final WorkoutGeneratorService workoutGeneratorService;

    public WorkoutGenController(WorkoutGeneratorService workoutGeneratorService) {
        this.workoutGeneratorService = workoutGeneratorService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate a custom workout",
            description = "Deterministically generates a warmup/main/cooldown workout from a stated goal, "
                    + "available equipment, and physical limitations, using this app's own curated exercise "
                    + "template library - no LLM is called (see the response's disclaimer and source fields). "
                    + "The same request always produces the same workout. When real recent training-load data "
                    + "shows a 'high' overtraining-risk signal, the plan is automatically lightened and "
                    + "intensityAdjustment explains why.")
    public GeneratedWorkout generate(@RequestBody GenerateWorkoutRequest request) {
        WorkoutGenerationRequest domainRequest = new WorkoutGenerationRequest(
                request.goal(),
                request.equipment(),
                request.limitations(),
                request.durationMinutes(),
                DEFAULT_ACCOUNT_ID
        );
        return workoutGeneratorService.generate(domainRequest);
    }

    public record GenerateWorkoutRequest(
            Goal goal,
            Set<Equipment> equipment,
            Set<Limitation> limitations,
            Integer durationMinutes
    ) {}
}
