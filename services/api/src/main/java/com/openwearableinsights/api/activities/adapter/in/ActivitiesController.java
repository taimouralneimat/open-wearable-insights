package com.openwearableinsights.api.activities.adapter.in;

import com.openwearableinsights.api.activities.application.ActivityInsightService;
import com.openwearableinsights.api.activities.domain.ActivitySummary;
import com.openwearableinsights.api.activities.domain.ActivityTrendPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for activity data and trends.
 *
 * <p>Computes real step counts from measurement data (see
 * ActivityInsightService). Calories/active minutes/active zone minutes
 * are honestly reported as unavailable rather than fabricated — the data
 * model has no measurement type for them yet.
 */
@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "Activities", description = "Activity summaries and trends")
public class ActivitiesController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;
    private static final int TREND_DAYS = 7;

    private final ActivityInsightService activityInsightService;

    public ActivitiesController(ActivityInsightService activityInsightService) {
        this.activityInsightService = activityInsightService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get activity summary",
            description = "Returns real step count for the most recent day with data. Calories/active minutes/active zone minutes are null — not yet tracked in the data model.")
    public ActivitySummary getSummary() {
        return activityInsightService.computeLatestSummary(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/trends")
    @Operation(summary = "Get activity trends",
            description = "Returns real step-count trends for up to the last 7 days with data.")
    public List<ActivityTrendPoint> getTrends() {
        return activityInsightService.computeStepTrends(DEFAULT_ACCOUNT_ID, TREND_DAYS);
    }
}
