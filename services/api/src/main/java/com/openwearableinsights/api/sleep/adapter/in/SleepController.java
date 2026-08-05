package com.openwearableinsights.api.sleep.adapter.in;

import com.openwearableinsights.api.sleep.application.SleepInsightService;
import com.openwearableinsights.api.sleep.domain.SleepConsistency;
import com.openwearableinsights.api.sleep.domain.SleepDebt;
import com.openwearableinsights.api.sleep.domain.SleepPlan;
import com.openwearableinsights.api.sleep.domain.SleepSummary;
import com.openwearableinsights.api.sleep.domain.SleepTrendPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for sleep data.
 *
 * <p>Computes real sleep summaries from sleep_stage measurements when
 * available (see SleepInsightService), with an honest confidence
 * assessment based on reading density. Returns an explicit empty/low-
 * confidence result rather than fabricated data when no sleep data exists.
 */
@RestController
@RequestMapping("/api/v1/sleep")
@Tag(name = "Sleep", description = "Sleep summaries and stage breakdowns")
public class SleepController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;
    private static final int DEFAULT_TREND_DAYS = 7;
    // A Garmin Connect historical sync can bring in a year+ of real data —
    // this just bounds a single request's query cost, not what's storable.
    private static final int MAX_TREND_DAYS = 366;

    private final SleepInsightService sleepInsightService;

    public SleepController(SleepInsightService sleepInsightService) {
        this.sleepInsightService = sleepInsightService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get latest sleep summary",
            description = "Returns the most recent night's sleep summary computed from real sleep_stage measurements, with an honest confidence assessment. Empty stages/zero score if no sleep data exists yet.")
    public SleepSummary getSummary() {
        return sleepInsightService.computeLatestSummary(DEFAULT_ACCOUNT_ID)
                .orElseGet(() -> new SleepSummary(
                        0, 0, 0, 0, 0, 0, 0, null, List.of(), "none",
                        List.of("No sleep data has been imported yet.")
                ));
    }

    @GetMapping("/trends")
    @Operation(summary = "Get sleep trends",
            description = "Returns sleep trend data for up to the last N nights with real data (default 7, max " + MAX_TREND_DAYS + "). Returns fewer points if less history exists. "
                    + "Beyond 31 days, points are weekly averages instead of nightly values; beyond 120, monthly — "
                    + "each point's granularity field says which.")
    public List<SleepTrendPoint> getTrends(@RequestParam(required = false) Integer days) {
        int window = Math.min(days != null && days > 0 ? days : DEFAULT_TREND_DAYS, MAX_TREND_DAYS);
        return sleepInsightService.computeTrends(DEFAULT_ACCOUNT_ID, window);
    }

    @GetMapping("/debt")
    @Operation(summary = "Get accumulated sleep debt",
            description = "Returns the account's personal sleep need (rolling baseline, same figure the readiness score uses) and accumulated debt/surplus over the last 14 nights with real data. Honest empty state when there isn't enough history yet — never a fabricated need.")
    public SleepDebt getDebt() {
        return sleepInsightService.computeSleepDebt(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/plan")
    @Operation(summary = "Get tonight's bedtime recommendation",
            description = "Recommends a bedtime based on the account's inferred usual wake time, personal sleep need, and a capped gradual repayment of accumulated debt — with the reasoning shown, not just a number. Honest empty state when there isn't enough real history for either input.")
    public SleepPlan getPlan() {
        return sleepInsightService.computeSleepPlan(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/consistency")
    @Operation(summary = "Get sleep consistency (bed/wake time regularity)",
            description = "How regular bed/wake times have been over the last 14 nights — a 0-100 score plus average bed/wake times, distinct from sleep duration/debt. Honest empty state when there isn't enough recent history.")
    public SleepConsistency getConsistency() {
        return sleepInsightService.computeSleepConsistency(DEFAULT_ACCOUNT_ID);
    }
}
