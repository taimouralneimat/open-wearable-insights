package com.openwearableinsights.api.administration.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for data-quality dashboard.
 *
 * <p>Phase 2: returns data-quality metrics including completeness, freshness,
 * coverage, and quality flags. Currently returns synthetic/placeholder data
 * until the full normalization pipeline is wired.
 */
@RestController
@RequestMapping("/api/v1/data-quality")
@Tag(name = "Data Quality", description = "Data-quality dashboard metrics")
public class DataQualityController {

    @GetMapping("/summary")
    @Operation(summary = "Get data-quality summary",
            description = "Returns overall data-quality metrics: completeness, freshness, coverage, issues.")
    public DataQualitySummary getSummary() {
        return new DataQualitySummary(
                0.78, // completeness fraction
                "fresh", // freshness
                7, // days of data
                3, // total sources
                List.of(
                        new MetricQuality("HRV", 0.85, "good", "4 readings/day"),
                        new MetricQuality("RHR", 0.90, "good", "4 readings/day"),
                        new MetricQuality("Sleep", 0.70, "fair", "1 session/day"),
                        new MetricQuality("Steps", 0.95, "good", "continuous"),
                        new MetricQuality("Calories", 0.80, "good", "daily total"),
                        new MetricQuality("Stress", 0.60, "fair", "4 readings/day"),
                        new MetricQuality("SpO2", 0.40, "poor", "intermittent"),
                        new MetricQuality("Respiration", 0.50, "fair", "intermittent")
                ),
                List.of(
                        "SpO2 coverage is low (40%) — sensor may not be recording continuously.",
                        "Stress coverage is fair (60%) — consider enabling all-day stress tracking.",
                        "Sleep data has a 2-day gap in the last 7 days."
                ),
                "v0.1"
        );
    }

    public record DataQualitySummary(
            double completeness,
            String freshness,
            int daysOfData,
            int totalSources,
            List<MetricQuality> metrics,
            List<String> issues,
            String algorithmVersion
    ) {}

    public record MetricQuality(
            String metric,
            double coverage,
            String quality,
            String frequency
    ) {}
}
