package com.openwearableinsights.api.vo2max;

import com.openwearableinsights.api.readiness.application.BaselineService;
import com.openwearableinsights.api.readiness.domain.PersonalBaseline;
import com.openwearableinsights.api.vo2max.application.Vo2MaxService;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxEstimate;
import com.openwearableinsights.api.vo2max.domain.Vo2MaxTrendPoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Vo2MaxService}: the Uth-Sorensen-Overgaard-Pedersen
 * Heart Rate Ratio formula (15.3 x HRmax/HRrest, see class Javadoc), the
 * honest-empty-state paths when real HRmax or resting-heart-rate data is
 * missing, the plausibility guard against physiologically-impossible
 * inputs, and the per-month trend join.
 *
 * <p>{@link BaselineService} is mocked directly rather than exercised
 * through a real {@link JdbcTemplate} — its own SQL/aggregation logic is
 * already covered by {@code readiness.PersonalBaselineTest}; these tests
 * only need to control what personal-baseline RHR value and confidence
 * {@link Vo2MaxService} sees.
 *
 * <p>Uses only synthetic data (no real health data).
 */
class Vo2MaxServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    private static Vo2MaxService service(JdbcTemplate jdbc, BaselineService baselineService) {
        return new Vo2MaxService(jdbc, baselineService);
    }

    private static PersonalBaseline baselineWithRhr(double rhr, String confidence, boolean provisional) {
        return new PersonalBaseline(
                Map.of("rhr", rhr),
                Map.of("rhr", 20),
                provisional ? 3 : 28,
                confidence,
                provisional ? "3-day provisional" : "28-day rolling"
        );
    }

    private static Map<String, Object> activityMaxHrRow(Instant startTime, int maxHeartRate) {
        return Map.of("start_time", Timestamp.from(startTime), "max_heart_rate", maxHeartRate);
    }

    // --- computeEstimate: honest empty states ---

    @Test
    void computeEstimate_noQualifyingActivity_returnsHonestEmptyEstimate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenReturn(List.of());
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(baselineWithRhr(60.0, "medium", false));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        assertThat(estimate.vo2Max()).isNull();
        assertThat(estimate.hrMaxBpm()).isNull();
        assertThat(estimate.hrRestBpm()).isNull();
        assertThat(estimate.confidence()).isEqualTo("none");
        assertThat(estimate.algorithmVersion()).isEqualTo(Vo2MaxService.ALGORITHM_VERSION);
        assertThat(estimate.limitations()).anyMatch(l -> l.contains("No real activity session"));
    }

    @Test
    void computeEstimate_noRhrBaseline_returnsHonestEmptyEstimate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenReturn(List.of(activityMaxHrRow(Instant.now().minus(5, ChronoUnit.DAYS), 180)));
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(new PersonalBaseline(Map.of(), Map.of(), 0, "low", "no baseline data"));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        assertThat(estimate.vo2Max()).isNull();
        assertThat(estimate.confidence()).isEqualTo("none");
        assertThat(estimate.limitations()).anyMatch(l -> l.contains("resting-heart-rate history"));
    }

    @Test
    void computeEstimate_activityQueryFails_returnsHonestEmptyEstimateRatherThanThrowing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenThrow(new RuntimeException("connection lost"));
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(baselineWithRhr(60.0, "medium", false));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        assertThat(estimate.vo2Max()).isNull();
        assertThat(estimate.confidence()).isEqualTo("none");
    }

    // --- computeEstimate: real formula ---

    @Test
    void computeEstimate_realData_computesHeartRateRatioFormula_andCapsConfidenceAtMedium() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant recent = Instant.now().minus(10, ChronoUnit.DAYS);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenReturn(List.of(activityMaxHrRow(recent, 180)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong(), any(Timestamp.class))).thenReturn(3);
        BaselineService baselineService = mock(BaselineService.class);
        // Baseline confidence "high" — result must still cap at "medium" (see class Javadoc).
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(baselineWithRhr(60.0, "high", false));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        // 15.3 * (180 / 60) = 45.9
        assertThat(estimate.vo2Max()).isEqualTo(45.9);
        assertThat(estimate.hrMaxBpm()).isEqualTo(180);
        assertThat(estimate.hrRestBpm()).isEqualTo(60.0);
        assertThat(estimate.confidence()).isEqualTo("medium");
        assertThat(estimate.algorithmVersion()).isEqualTo(Vo2MaxService.ALGORITHM_VERSION);
        assertThat(estimate.methodology()).contains("Uth").contains("2004").contains("15.3");
        assertThat(estimate.limitations()).anyMatch(l -> l.contains("margin of error"));
        assertThat(estimate.hrMaxSource()).contains("180 bpm");
        assertThat(estimate.hrRestSource()).contains("28-day rolling");
    }

    @Test
    void computeEstimate_staleHrMax_downgradesConfidenceToLow_andAddsLimitation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant old = Instant.now().minus(120, ChronoUnit.DAYS);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenReturn(List.of(activityMaxHrRow(old, 180)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong(), any(Timestamp.class))).thenReturn(1);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(baselineWithRhr(60.0, "high", false));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        assertThat(estimate.vo2Max()).isEqualTo(45.9);
        assertThat(estimate.confidence()).isEqualTo("low");
        assertThat(estimate.limitations()).anyMatch(l -> l.contains("days old"));
    }

    @Test
    void computeEstimate_lowConfidenceBaseline_propagatesToLowConfidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant recent = Instant.now().minus(5, ChronoUnit.DAYS);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenReturn(List.of(activityMaxHrRow(recent, 180)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong(), any(Timestamp.class))).thenReturn(1);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(baselineWithRhr(60.0, "low", true));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        assertThat(estimate.confidence()).isEqualTo("low");
        assertThat(estimate.limitations()).anyMatch(l -> l.contains("provisional"));
    }

    // --- computeEstimate: plausibility guard ---

    @Test
    void computeEstimate_hrMaxTooCloseToHrRest_treatedAsImplausible_noEstimateShown() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant recent = Instant.now().minus(5, ChronoUnit.DAYS);
        // Gap of only 5 bpm — physiologically implausible (MIN gap is 20).
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenReturn(List.of(activityMaxHrRow(recent, 70)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong(), any(Timestamp.class))).thenReturn(1);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(baselineWithRhr(65.0, "medium", false));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        assertThat(estimate.vo2Max()).isNull();
        assertThat(estimate.confidence()).isEqualTo("none");
        // Raw inputs are still shown (unlike the fully-missing-data case) —
        // more transparent than hiding the suspect numbers outright.
        assertThat(estimate.hrMaxBpm()).isEqualTo(70);
        assertThat(estimate.hrRestBpm()).isEqualTo(65.0);
        assertThat(estimate.limitations()).anyMatch(l -> l.contains("physiologically plausible"));
    }

    @Test
    void computeEstimate_hrMaxOutsidePlausibleRange_treatedAsImplausible() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant recent = Instant.now().minus(5, ChronoUnit.DAYS);
        // 260 bpm exceeds MAX_PLAUSIBLE_HR_MAX_BPM (230) — a data glitch, not a real reading.
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenReturn(List.of(activityMaxHrRow(recent, 260)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyLong(), any(Timestamp.class))).thenReturn(1);
        BaselineService baselineService = mock(BaselineService.class);
        when(baselineService.computeBaseline(ACCOUNT_ID)).thenReturn(baselineWithRhr(60.0, "medium", false));

        Vo2MaxEstimate estimate = service(jdbc, baselineService).computeEstimate(ACCOUNT_ID);

        assertThat(estimate.vo2Max()).isNull();
        assertThat(estimate.confidence()).isEqualTo("none");
    }

    // --- computeTrend ---

    @Test
    void computeTrend_noData_returnsEmptyList() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class))).thenReturn(List.of());
        BaselineService baselineService = mock(BaselineService.class);

        List<Vo2MaxTrendPoint> trend = service(jdbc, baselineService).computeTrend(ACCOUNT_ID);

        assertThat(trend).isEmpty();
    }

    @Test
    void computeTrend_dbFailure_returnsEmptyListRatherThanThrowing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong(), any(Timestamp.class)))
                .thenThrow(new RuntimeException("connection lost"));
        BaselineService baselineService = mock(BaselineService.class);

        List<Vo2MaxTrendPoint> trend = service(jdbc, baselineService).computeTrend(ACCOUNT_ID);

        assertThat(trend).isEmpty();
    }

    @Test
    void computeTrend_onlyJoinsMonthsPresentInBothSources_sortedAscending() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);

        YearMonth twoMonthsAgo = YearMonth.now().minusMonths(2);
        YearMonth oneMonthAgo = YearMonth.now().minusMonths(1);
        Timestamp twoMonthsAgoTs = monthStartTimestamp(twoMonthsAgo);
        Timestamp oneMonthAgoTs = monthStartTimestamp(oneMonthAgo);

        // Both months have an activity max HR, but only twoMonthsAgo also
        // has RHR data — oneMonthAgo must be omitted from the trend.
        when(jdbc.queryForList(contains("FROM activities"), eq(ACCOUNT_ID), any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("month", twoMonthsAgoTs, "hr_max", 183),
                        Map.of("month", oneMonthAgoTs, "hr_max", 190)
                ));
        when(jdbc.queryForList(contains("FROM measurements"), eq(ACCOUNT_ID), any(Timestamp.class)))
                .thenReturn(List.of(
                        Map.of("month", twoMonthsAgoTs, "avg_rhr", 61.0)
                ));

        List<Vo2MaxTrendPoint> trend = service(jdbc, baselineService).computeTrend(ACCOUNT_ID);

        assertThat(trend).hasSize(1);
        // 183 / 61 = 3.0 -> 15.3 * 3.0 = 45.9
        assertThat(trend.get(0).vo2Max()).isEqualTo(45.9);
        assertThat(trend.get(0).month()).isEqualTo(twoMonthsAgo.toString());
    }

    @Test
    void computeTrend_implausibleMonth_omittedFromTrend() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BaselineService baselineService = mock(BaselineService.class);

        YearMonth month = YearMonth.now().minusMonths(1);
        Timestamp monthTs = monthStartTimestamp(month);

        when(jdbc.queryForList(contains("FROM activities"), eq(ACCOUNT_ID), any(Timestamp.class)))
                .thenReturn(List.of(Map.of("month", monthTs, "hr_max", 70)));
        when(jdbc.queryForList(contains("FROM measurements"), eq(ACCOUNT_ID), any(Timestamp.class)))
                .thenReturn(List.of(Map.of("month", monthTs, "avg_rhr", 65.0)));

        List<Vo2MaxTrendPoint> trend = service(jdbc, baselineService).computeTrend(ACCOUNT_ID);

        assertThat(trend).isEmpty();
    }

    private static Timestamp monthStartTimestamp(YearMonth month) {
        return Timestamp.from(month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
