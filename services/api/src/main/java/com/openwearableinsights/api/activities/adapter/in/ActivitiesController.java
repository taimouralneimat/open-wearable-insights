package com.openwearableinsights.api.activities.adapter.in;

import com.openwearableinsights.api.activities.application.ActivityInsightService;
import com.openwearableinsights.api.activities.application.ActivitySessionRepository;
import com.openwearableinsights.api.activities.domain.ActivitySession;
import com.openwearableinsights.api.activities.domain.ActivitySummary;
import com.openwearableinsights.api.activities.domain.ActivityTrendPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    private static final int SESSIONS_LIMIT = 20;

    private final ActivityInsightService activityInsightService;
    private final ActivitySessionRepository activitySessionRepository;

    public ActivitiesController(ActivityInsightService activityInsightService, ActivitySessionRepository activitySessionRepository) {
        this.activityInsightService = activityInsightService;
        this.activitySessionRepository = activitySessionRepository;
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

    @GetMapping("/sessions")
    @Operation(summary = "List recent activity sessions",
            description = "Returns up to the 20 most recent discrete workout sessions (e.g. runs, rides) parsed from connector data — distinct from the day-level step summary above.")
    public List<ActivitySession> getSessions() {
        return activitySessionRepository.findRecent(DEFAULT_ACCOUNT_ID, SESSIONS_LIMIT);
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get activity session detail",
            description = "Returns full detail for one activity session, including HR-zone breakdown when the source device reported it.")
    public ActivitySession getSession(@PathVariable Long id) {
        return activitySessionRepository.findById(DEFAULT_ACCOUNT_ID, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No activity session with id " + id));
    }
}
