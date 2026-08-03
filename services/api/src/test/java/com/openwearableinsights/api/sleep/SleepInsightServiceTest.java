package com.openwearableinsights.api.sleep;

import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.sleep.application.SleepInsightService;
import com.openwearableinsights.api.sleep.domain.SleepConsistency;
import com.openwearableinsights.api.sleep.domain.SleepDebt;
import com.openwearableinsights.api.sleep.domain.SleepPlan;
import com.openwearableinsights.api.sleep.domain.SleepSummary;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SleepInsightService}'s personal sleep-need/debt
 * computation — closes parity row #4. Fixes the bug where
 * CurrentMetricsService fed a hardcoded 7.5h "need" into every user's
 * readiness score regardless of their real history (see
 * CurrentMetricsService's updated comment) by finally surfacing and reusing
 * BaselineService's already-correct rolling "sleep_duration" baseline.
 */
class SleepInsightServiceTest {

    private static PersonalBaseline baselineWithSleepNeed(double need) {
        return new PersonalBaseline(
                Map.of("sleep_duration", need),
                Map.of("sleep_duration", 20),
                28, "high", "28-day rolling"
        );
    }

    private static PersonalBaseline emptyBaseline() {
        return new PersonalBaseline(Map.of(), Map.of(), 0, "none", "no data");
    }

    @Test
    void computeSleepDebt_noBaselineYet_reportsHonestEmptyStateNotFabricatedNeed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(1L)).thenReturn(emptyBaseline());

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepDebt debt = service.computeSleepDebt(1L);

        assertThat(debt.neededHoursPerNight()).isNull();
        assertThat(debt.accumulatedHours()).isNull();
        assertThat(debt.confidence()).isEqualTo("none");
        assertThat(debt.limitations()).anyMatch(l -> l.contains("Not enough sleep history"));
    }

    @Test
    void computeSleepDebt_withRealNights_accumulatesDeficitAcrossThem() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(1L)).thenReturn(baselineWithSleepNeed(8.0));

        LocalDate night1 = LocalDate.of(2026, 7, 20);
        LocalDate night2 = LocalDate.of(2026, 7, 21);

        when(jdbc.queryForList(anyString(), eq(LocalDate.class), eq(1L), anyInt()))
                .thenReturn(List.of(night1, night2));

        // night1: 6h actual (8 readings * 0.25h = 2h... use enough readings for 6h = 24 readings)
        mockNight(jdbc, 1L, night1, 24); // 24 * 0.25 = 6.0h
        mockNight(jdbc, 1L, night2, 32); // 32 * 0.25 = 8.0h (meets need exactly)

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepDebt debt = service.computeSleepDebt(1L);

        assertThat(debt.neededHoursPerNight()).isEqualTo(8.0);
        // night1 deficit = 8-6=2.0, night2 deficit = 8-8=0.0 -> accumulated = 2.0
        assertThat(debt.accumulatedHours()).isEqualTo(2.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(debt.nightsConsidered()).isEqualTo(2);
    }

    @Test
    void computeLatestSummary_includesPersonalNeed_whenBaselineExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(1L)).thenReturn(baselineWithSleepNeed(7.75));

        LocalDate date = LocalDate.of(2026, 7, 28);
        when(jdbc.queryForList(anyString(), eq(LocalDate.class), eq(1L)))
                .thenReturn(List.of(date));
        mockNight(jdbc, 1L, date, 28);

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepSummary summary = service.computeLatestSummary(1L).orElseThrow();

        assertThat(summary.sleepNeedHours()).isEqualTo(7.75);
    }

    @Test
    void computeLatestSummary_noBaselineYet_sleepNeedIsNullNotFabricated() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(1L)).thenReturn(emptyBaseline());

        LocalDate date = LocalDate.of(2026, 7, 28);
        when(jdbc.queryForList(anyString(), eq(LocalDate.class), eq(1L)))
                .thenReturn(List.of(date));
        mockNight(jdbc, 1L, date, 28);

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepSummary summary = service.computeLatestSummary(1L).orElseThrow();

        assertThat(summary.sleepNeedHours()).isNull();
        assertThat(summary.limitations()).anyMatch(l -> l.contains("default target"));
    }

    @Test
    void computeSleepPlan_noBaselineOrWakeTimeData_reportsHonestEmptyState() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(1L)).thenReturn(emptyBaseline());

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepPlan plan = service.computeSleepPlan(1L);

        assertThat(plan.recommendedBedtime()).isNull();
        assertThat(plan.targetWakeTime()).isNull();
        assertThat(plan.confidence()).isEqualTo("none");
    }

    @Test
    void computeSleepPlan_withRealData_recommendsBedtimeIncludingDebtRepayment() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(1L)).thenReturn(baselineWithSleepNeed(8.0));

        LocalDate night1 = LocalDate.of(2026, 7, 20);
        LocalDate night2 = LocalDate.of(2026, 7, 21);
        LocalDate night3 = LocalDate.of(2026, 7, 22);
        when(jdbc.queryForList(anyString(), eq(LocalDate.class), eq(1L), anyInt()))
                .thenReturn(List.of(night1, night2, night3));
        mockNight(jdbc, 1L, night1, 24); // 6.0h -> 2.0h deficit
        mockNight(jdbc, 1L, night2, 32); // 8.0h -> 0.0h deficit
        mockNight(jdbc, 1L, night3, 20); // 5.0h -> 3.0h deficit (accumulated = 5.0h)

        // Wake-time query: 3 nights, last reading each at 07:00 UTC.
        Instant wakeBase = Instant.parse("2026-07-22T07:00:00Z");
        List<Map<String, Object>> wakeRows = List.of(
                Map.of("d", night1, "last_reading", Timestamp.from(wakeBase)),
                Map.of("d", night2, "last_reading", Timestamp.from(wakeBase)),
                Map.of("d", night3, "last_reading", Timestamp.from(wakeBase))
        );
        when(jdbc.queryForList(anyString(), eq(1L), eq(7))).thenReturn(wakeRows);

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepPlan plan = service.computeSleepPlan(1L);

        assertThat(plan.targetWakeTime()).isEqualTo("07:00");
        // accumulated debt = 2.0 + 0.0 + 3.0 = 5.0h -> repayment = min(5.0/7, 1.0) = 0.7143h (not capped)
        assertThat(plan.debtRepaymentHours()).isEqualTo(5.0 / 7, org.assertj.core.data.Offset.offset(0.01));
        assertThat(plan.targetSleepHours()).isEqualTo(8.0 + 5.0 / 7, org.assertj.core.data.Offset.offset(0.01));
        // 07:00 - ~8h43m = ~22:17 the night before
        assertThat(plan.recommendedBedtime()).isEqualTo("22:17");
        assertThat(plan.reasoning()).contains("catch up");
    }

    /** Stubs the two per-night queries computeSummaryForDate issues for a given date. */
    private void mockNight(JdbcTemplate jdbc, Long accountId, LocalDate date, int readingCount) {
        Instant base = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        // First query in computeSummaryForDate: SELECT value ... (used only for .size())
        when(jdbc.queryForList(anyString(), eq(accountId), eq(date)))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("ORDER BY time")) {
                        return orderedRows(base, readingCount);
                    }
                    return dummyRows(readingCount);
                });
    }

    private List<Map<String, Object>> dummyRows(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> (Map<String, Object>) Map.<String, Object>of("value", 2))
                .toList();
    }

    private List<Map<String, Object>> orderedRows(Instant base, int count) {
        // Cycle through stage indices 0-3 (deep/rem/light/awake) so totalHours = count * 0.25h.
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(Map.of(
                    "time", Timestamp.from(base.plusSeconds(i * 900L)),
                    "value", i % 4
            ));
        }
        return rows;
    }

    private static Map<String, Object> consistencyRow(LocalDate date, String bedtimeHHmm, String wakeHHmm) {
        return Map.of(
                "bedtime", Timestamp.from(date.atTime(java.time.LocalTime.parse(bedtimeHHmm)).toInstant(ZoneOffset.UTC)),
                "waketime", Timestamp.from(date.plusDays(1).atTime(java.time.LocalTime.parse(wakeHHmm)).toInstant(ZoneOffset.UTC))
        );
    }

    @Test
    void computeSleepConsistency_tooFewNights_returnsHonestEmptyState() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        when(jdbc.queryForList(anyString(), eq(1L), any(Timestamp.class))).thenReturn(List.of(
                consistencyRow(LocalDate.of(2026, 8, 1), "23:00", "07:00"),
                consistencyRow(LocalDate.of(2026, 8, 2), "23:15", "07:10")
        ));

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepConsistency result = service.computeSleepConsistency(1L);

        assertThat(result.consistencyScore()).isNull();
        assertThat(result.confidence()).isEqualTo("none");
        assertThat(result.nightsConsidered()).isEqualTo(2);
        assertThat(String.join(" ", result.limitations())).contains("at least");
    }

    @Test
    void computeSleepConsistency_veryRegularBedtimes_scoresHigh() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        // Same bed/wake time every night (down to the minute) — should score near 100.
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        LocalDate start = LocalDate.of(2026, 8, 1);
        for (int i = 0; i < 7; i++) {
            rows.add(consistencyRow(start.plusDays(i), "23:00", "07:00"));
        }
        when(jdbc.queryForList(anyString(), eq(1L), any(Timestamp.class))).thenReturn(rows);

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepConsistency result = service.computeSleepConsistency(1L);

        assertThat(result.consistencyScore()).isEqualTo(100);
        assertThat(result.avgBedtime()).isEqualTo("23:00");
        assertThat(result.avgWakeTime()).isEqualTo("07:00");
        assertThat(result.bedtimeVarianceMinutes()).isEqualTo(0.0);
    }

    @Test
    void computeSleepConsistency_bedtimesStraddlingMidnight_averageCorrectly() {
        // Real-world case the noon-shift exists for: alternating 23:30 and 00:30
        // bedtimes should average to ~midnight, NOT ~noon (which a naive
        // minutes-since-midnight mean would produce).
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        LocalDate start = LocalDate.of(2026, 8, 1);
        for (int i = 0; i < 6; i++) {
            String bedtime = i % 2 == 0 ? "23:30" : "00:30";
            rows.add(consistencyRow(start.plusDays(i), bedtime, "07:00"));
        }
        when(jdbc.queryForList(anyString(), eq(1L), any(Timestamp.class))).thenReturn(rows);

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepConsistency result = service.computeSleepConsistency(1L);

        // Midpoint of 23:30 and 00:30 is 00:00, not 12:00.
        assertThat(result.avgBedtime()).isEqualTo("00:00");
    }

    @Test
    void computeSleepConsistency_erraticBedtimes_scoresLow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);
        List<Map<String, Object>> rows = List.of(
                consistencyRow(LocalDate.of(2026, 8, 1), "21:00", "05:00"),
                consistencyRow(LocalDate.of(2026, 8, 2), "23:45", "09:30"),
                consistencyRow(LocalDate.of(2026, 8, 3), "01:30", "06:15"),
                consistencyRow(LocalDate.of(2026, 8, 4), "22:15", "10:45"),
                consistencyRow(LocalDate.of(2026, 8, 5), "00:50", "05:50")
        );
        when(jdbc.queryForList(anyString(), eq(1L), any(Timestamp.class))).thenReturn(rows);

        SleepInsightService service = new SleepInsightService(jdbc, baselineService);
        SleepConsistency result = service.computeSleepConsistency(1L);

        assertThat(result.consistencyScore()).isLessThan(70);
    }
}
