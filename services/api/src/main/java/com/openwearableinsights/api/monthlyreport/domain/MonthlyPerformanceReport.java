package com.openwearableinsights.api.monthlyreport.domain;

import java.time.Instant;
import java.util.List;

/**
 * The monthly performance report -- docs/product/parity-matrix.md row 30
 * ("Monthly/longitudinal performance assessment"). An original, transparent
 * summary for one calendar month, built entirely from real data this app
 * already computes elsewhere (readiness-score history, training load, sleep
 * trends); not a reproduction of any vendor's proprietary report design or
 * scoring.
 *
 * <p>{@code strain}/{@code sleep}/{@code recovery} are all {@code null}
 * whenever {@code sufficientHistory} is {@code false} -- see {@code
 * MonthlyPerformanceReportService} class Javadoc "Insufficient-history gate"
 * for exactly what unlocks the report and why. This is a deliberate,
 * explicit "not enough history yet" state (per the acceptance criteria in
 * parity-matrix.md row 30), never a partial report built from whatever data
 * happens to exist.
 *
 * @param month                      the reported calendar month, ISO
 *                                   yyyy-MM (e.g. "2026-06")
 * @param algorithmVersion           versioned, deterministic identifier
 * @param sufficientHistory          whether the recovery-score gate passed
 *                                   for this month
 * @param recoveryScoreCount         real persisted readiness scores found in
 *                                   this month
 * @param requiredRecoveryScoreCount the gate threshold (28 -- see service
 *                                   class Javadoc for why this is scoped per
 *                                   month)
 * @param strain                     training-load breakdown, or
 *                                   {@code null} if not enough history
 * @param sleep                      sleep breakdown, or {@code null} if not
 *                                   enough history
 * @param recovery                   readiness-score breakdown, or
 *                                   {@code null} if not enough history
 * @param overallConfidence          "none", "low", or "medium" -- the
 *                                   weakest of the three sections' own
 *                                   confidence, capped at "medium" (this
 *                                   composite never claims "high"); "none"
 *                                   when the gate itself didn't pass
 * @param methodology                plain statement of what this report
 *                                   composes and how -- not any vendor's
 *                                   proprietary formula
 * @param limitations                honest, report-level caveats
 * @param insufficientHistoryMessage plain-language explanation of the gate,
 *                                   present only when
 *                                   {@code sufficientHistory} is false
 * @param generatedAt                computed fresh on every request, like
 *                                   every other analytics endpoint in this
 *                                   app -- no stored snapshot, no scheduled
 *                                   job
 */
public record MonthlyPerformanceReport(
        String month,
        String algorithmVersion,
        boolean sufficientHistory,
        int recoveryScoreCount,
        int requiredRecoveryScoreCount,
        MonthlyMetricSection strain,
        MonthlyMetricSection sleep,
        MonthlyMetricSection recovery,
        String overallConfidence,
        String methodology,
        List<String> limitations,
        String insufficientHistoryMessage,
        Instant generatedAt
) {}
