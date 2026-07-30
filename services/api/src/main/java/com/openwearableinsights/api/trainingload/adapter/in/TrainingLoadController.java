package com.openwearableinsights.api.trainingload.adapter.in;

import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadSummary;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadTrendPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for training load (ACWR) summary and trend data.
 *
 * <p>Backs docs/product/parity-matrix.md row 2 ("Strain") — the real
 * HR-zone-weighted training load already computed by
 * {@link TrainingLoadService} (and consumed internally by the readiness
 * score) previously had no dedicated external endpoint of its own; this
 * gives a Flutter "Training-load view" (planned separately) something real
 * to build against.
 */
@RestController
@RequestMapping("/api/v1/trainingload")
@Tag(name = "Training Load", description = "Training load (ACWR) summary and daily trend")
public class TrainingLoadController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    // Matches TrainingLoadService's chronic (28d) window, so the trend
    // covers exactly the window the chronic average is drawn from.
    private static final int TREND_DAYS = 28;

    private final TrainingLoadService trainingLoadService;

    public TrainingLoadController(TrainingLoadService trainingLoadService) {
        this.trainingLoadService = trainingLoadService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get training load summary",
            description = "Returns current acute (7d) / chronic (28d) average daily load and the ACWR ratio "
                    + "(acuteLoad / chronicLoad — the identical ratio ReadinessCalculator uses for the readiness "
                    + "score's 'Training load (ACWR)' factor), plus a plain-language loadStatus band. loadStatus "
                    + "is a wellness heuristic based on common sports-science ACWR conventions, not a medical or "
                    + "clinical assessment — see the response's limitations field. Reports an honest 'unknown' "
                    + "status with 'none' confidence when there isn't enough recent activity data.")
    public TrainingLoadSummary getSummary() {
        return trainingLoadService.computeSummary(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/trends")
    @Operation(summary = "Get daily training-load trend",
            description = "Returns real per-day training load (sum of that day's session loads) for up to the "
                    + "last 28 days, matching the chronic window. Only days with at least one activity are "
                    + "included — no zero-fill or interpolation for rest days.")
    public List<TrainingLoadTrendPoint> getTrends() {
        return trainingLoadService.fetchDailyLoadHistory(DEFAULT_ACCOUNT_ID, TREND_DAYS);
    }
}
