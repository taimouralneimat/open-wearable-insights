package com.openwearableinsights.api.activities;

import com.openwearableinsights.api.activities.application.ActivityInsightService;
import com.openwearableinsights.api.activities.domain.ActivitySummary;
import com.openwearableinsights.api.activities.domain.ActivityTrendPoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActivityInsightService}, focused on the calories/
 * active-minutes fields added once a Garmin Connect sync can provide them —
 * previously always null/unavailable regardless of data source.
 */
class ActivityInsightServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    @Test
    void noStepData_returnsEmptySummaryNotError() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("LIMIT 1"),
                eq(LocalDate.class), eq(ACCOUNT_ID))).thenReturn(List.of());

        ActivityInsightService service = new ActivityInsightService(jdbc);
        ActivitySummary summary = service.computeLatestSummary(ACCOUNT_ID);

        assertThat(summary.steps()).isZero();
        assertThat(summary.calories()).isNull();
        assertThat(summary.confidence()).isEqualTo("none");
    }

    @Test
    void fitOnlyData_stepsRealButCaloriesAndActiveMinutesNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("LIMIT 1"),
                eq(LocalDate.class), eq(ACCOUNT_ID))).thenReturn(List.of(DATE));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("SUM(value), 0"),
                eq(Integer.class), eq(ACCOUNT_ID), eq(DATE))).thenReturn(8342);
        // No calories/intensity-minutes rows exist for a FIT-only account.
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("COUNT(*)"),
                eq(Integer.class), eq(ACCOUNT_ID), org.mockito.ArgumentMatchers.anyString(), eq(DATE)))
                .thenReturn(0);

        ActivityInsightService service = new ActivityInsightService(jdbc);
        ActivitySummary summary = service.computeLatestSummary(ACCOUNT_ID);

        assertThat(summary.steps()).isEqualTo(8342);
        assertThat(summary.calories()).isNull();
        assertThat(summary.activeMinutes()).isNull();
        assertThat(summary.activeZoneMinutes()).isNull();
        assertThat(String.join(" ", summary.limitations())).contains("Garmin Connect sync");
    }

    @Test
    void garminConnectSyncedData_realCaloriesAndActiveMinutes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("LIMIT 1"),
                eq(LocalDate.class), eq(ACCOUNT_ID))).thenReturn(List.of(DATE));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("SUM(value), 0"),
                eq(Integer.class), eq(ACCOUNT_ID), eq(DATE))).thenReturn(8342);

        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("COUNT(*)"),
                eq(Integer.class), eq(ACCOUNT_ID), eq("calories"), eq(DATE))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("SUM(value)"),
                eq(Double.class), eq(ACCOUNT_ID), eq("calories"), eq(DATE))).thenReturn(2450.0);

        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("COUNT(*)"),
                eq(Integer.class), eq(ACCOUNT_ID), eq("intensity_minutes_moderate"), eq(DATE))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("SUM(value)"),
                eq(Double.class), eq(ACCOUNT_ID), eq("intensity_minutes_moderate"), eq(DATE))).thenReturn(30.0);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("COUNT(*)"),
                eq(Integer.class), eq(ACCOUNT_ID), eq("intensity_minutes_vigorous"), eq(DATE))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("SUM(value)"),
                eq(Double.class), eq(ACCOUNT_ID), eq("intensity_minutes_vigorous"), eq(DATE))).thenReturn(15.0);

        ActivityInsightService service = new ActivityInsightService(jdbc);
        ActivitySummary summary = service.computeLatestSummary(ACCOUNT_ID);

        assertThat(summary.steps()).isEqualTo(8342);
        assertThat(summary.calories()).isEqualTo(2450);
        assertThat(summary.activeMinutes()).isEqualTo(45); // 30 moderate + 15 vigorous
        assertThat(summary.activeZoneMinutes()).isNull(); // still genuinely untracked
    }

    @Test
    void computeStepTrends_smallWindow_returnsDailyPoints() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("GROUP BY DATE(time)"), eq(ACCOUNT_ID), eq(7))).thenReturn(List.of(
                Map.of("day", Date.valueOf(LocalDate.of(2026, 8, 3)), "total", 9000),
                Map.of("day", Date.valueOf(LocalDate.of(2026, 8, 2)), "total", 8000),
                Map.of("day", Date.valueOf(LocalDate.of(2026, 8, 1)), "total", 7000)
        ));

        List<ActivityTrendPoint> trends = new ActivityInsightService(jdbc).computeStepTrends(ACCOUNT_ID, 7);

        assertThat(trends).hasSize(3);
        assertThat(trends).allMatch(t -> "day".equals(t.granularity()));
        assertThat(trends.get(0).date()).isEqualTo("2026-08-01");
        assertThat(trends.get(0).steps()).isEqualTo(7000);
        assertThat(trends.get(2).date()).isEqualTo("2026-08-03");
    }

    @Test
    void computeStepTrends_beyond31Days_rollsUpToWeeklyAverages() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Two real days in the same Mon-Sun week (2026-08-03 is a Monday).
        when(jdbc.queryForList(contains("GROUP BY DATE(time)"), eq(ACCOUNT_ID), eq(90))).thenReturn(List.of(
                Map.of("day", Date.valueOf(LocalDate.of(2026, 8, 4)), "total", 10000),
                Map.of("day", Date.valueOf(LocalDate.of(2026, 8, 3)), "total", 8000)
        ));

        List<ActivityTrendPoint> trends = new ActivityInsightService(jdbc).computeStepTrends(ACCOUNT_ID, 90);

        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).granularity()).isEqualTo("week");
        assertThat(trends.get(0).date()).isEqualTo("2026-08-03"); // the Monday
        assertThat(trends.get(0).steps()).isEqualTo(9000); // avg of 10000/8000, not their sum
    }

    @Test
    void computeStepTrends_beyond120Days_rollsUpToMonthlyAverages() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("GROUP BY DATE(time)"), eq(ACCOUNT_ID), eq(200))).thenReturn(List.of(
                Map.of("day", Date.valueOf(LocalDate.of(2026, 6, 15)), "total", 6000),
                Map.of("day", Date.valueOf(LocalDate.of(2026, 7, 20)), "total", 12000)
        ));

        List<ActivityTrendPoint> trends = new ActivityInsightService(jdbc).computeStepTrends(ACCOUNT_ID, 200);

        assertThat(trends).hasSize(2);
        assertThat(trends).allMatch(t -> "month".equals(t.granularity()));
        assertThat(trends.get(0).date()).isEqualTo("2026-06-01");
        assertThat(trends.get(0).steps()).isEqualTo(6000);
        assertThat(trends.get(1).date()).isEqualTo("2026-07-01");
        assertThat(trends.get(1).steps()).isEqualTo(12000);
    }

    @Test
    void computeStepTrends_dbFailure_returnsEmptyListRatherThanThrowing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("GROUP BY DATE(time)"), eq(ACCOUNT_ID), eq(7)))
                .thenThrow(new RuntimeException("connection lost"));

        List<ActivityTrendPoint> trends = new ActivityInsightService(jdbc).computeStepTrends(ACCOUNT_ID, 7);

        assertThat(trends).isEmpty();
    }
}
