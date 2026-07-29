package com.openwearableinsights.api.journal;

import com.openwearableinsights.api.journal.application.CorrelationService;
import com.openwearableinsights.api.journal.domain.BehaviorCorrelation;
import com.openwearableinsights.api.readiness.application.ReadinessScoreHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CorrelationService}: minimum sample size
 * enforcement, correct group-mean math, and the correlation-not-causation
 * discipline required by docs/product/parity-matrix.md row 6.
 */
class CorrelationServiceTest {

    private static Timestamp ts(LocalDate date, int hour) {
        return Timestamp.from(date.atStartOfDay(ZoneOffset.UTC).plusHours(hour).toInstant());
    }

    private static Map<String, Object> journalRow(String behavior, LocalDate date) {
        return Map.of("behavior", behavior, "time", ts(date, 8));
    }

    @Test
    void behaviorsBelowMinSampleSize_areExcluded() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReadinessScoreHistoryRepository history = mock(ReadinessScoreHistoryRepository.class);

        LocalDate d1 = LocalDate.of(2026, 7, 1);
        LocalDate d2 = LocalDate.of(2026, 7, 2);
        LocalDate d3 = LocalDate.of(2026, 7, 3);

        // Only 2 logged days — below MIN_SAMPLE_SIZE_PER_GROUP (3)
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), anyLong()))
                .thenReturn(List.of(journalRow("Nutrition::Alcohol", d1), journalRow("Nutrition::Alcohol", d2)));
        when(history.findScoresByAccountId(anyLong()))
                .thenReturn(Map.of(d1, 50, d2, 55, d3, 60));

        CorrelationService service = new CorrelationService(jdbc, history);
        List<BehaviorCorrelation> results = service.computeCorrelations(1L);

        assertThat(results).isEmpty();
    }

    @Test
    void behaviorsMeetingMinSampleSize_computeCorrectGroupMeans() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReadinessScoreHistoryRepository history = mock(ReadinessScoreHistoryRepository.class);

        LocalDate loggedA = LocalDate.of(2026, 7, 1);
        LocalDate loggedB = LocalDate.of(2026, 7, 2);
        LocalDate loggedC = LocalDate.of(2026, 7, 3);
        LocalDate notLoggedA = LocalDate.of(2026, 7, 4);
        LocalDate notLoggedB = LocalDate.of(2026, 7, 5);
        LocalDate notLoggedC = LocalDate.of(2026, 7, 6);

        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), anyLong()))
                .thenReturn(List.of(
                        journalRow("Recovery::Cold exposure", loggedA),
                        journalRow("Recovery::Cold exposure", loggedB),
                        journalRow("Recovery::Cold exposure", loggedC)
                ));
        // Logged days average 40, not-logged days average 70 -> difference -30
        when(history.findScoresByAccountId(anyLong())).thenReturn(Map.of(
                loggedA, 30, loggedB, 40, loggedC, 50,
                notLoggedA, 60, notLoggedB, 70, notLoggedC, 80
        ));

        CorrelationService service = new CorrelationService(jdbc, history);
        List<BehaviorCorrelation> results = service.computeCorrelations(1L);

        assertThat(results).hasSize(1);
        BehaviorCorrelation c = results.get(0);
        assertThat(c.category()).isEqualTo("Recovery");
        assertThat(c.behavior()).isEqualTo("Cold exposure");
        assertThat(c.loggedDayCount()).isEqualTo(3);
        assertThat(c.notLoggedDayCount()).isEqualTo(3);
        assertThat(c.avgReadinessWhenLogged()).isEqualTo(40.0);
        assertThat(c.avgReadinessWhenNotLogged()).isEqualTo(70.0);
        assertThat(c.difference()).isEqualTo(-30.0);
    }

    @Test
    void everyResult_includesCorrelationNotCausationCaution() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReadinessScoreHistoryRepository history = mock(ReadinessScoreHistoryRepository.class);

        LocalDate[] logged = {LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3)};
        LocalDate[] notLogged = {LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 6)};

        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), anyLong()))
                .thenReturn(List.of(
                        journalRow("Sleep::Used sleep aid", logged[0]),
                        journalRow("Sleep::Used sleep aid", logged[1]),
                        journalRow("Sleep::Used sleep aid", logged[2])
                ));
        when(history.findScoresByAccountId(anyLong())).thenReturn(Map.of(
                logged[0], 60, logged[1], 65, logged[2], 70,
                notLogged[0], 50, notLogged[1], 55, notLogged[2], 60
        ));

        CorrelationService service = new CorrelationService(jdbc, history);
        List<BehaviorCorrelation> results = service.computeCorrelations(1L);

        assertThat(results).isNotEmpty();
        for (BehaviorCorrelation c : results) {
            String allText = String.join(" ", c.limitations());
            assertThat(allText.toLowerCase()).contains("not causation");
            // Never imply the behavior caused the outcome
            assertThat(allText.toLowerCase()).doesNotContain("causes ");
            assertThat(allText.toLowerCase()).doesNotContain("leads to");
            assertThat(allText.toLowerCase()).doesNotContain("results in");
        }
    }

    @Test
    void confidence_isNeverHigh_evenWithLargeSamples() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReadinessScoreHistoryRepository history = mock(ReadinessScoreHistoryRepository.class);

        List<Map<String, Object>> journalRows = new java.util.ArrayList<>();
        Map<LocalDate, Integer> scores = new java.util.HashMap<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 20; i++) {
            LocalDate d = start.plusDays(i);
            journalRows.add(journalRow("Training::Muscle soreness", d));
            scores.put(d, 50 + i);
        }
        for (int i = 20; i < 40; i++) {
            scores.put(start.plusDays(i), 50 - i);
        }
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), anyLong())).thenReturn(journalRows);
        when(history.findScoresByAccountId(anyLong())).thenReturn(scores);

        CorrelationService service = new CorrelationService(jdbc, history);
        List<BehaviorCorrelation> results = service.computeCorrelations(1L);

        assertThat(results).isNotEmpty();
        results.forEach(c -> assertThat(c.confidence()).isIn("low", "medium"));
    }

    @Test
    void noReadinessHistory_returnsEmptyNotError() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReadinessScoreHistoryRepository history = mock(ReadinessScoreHistoryRepository.class);
        when(history.findScoresByAccountId(anyLong())).thenReturn(Map.of());

        CorrelationService service = new CorrelationService(jdbc, history);
        List<BehaviorCorrelation> results = service.computeCorrelations(1L);

        assertThat(results).isEmpty();
    }

    @Test
    void results_sortedByAbsoluteDifferenceDescending() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReadinessScoreHistoryRepository history = mock(ReadinessScoreHistoryRepository.class);

        LocalDate[] logged = {LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3)};
        LocalDate[] notLogged = {LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 6)};

        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), anyLong()))
                .thenReturn(List.of(
                        journalRow("Nutrition::Alcohol", logged[0]),
                        journalRow("Nutrition::Alcohol", logged[1]),
                        journalRow("Nutrition::Alcohol", logged[2]),
                        journalRow("Recovery::Massage", logged[0]),
                        journalRow("Recovery::Massage", logged[1]),
                        journalRow("Recovery::Massage", logged[2])
                ));
        // Alcohol: logged avg 40 vs not-logged avg 60 -> |diff|=20
        // Massage: logged avg 55 vs not-logged avg 60 -> |diff|=5 (smaller)
        when(history.findScoresByAccountId(anyLong())).thenReturn(Map.of(
                logged[0], 30, logged[1], 40, logged[2], 50,
                notLogged[0], 55, notLogged[1], 60, notLogged[2], 65
        ));

        CorrelationService service = new CorrelationService(jdbc, history);
        List<BehaviorCorrelation> results = service.computeCorrelations(1L);

        assertThat(results).hasSize(2);
        assertThat(Math.abs(results.get(0).difference()))
                .isGreaterThanOrEqualTo(Math.abs(results.get(1).difference()));
    }
}
