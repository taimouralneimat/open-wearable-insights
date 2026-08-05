package com.openwearableinsights.api.healthspan.adapter.in;

import com.openwearableinsights.api.healthspan.application.HealthspanService;
import com.openwearableinsights.api.healthspan.domain.HealthspanSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the composite healthspan/wellness score.
 *
 * <p>Backs docs/product/parity-matrix.md row 24 ("Healthspan / biological
 * age score") — see {@link HealthspanService} for the full, disclosed,
 * original methodology and exactly which real data sources feed it. This is
 * not a reproduction of any vendor's proprietary biological-age formula and
 * was not developed with or validated by any external research institute.
 */
@RestController
@RequestMapping("/api/v1/healthspan")
@Tag(name = "Healthspan", description = "Composite wellness score - a wellness estimate, not a medical or actuarial age")
public class HealthspanController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final HealthspanService healthspanService;

    public HealthspanController(HealthspanService healthspanService) {
        this.healthspanService = healthspanService;
    }

    @GetMapping
    @Operation(summary = "Get the composite healthspan/wellness score (30-day and 6-month)",
            description = "Composes real VO2max, Strength Activity Time, resting-heart-rate, sleep-consistency, "
                    + "and training-load data already computed elsewhere in this app into an original, "
                    + "fully-disclosed composite score comparing your own recent period against your own prior "
                    + "history. Not a reproduction of any vendor's proprietary biological-age formula, not "
                    + "developed with or validated by any external research institute, and never more than "
                    + "'medium' confidence. Reports an honest empty score (confidence 'none') per window when "
                    + "there isn't enough real history yet across enough contributing metrics. See the response's "
                    + "disclaimer, methodology, factors, and limitations fields.")
    public HealthspanSummary getSummary() {
        return healthspanService.computeSummary(DEFAULT_ACCOUNT_ID);
    }
}
