package com.openwearableinsights.api.administration.adapter.in;

import com.openwearableinsights.api.administration.application.DataQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the data-quality dashboard.
 *
 * <p>Computes real per-metric coverage, freshness, and issues from the
 * measurements table (see DataQualityService) — previously returned
 * hardcoded placeholder values unconditionally (parity-matrix row #12).
 */
@RestController
@RequestMapping("/api/v1/data-quality")
@Tag(name = "Data Quality", description = "Data-quality dashboard metrics")
public class DataQualityController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get data-quality summary",
            description = "Returns real per-metric coverage, freshness, and issues computed from the last 7 days of measurement data.")
    public DataQualitySummary getSummary() {
        return dataQualityService.computeSummary(DEFAULT_ACCOUNT_ID);
    }

    public record DataQualitySummary(
            double completeness,
            String freshness,
            int daysOfData,
            int totalSources,
            List<MetricQuality> metrics,
            List<String> issues,
            String algorithmVersion,
            String confidence,
            List<String> limitations
    ) {}

    public record MetricQuality(
            String metric,
            double coverage,
            String quality,
            String frequency
    ) {}
}
