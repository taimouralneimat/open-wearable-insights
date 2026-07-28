package com.openwearableinsights.api.sleep.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for sleep data.
 *
 * <p>Phase 2: returns sleep summaries and stage breakdowns.
 * Currently returns synthetic/placeholder data until the full normalization
 * pipeline is wired.
 */
@RestController
@RequestMapping("/api/v1/sleep")
@Tag(name = "Sleep", description = "Sleep summaries and stage breakdowns")
public class SleepController {

    // Not derived from any real computation — see PLACEHOLDER_LIMITATIONS.
    private static final String PLACEHOLDER_CONFIDENCE = "none";
    private static final List<String> PLACEHOLDER_LIMITATIONS = List.of(
            "This is placeholder/synthetic data, not computed from your real " +
            "sleep sessions — the full normalization pipeline has not been " +
            "wired yet (Phase 2 known limitation, see docs/qa/phase-gate-report-phase2.md).",
            "Do not treat these values as reflective of actual sleep quality."
    );

    @GetMapping("/summary")
    @Operation(summary = "Get latest sleep summary",
            description = "Returns last night's sleep summary with stage breakdown. Currently placeholder data — see limitations field.")
    public SleepSummary getSummary() {
        return new SleepSummary(
                7.2, // total hours
                1.5, // deep hours
                1.8, // rem hours
                3.4, // light hours
                0.5, // awake hours
                85,  // sleep score
                List.of(
                        new SleepStagePoint("23:00", "light"),
                        new SleepStagePoint("23:15", "deep"),
                        new SleepStagePoint("01:00", "deep"),
                        new SleepStagePoint("01:30", "rem"),
                        new SleepStagePoint("03:00", "light"),
                        new SleepStagePoint("04:00", "rem"),
                        new SleepStagePoint("05:00", "light"),
                        new SleepStagePoint("06:00", "awake")
                ),
                PLACEHOLDER_CONFIDENCE,
                PLACEHOLDER_LIMITATIONS
        );
    }

    @GetMapping("/trends")
    @Operation(summary = "Get sleep trends",
            description = "Returns 7-day sleep trend data.")
    public List<SleepTrendPoint> getTrends() {
        return List.of(
                new SleepTrendPoint("2026-07-21", 6.5, 1.2, 1.5, 3.3, 0.5, 72),
                new SleepTrendPoint("2026-07-22", 7.8, 1.6, 1.9, 3.7, 0.6, 88),
                new SleepTrendPoint("2026-07-23", 5.9, 1.0, 1.3, 3.1, 0.5, 65),
                new SleepTrendPoint("2026-07-24", 8.1, 1.7, 2.0, 3.8, 0.6, 90),
                new SleepTrendPoint("2026-07-25", 7.0, 1.4, 1.7, 3.4, 0.5, 80),
                new SleepTrendPoint("2026-07-26", 7.5, 1.5, 1.8, 3.6, 0.6, 85),
                new SleepTrendPoint("2026-07-27", 7.2, 1.5, 1.8, 3.4, 0.5, 85)
        );
    }

    public record SleepSummary(
            double totalHours,
            double deepHours,
            double remHours,
            double lightHours,
            double awakeHours,
            int sleepScore,
            List<SleepStagePoint> stages,
            String confidence,
            List<String> limitations
    ) {}

    public record SleepStagePoint(
            String time,
            String stage
    ) {}

    public record SleepTrendPoint(
            String date,
            double totalHours,
            double deepHours,
            double remHours,
            double lightHours,
            double awakeHours,
            int sleepScore
    ) {}
}
