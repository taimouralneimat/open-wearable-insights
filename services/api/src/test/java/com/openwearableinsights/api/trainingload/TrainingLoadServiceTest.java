package com.openwearableinsights.api.trainingload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadSummary;
import com.openwearableinsights.api.trainingload.domain.TrainingLoadTrendPoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
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

    // --- per-day load history (fetchDailyLoadHistory) ---

    @Test
    void fetchDailyLoadHistory_returnsSortedOldestFirst_summedPerDay() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant day1 = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant day2 = Instant.now().minus(2, ChronoUnit.DAYS);

        // Rows deliberately returned out of chronological order, and day2
        // has two sessions that must be summed into one point.
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenReturn(List.of(
                sessionRow(day2, 60.0, "[60.0,0.0,0.0,0.0,0.0]"), // load 1.0
                sessionRow(day2, 60.0, "[60.0,0.0,0.0,0.0,0.0]"), // load 1.0 -> day2 total 2.0
                sessionRow(day1, 120.0, "[0.0,120.0,0.0,0.0,0.0]") // load 2 * 2 = 4.0
        ));

        TrainingLoadService service = serviceWithRows(jdbc);
        List<TrainingLoadTrendPoint> history = service.fetchDailyLoadHistory(1L, 28);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).date()).isEqualTo(day1.atZone(ZoneOffset.UTC).toLocalDate().toString());
        assertThat(history.get(0).load()).isEqualTo(4.0);
        assertThat(history.get(1).date()).isEqualTo(day2.atZone(ZoneOffset.UTC).toLocalDate().toString());
        assertThat(history.get(1).load()).isEqualTo(2.0);
    }

    @Test
    void fetchDailyLoadHistory_noRows_returnsEmptyList() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenReturn(List.of());

        TrainingLoadService service = serviceWithRows(jdbc);

        assertThat(service.fetchDailyLoadHistory(1L, 28)).isEmpty();
    }

    @Test
    void fetchDailyLoadHistory_dbFailure_returnsEmptyListRatherThanThrowing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenThrow(new RuntimeException("connection lost"));

        TrainingLoadService service = serviceWithRows(jdbc);

        assertThat(service.fetchDailyLoadHistory(1L, 28)).isEmpty();
    }

    // --- summary (computeSummary) ---

    /**
     * Mocks separate acute (7d) vs. chronic (28d) query results by
     * inspecting the "since" timestamp each fetch call is made with,
     * exactly the way TrainingLoadService's real SQL WHERE clause would
     * filter, so acute-only load differs from chronic-only load.
     */
    private static TrainingLoadSummary computeSummaryWithRows(
            List<Map<String, Object>> acuteWindowRows, List<Map<String, Object>> chronicWindowRows) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenAnswer(invocation -> {
            Timestamp since = invocation.getArgument(2);
            boolean isAcuteWindow = since.toInstant().isAfter(Instant.now().minus(10, ChronoUnit.DAYS));
            return isAcuteWindow ? acuteWindowRows : chronicWindowRows;
        });
        return serviceWithRows(jdbc).computeSummary(1L);
    }

    @Test
    void computeSummary_noActivityData_reportsUnknownStatusHonestly() {
        TrainingLoadSummary summary = computeSummaryWithRows(List.of(), List.of());

        assertThat(summary.acuteLoad()).isNull();
        assertThat(summary.chronicLoad()).isNull();
        assertThat(summary.acwr()).isNull();
        assertThat(summary.loadStatus()).isEqualTo("unknown");
        assertThat(summary.confidence()).isEqualTo("none");
        assertThat(summary.algorithmVersion()).isEqualTo(TrainingLoadService.ALGORITHM_VERSION);
    }

    @Test
    void computeSummary_acwrAroundOne_classifiedOptimal_andMatchesReadinessFormula() {
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        List<Map<String, Object>> singleDayLoadOne = List.of(
                sessionRow(recent, 60.0, "[60.0,0.0,0.0,0.0,0.0]") // load 1.0
        );

        // Same single day in both windows -> acute == chronic == 1.0 -> acwr == 1.0
        TrainingLoadSummary summary = computeSummaryWithRows(singleDayLoadOne, singleDayLoadOne);

        assertThat(summary.acuteLoad()).isEqualTo(1.0);
        assertThat(summary.chronicLoad()).isEqualTo(1.0);
        // Identical to ReadinessCalculator's "chronic > 0 ? acute / chronic : 1.0" branch.
        assertThat(summary.acwr()).isEqualTo(1.0);
        assertThat(summary.loadStatus()).isEqualTo("optimal"); // 0.8 <= 1.0 <= 1.3
        assertThat(summary.confidence()).isEqualTo("medium");
    }

    @Test
    void computeSummary_acuteFarAboveChronic_classifiedHigh() {
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant older = Instant.now().minus(20, ChronoUnit.DAYS);

        List<Map<String, Object>> acuteRows = List.of(
                sessionRow(recent, 1800.0, "[0.0,0.0,0.0,0.0,1800.0]") // load 5 * 30 = 150.0
        );
        List<Map<String, Object>> chronicRows = List.of(
                sessionRow(recent, 1800.0, "[0.0,0.0,0.0,0.0,1800.0]"), // load 150.0
                sessionRow(older, 60.0, "[60.0,0.0,0.0,0.0,0.0]")       // load 1.0
        );
        // chronic average = (150.0 + 1.0) / 2 active days = 75.5

        TrainingLoadSummary summary = computeSummaryWithRows(acuteRows, chronicRows);

        assertThat(summary.acuteLoad()).isEqualTo(150.0);
        assertThat(summary.chronicLoad()).isEqualTo(75.5);
        assertThat(summary.acwr()).isEqualTo(150.0 / 75.5);
        assertThat(summary.loadStatus()).isEqualTo("high"); // acwr ~1.99 > 1.5
    }

    @Test
    void computeSummary_acuteFarBelowChronic_classifiedLow() {
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant older = Instant.now().minus(20, ChronoUnit.DAYS);

        List<Map<String, Object>> acuteRows = List.of(
                sessionRow(recent, 60.0, "[60.0,0.0,0.0,0.0,0.0]") // load 1.0
        );
        List<Map<String, Object>> chronicRows = List.of(
                sessionRow(recent, 60.0, "[60.0,0.0,0.0,0.0,0.0]"),      // load 1.0
                sessionRow(older, 1800.0, "[0.0,0.0,0.0,0.0,1800.0]")    // load 150.0
        );
        // chronic average = (1.0 + 150.0) / 2 active days = 75.5

        TrainingLoadSummary summary = computeSummaryWithRows(acuteRows, chronicRows);

        assertThat(summary.acuteLoad()).isEqualTo(1.0);
        assertThat(summary.chronicLoad()).isEqualTo(75.5);
        assertThat(summary.acwr()).isEqualTo(1.0 / 75.5);
        assertThat(summary.loadStatus()).isEqualTo("low"); // acwr ~0.013 < 0.8
    }

    @Test
    void computeSummary_onlyAcuteDataPresent_stillReportsUnknownAndNoneConfidence() {
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        List<Map<String, Object>> acuteRows = List.of(
                sessionRow(recent, 60.0, "[60.0,0.0,0.0,0.0,0.0]") // load 1.0
        );

        // No chronic-window data at all -> can't compute a ratio, even
        // though acute has a real value; mirrors CurrentMetricsService's
        // Optional.empty() treatment for the readiness "Training load
        // (ACWR)" factor in this same situation.
        TrainingLoadSummary summary = computeSummaryWithRows(acuteRows, List.of());

        assertThat(summary.acuteLoad()).isEqualTo(1.0);
        assertThat(summary.chronicLoad()).isNull();
        assertThat(summary.acwr()).isNull();
        assertThat(summary.loadStatus()).isEqualTo("unknown");
        assertThat(summary.confidence()).isEqualTo("none");
    }
}
