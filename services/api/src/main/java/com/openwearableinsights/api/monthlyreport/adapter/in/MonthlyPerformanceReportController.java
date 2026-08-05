package com.openwearableinsights.api.monthlyreport.adapter.in;

import com.openwearableinsights.api.monthlyreport.application.MonthlyPerformanceReportService;
import com.openwearableinsights.api.monthlyreport.domain.MonthlyPerformanceReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * REST controller for the monthly/longitudinal performance report.
 *
 * <p>Backs docs/product/parity-matrix.md row 30 ("Monthly/longitudinal
 * performance assessment") -- see {@link MonthlyPerformanceReportService}
 * for the full, disclosed methodology, exactly which real data sources feed
 * it, and why the "28 recovery scores" gate is scoped per reported month.
 * This is not a reproduction of any vendor's proprietary monthly-report
 * design or scoring.
 */
@RestController
@RequestMapping("/api/v1/monthly-report")
@Tag(name = "Monthly report", description = "Real strain/sleep/recovery breakdown for one calendar month, "
        + "gated behind enough real readiness-score history")
public class MonthlyPerformanceReportController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final MonthlyPerformanceReportService monthlyPerformanceReportService;

    public MonthlyPerformanceReportController(MonthlyPerformanceReportService monthlyPerformanceReportService) {
        this.monthlyPerformanceReportService = monthlyPerformanceReportService;
    }

    @GetMapping
    @Operation(summary = "Get the monthly performance report",
            description = "Composes real readiness-score history, training load, and sleep-score trend data "
                    + "already computed elsewhere in this app into an original, fully-disclosed strain/sleep/"
                    + "recovery breakdown for one calendar month. Defaults to the most recently complete month "
                    + "when 'month' is omitted. Returns an honest 'not enough history yet' state (sufficientHistory "
                    + "= false, all three sections null) when the requested month has fewer than 28 real "
                    + "persisted readiness scores -- never a partial report. Never more than 'medium' overall "
                    + "confidence. See the response's methodology, limitations, and per-section fields.")
    public MonthlyPerformanceReport getReport(
            @RequestParam(required = false) String month) {
        if (month == null || month.isBlank()) {
            return monthlyPerformanceReportService.computeReport(DEFAULT_ACCOUNT_ID);
        }
        return monthlyPerformanceReportService.computeReport(DEFAULT_ACCOUNT_ID, YearMonth.parse(month));
    }
}
