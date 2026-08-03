package com.openwearableinsights.api.activities;

import com.openwearableinsights.api.activities.application.ActivityInsightService;
import com.openwearableinsights.api.activities.domain.ActivitySummary;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
}
