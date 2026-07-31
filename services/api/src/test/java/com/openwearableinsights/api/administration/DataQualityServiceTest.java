package com.openwearableinsights.api.administration;

import com.openwearableinsights.api.administration.adapter.in.DataQualityController.DataQualitySummary;
import com.openwearableinsights.api.administration.adapter.in.DataQualityController.MetricQuality;
import com.openwearableinsights.api.administration.application.DataQualityService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataQualityService} — real per-metric coverage
 * computation, replacing the hardcoded placeholder the controller used to
 * return unconditionally (parity-matrix row #12).
 */
class DataQualityServiceTest {

    @Test
    void metricsWithGoodCoverage_bandedAsGood_andFreshnessReflectsMostRecentReading() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant now = Instant.now();

        // hr: present all 7 days, most recent reading 1 hour ago -> "fresh"
        List<Map<String, Object>> statsRows = List.of(
                Map.of("metric_type", "hr", "cnt", 28L, "days_with_data", 7,
                        "last_reading", Timestamp.from(now.minus(1, ChronoUnit.HOURS))),
                Map.of("metric_type", "steps", "cnt", 3L, "days_with_data", 3,
                        "last_reading", Timestamp.from(now.minus(2, ChronoUnit.DAYS)))
        );
        when(jdbc.queryForList(anyString(), eq(1L), any(Timestamp.class))).thenReturn(statsRows);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L), any(Timestamp.class))).thenReturn(5);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(2);

        DataQualityService service = new DataQualityService(jdbc);
        DataQualitySummary summary = service.computeSummary(1L);

        MetricQuality hr = summary.metrics().stream().filter(m -> m.metric().equals("Heart rate")).findFirst().orElseThrow();
        assertThat(hr.coverage()).isEqualTo(1.0);
        assertThat(hr.quality()).isEqualTo("good");

        MetricQuality steps = summary.metrics().stream().filter(m -> m.metric().equals("Steps")).findFirst().orElseThrow();
        assertThat(steps.coverage()).isCloseTo(3.0 / 7.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(steps.quality()).isEqualTo("poor");

        assertThat(summary.freshness()).isEqualTo("fresh"); // driven by hr's 1-hour-old reading
        assertThat(summary.daysOfData()).isEqualTo(5);
        assertThat(summary.totalSources()).isEqualTo(2);
        assertThat(summary.confidence()).isEqualTo("medium");
    }

    @Test
    void noDataAtAll_reportsHonestEmptyStateNotFabricatedValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(1L), any(Timestamp.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L), any(Timestamp.class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(0);

        DataQualityService service = new DataQualityService(jdbc);
        DataQualitySummary summary = service.computeSummary(1L);

        assertThat(summary.completeness()).isZero();
        assertThat(summary.freshness()).isEqualTo("no data");
        assertThat(summary.totalSources()).isZero();
        assertThat(summary.confidence()).isEqualTo("none");
        assertThat(summary.metrics()).allMatch(m -> m.quality().equals("poor"));
        assertThat(summary.issues()).isNotEmpty();
    }

    @Test
    void everyMetricQuality_isOneOfTheThreeBandsFlutterExpects() {
        // apps/flutter/lib/features/settings/data_quality_page.dart string-matches
        // quality == 'good' / 'fair' with everything else falling to a poor color —
        // this pins the contract so a future change can't silently drift.
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(1L), any(Timestamp.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L), any(Timestamp.class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(0);

        DataQualityService service = new DataQualityService(jdbc);
        DataQualitySummary summary = service.computeSummary(1L);

        assertThat(summary.metrics()).allMatch(m -> List.of("good", "fair", "poor").contains(m.quality()));
    }
}
