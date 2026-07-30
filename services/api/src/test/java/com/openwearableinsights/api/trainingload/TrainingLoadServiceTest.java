package com.openwearableinsights.api.trainingload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrainingLoadService}: the HR-zone-weighted
 * per-session load formula (v1, see class Javadoc), the flat-intensity
 * fallback for sessions with no zone breakdown, and the acute/chronic
 * (7-day / 28-day) aggregation math used by
 * {@code readiness.application.CurrentMetricsService} to feed the
 * "Training load (ACWR)" factor.
 *
 * <p>Uses only synthetic data (no real health data), matching
 * packages/test-data/synthetic-activity.fit's known session shape.
 */
class TrainingLoadServiceTest {

    private static Map<String, Object> sessionRow(Instant startTime, double durationSeconds, String hrZoneJson) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("start_time", Timestamp.from(startTime));
        row.put("duration_seconds", durationSeconds);
        row.put("hr_zone_seconds", hrZoneJson);
        return row;
    }

    private static TrainingLoadService serviceWithRows(JdbcTemplate jdbc) {
        return new TrainingLoadService(jdbc, new ObjectMapper());
    }

    // --- per-session HR-zone-weighted load (pure function) ---

    @Test
    void computeSessionLoad_knownSyntheticSession_matchesHandComputedValue() {
        TrainingLoadService service = serviceWithRows(mock(JdbcTemplate.class));

        // Matches packages/test-data/synthetic-activity.fit's imported session
        // (hr_zone_seconds = [60, 300, 900, 480, 60], duration 1800s), also
        // confirmed present as-is in the real `activities` table.
        //
        // load = sum((zone+1) * minutesInZone):
        //   zone0: 1 * (60/60)   = 1
        //   zone1: 2 * (300/60)  = 10
        //   zone2: 3 * (900/60)  = 45
        //   zone3: 4 * (480/60)  = 32
        //   zone4: 5 * (60/60)   = 5
        //   total                = 93
        double load = service.computeSessionLoad(List.of(60.0, 300.0, 900.0, 480.0, 60.0), 1800.0);

        assertThat(load).isEqualTo(93.0);
    }

    @Test
    void computeSessionLoad_nullHrZoneSeconds_fallsBackToDurationBasedEstimate() {
        TrainingLoadService service = serviceWithRows(mock(JdbcTemplate.class));

        // Fallback: FALLBACK_ZONE_WEIGHT (3.0, "moderate" zone) * minutes.
        // 1800s = 30 min -> 3.0 * 30 = 90.0
        double load = service.computeSessionLoad(null, 1800.0);

        assertThat(load).isEqualTo(90.0);
    }

    @Test
    void computeSessionLoad_emptyHrZoneSeconds_fallsBackToDurationBasedEstimate() {
        TrainingLoadService service = serviceWithRows(mock(JdbcTemplate.class));

        double load = service.computeSessionLoad(List.of(), 600.0); // 10 min -> 3.0 * 10 = 30.0

        assertThat(load).isEqualTo(30.0);
    }

    @Test
    void computeSessionLoad_allZeroZones_treatedAsMissingAndFallsBack() {
        TrainingLoadService service = serviceWithRows(mock(JdbcTemplate.class));

        double load = service.computeSessionLoad(List.of(0.0, 0.0, 0.0, 0.0, 0.0), 600.0);

        assertThat(load).isEqualTo(30.0); // same fallback as empty/null
    }

    // --- acute / chronic aggregation ---

    @Test
    void fetchAcuteLoad_averagesPerDaySum_overDaysWithActivityOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant day1 = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant day2 = Instant.now().minus(2, ChronoUnit.DAYS);

        // day1: a single session, load = 1 * (60/60) = 1.0
        // day2: two sessions, each load = 1.0 -> daily sum = 2.0
        // No row at all for the other 5 days in the 7-day window — those
        // days simply don't contribute a GROUP BY bucket (see class Javadoc
        // on why we average over active days, not calendar days).
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenReturn(List.of(
                sessionRow(day1, 60.0, "[60.0,0.0,0.0,0.0,0.0]"),
                sessionRow(day2, 60.0, "[60.0,0.0,0.0,0.0,0.0]"),
                sessionRow(day2, 60.0, "[60.0,0.0,0.0,0.0,0.0]")
        ));

        TrainingLoadService service = serviceWithRows(jdbc);

        // (1.0 + 2.0) / 2 active days = 1.5, NOT /7
        assertThat(service.fetchAcuteLoad(1L)).isEqualTo(1.5);
    }

    @Test
    void fetchChronicLoad_noActivityRows_returnsZero() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenReturn(List.of());

        TrainingLoadService service = serviceWithRows(jdbc);

        assertThat(service.fetchChronicLoad(1L)).isEqualTo(0.0);
    }

    @Test
    void fetchAcuteLoad_malformedHrZoneJson_fallsBackRatherThanThrowing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant recently = Instant.now().minus(1, ChronoUnit.DAYS);

        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenReturn(List.of(
                sessionRow(recently, 600.0, "not-valid-json")
        ));

        TrainingLoadService service = serviceWithRows(jdbc);

        // Parse failure -> treated as missing zone data -> fallback: 3.0 * 10 min = 30.0
        assertThat(service.fetchAcuteLoad(1L)).isEqualTo(30.0);
    }

    @Test
    void fetchAcuteLoad_dbFailure_returnsZeroRatherThanThrowing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenThrow(new RuntimeException("connection lost"));

        TrainingLoadService service = serviceWithRows(jdbc);

        assertThat(service.fetchAcuteLoad(1L)).isEqualTo(0.0);
    }
}
