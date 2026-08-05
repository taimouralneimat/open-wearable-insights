package com.openwearableinsights.api.sleep.domain;

import java.util.List;

/**
 * A nightly bedtime recommendation — parity-matrix row #20, the direct
 * follow-on to sleep need/debt (row #4): "recommends bedtime/wake time with
 * the reasoning shown, not just the deficit number."
 *
 * <p>{@code targetWakeTime} is inferred from the account's own recent wake
 * pattern (average time-of-day of the last sleep_stage reading each night),
 * not asked for explicitly — reuses real data rather than adding a new
 * required input. All fields null/empty until there's enough real history;
 * never a fabricated recommendation.
 *
 * @param debtRepaymentHours   how much of tonight's target duration is extra
 *                             catch-up for accumulated debt, on top of the
 *                             plain personal need (0 if no debt or no data)
 * @param strainAdjustmentHours how much of tonight's target duration is a
 *                             modest extra allowance for elevated/high
 *                             recent training load (0 if load is normal or
 *                             unavailable) — additive to, and disclosed
 *                             separately from, {@code debtRepaymentHours};
 *                             see {@code SleepInsightService#computeStrainAdjustment}
 */
public record SleepPlan(
        String recommendedBedtime,
        String targetWakeTime,
        Double targetSleepHours,
        double debtRepaymentHours,
        double strainAdjustmentHours,
        String reasoning,
        String confidence,
        List<String> limitations
) {}
