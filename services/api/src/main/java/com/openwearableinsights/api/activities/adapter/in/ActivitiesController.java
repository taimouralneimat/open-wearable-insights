package com.openwearableinsights.api.activities.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for activity data and trends.
 *
 * <p>Phase 2: returns activity summaries and trends from imported data.
 * Currently returns synthetic/placeholder data until the full normalization
 * pipeline is wired.
 */
@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "Activities", description = "Activity summaries and trends")
public class ActivitiesController {

    @GetMapping("/summary")
    @Operation(summary = "Get activity summary",
            description = "Returns daily activity summary (steps, calories, active minutes).")
    public ActivitySummary getSummary() {
        return new ActivitySummary(
                8420, 2340, 67, 5, Instant.now().toString()
        );
    }

    @GetMapping("/trends")
    @Operation(summary = "Get activity trends",
            description = "Returns 7-day activity trend data.")
    public List<ActivityTrendPoint> getTrends() {
        return List.of(
                new ActivityTrendPoint("2026-07-21", 7200, 2100, 45),
                new ActivityTrendPoint("2026-07-22", 9500, 2400, 72),
                new ActivityTrendPoint("2026-07-23", 6100, 1950, 38),
                new ActivityTrendPoint("2026-07-24", 11200, 2650, 85),
                new ActivityTrendPoint("2026-07-25", 8400, 2300, 67),
                new ActivityTrendPoint("2026-07-26", 9800, 2450, 75),
                new ActivityTrendPoint("2026-07-27", 8420, 2340, 67)
        );
    }

    public record ActivitySummary(
            int steps,
            int calories,
            int activeMinutes,
            int activeZoneMinutes,
            String timestamp
    ) {}

    public record ActivityTrendPoint(
            String date,
            int steps,
            int calories,
            int activeMinutes
    ) {}
}
