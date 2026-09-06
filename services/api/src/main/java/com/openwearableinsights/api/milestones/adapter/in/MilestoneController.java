package com.openwearableinsights.api.milestones.adapter.in;

import com.openwearableinsights.api.milestones.application.MilestoneService;
import com.openwearableinsights.api.milestones.domain.MilestonesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the "Personal Records / Milestones" feature — an
 * original delight surface, not a competitor-parity row. See {@link
 * MilestoneService} for exactly which real, already-computed data this
 * composes and why each comparison is a genuine, honest "new personal best"
 * rather than a fabricated one.
 */
@RestController
@RequestMapping("/api/v1/milestones")
@Tag(name = "Milestones", description = "Genuine personal records - real comparisons against your own historical data, never fabricated")
public class MilestoneController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping
    @Operation(summary = "Get current genuine personal records/milestones",
            description = "Checks habit streaks, weekly steps, monthly VO2max, and strength-training periods for a "
                    + "genuine new personal best, each a real comparison against the account's own real historical "
                    + "data already computed elsewhere in this app - no LLM involvement, no fabricated or inferred "
                    + "records. Returns an honest empty list when nothing genuinely new was found. See the "
                    + "response's limitations field for exactly what each check does and doesn't cover.")
    public MilestonesResponse getMilestones() {
        return milestoneService.computeMilestones(DEFAULT_ACCOUNT_ID);
    }
}
