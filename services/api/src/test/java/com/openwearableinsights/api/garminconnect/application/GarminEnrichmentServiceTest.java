package com.openwearableinsights.api.garminconnect.application;

import com.openwearableinsights.api.garminconnect.application.GarminEnrichmentService.BodyBatteryTrendPoint;
import com.openwearableinsights.api.garminconnect.application.GarminEnrichmentService.TodaySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link GarminEnrichmentService}, mocked-JdbcTemplate style matching ExportServiceTest. */
class GarminEnrichmentServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    @Test
    void today_noData_returnsAllNullsRatherThanThrowing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());

        GarminEnrichmentService service = new GarminEnrichmentService(jdbc);
        TodaySnapshot snapshot = service.today(ACCOUNT_ID);

        assertThat(snapshot.bodyBatteryAsOf()).isNull();
        assertThat(snapshot.bodyBatteryHigh()).isNull();
        assertThat(snapshot.trainingReadinessAsOf()).isNull();
        assertThat(snapshot.garminTrainingReadiness()).isNull();
    }

    @Test
    void today_realData_mapsEachMetricToItsOwnLatestValueAndDate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDate bbDate = LocalDate.of(2026, 8, 2);
        LocalDate trDate = LocalDate.of(2026, 8, 1);

        stubLatest(jdbc, "body_battery_charged", bbDate, 45.0);
        stubLatest(jdbc, "body_battery_drained", bbDate, 62.0);
        stubLatest(jdbc, "body_battery_high", bbDate, 88.0);
        stubLatest(jdbc, "body_battery_low", bbDate, 21.0);
        stubLatest(jdbc, "garmin_training_readiness", trDate, 73.0);

        GarminEnrichmentService service = new GarminEnrichmentService(jdbc);
        TodaySnapshot snapshot = service.today(ACCOUNT_ID);

        assertThat(snapshot.bodyBatteryAsOf()).isEqualTo(bbDate);
        assertThat(snapshot.bodyBatteryHigh()).isEqualTo(88.0);
        assertThat(snapshot.bodyBatteryLow()).isEqualTo(21.0);
        assertThat(snapshot.bodyBatteryCharged()).isEqualTo(45.0);
        assertThat(snapshot.bodyBatteryDrained()).isEqualTo(62.0);
        assertThat(snapshot.trainingReadinessAsOf()).isEqualTo(trDate);
        assertThat(snapshot.garminTrainingReadiness()).isEqualTo(73);
    }

    @Test
    void bodyBatteryTrend_mapsRowsToDatedHighLowPoints() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FILTER"), eq(ACCOUNT_ID), org.mockito.ArgumentMatchers.any(Timestamp.class)))
                .thenReturn(List.of(
                        rowOf(LocalDate.of(2026, 7, 30), 80.0, 15.0),
                        rowOf(LocalDate.of(2026, 7, 31), null, 20.0)
                ));

        GarminEnrichmentService service = new GarminEnrichmentService(jdbc);
        List<BodyBatteryTrendPoint> trend = service.bodyBatteryTrend(ACCOUNT_ID);

        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(trend.get(0).high()).isEqualTo(80.0);
        assertThat(trend.get(1).high()).isNull();
        assertThat(trend.get(1).low()).isEqualTo(20.0);
    }

    private void stubLatest(JdbcTemplate jdbc, String metricType, LocalDate date, double value) {
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), eq(metricType))).thenReturn(List.of(
                Map.of("time", Timestamp.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()), "value", value)
        ));
    }

    private Map<String, Object> rowOf(LocalDate date, Double high, Double low) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("d", Date.valueOf(date));
        row.put("high", high);
        row.put("low", low);
        return row;
    }
}
