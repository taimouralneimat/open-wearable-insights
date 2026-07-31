package com.openwearableinsights.api.sleep;

import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.sleep.application.SleepInsightService;
import com.openwearableinsights.api.sleep.domain.SleepDebt;
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
}
