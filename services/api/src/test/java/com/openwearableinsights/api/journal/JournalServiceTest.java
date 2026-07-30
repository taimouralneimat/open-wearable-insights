package com.openwearableinsights.api.journal;

import com.openwearableinsights.api.journal.application.JournalService;
import com.openwearableinsights.api.journal.domain.BehaviorCategory;
import com.openwearableinsights.api.journal.domain.HabitStreak;
import com.openwearableinsights.api.journal.domain.JournalEntry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JournalService}: taxonomy breadth, category/behavior
 * encoding round-trip, and untrusted-input handling.
 */
class JournalServiceTest {

    @Test
    void taxonomy_isMeaningfullyBroaderThanOriginalSixBehaviorStub() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JournalService service = new JournalService(jdbc);

        List<BehaviorCategory> taxonomy = service.getTaxonomy();

        assertThat(taxonomy).isNotEmpty();
        int totalBehaviors = taxonomy.stream().mapToInt(c -> c.behaviors().size()).sum();
        // The original Phase 1 stub covered 6 behaviors (caffeine, meals,
        // travel, alcohol, RPE, soreness) with no categories at all.
        assertThat(totalBehaviors).isGreaterThan(6 * 3);
        assertThat(taxonomy.size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void taxonomy_everyCategoryHasNameAndBehaviors() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JournalService service = new JournalService(jdbc);

        for (BehaviorCategory category : service.getTaxonomy()) {
            assertThat(category.name()).isNotBlank();
            assertThat(category.behaviors()).isNotEmpty();
            category.behaviors().forEach(b -> assertThat(b).isNotBlank());
        }
    }

    @Test
    void addEntry_encodesCategoryIntoStoredBehaviorColumn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), anyString(), any(), any()))
                .thenReturn(42L);
        JournalService service = new JournalService(jdbc);

        JournalEntry entry = service.addEntry(1L, "Nutrition", "Alcohol", "2 units", "wedding");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> behaviorCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(sqlCaptor.capture(), eq(Long.class), any(), any(),
                behaviorCaptor.capture(), any(), any());

        assertThat(sqlCaptor.getValue()).contains("INSERT INTO journal_entries");
        assertThat(behaviorCaptor.getValue()).isEqualTo("Nutrition::Alcohol");
        assertThat(entry.id()).isEqualTo(42L);
        assertThat(entry.category()).isEqualTo("Nutrition");
        assertThat(entry.behavior()).isEqualTo("Alcohol");
        assertThat(entry.treatedAsUntrusted()).isTrue();
    }

    @Test
    void addEntry_withNullCategory_fallsBackToOther() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), anyString(), any(), any()))
                .thenReturn(1L);
        JournalService service = new JournalService(jdbc);

        service.addEntry(1L, null, "Custom behavior", null, null);

        ArgumentCaptor<Object> behaviorCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(anyString(), eq(Long.class), any(), any(),
                behaviorCaptor.capture(), any(), any());
        assertThat(behaviorCaptor.getValue()).isEqualTo("Other::Custom behavior");
    }

    @Test
    void listEntries_decodesCategoryAndBehaviorFromStoredColumn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant now = Instant.now();
        Map<String, Object> row = Map.of(
                "id", 5L, "account_id", 1L, "time", Timestamp.from(now),
                "behavior", "Recovery::Cold exposure", "value", "5 min", "note", "",
                "treated_as_untrusted", true, "created_at", Timestamp.from(now)
        );
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(row));
        JournalService service = new JournalService(jdbc);

        List<JournalEntry> entries = service.listEntries(1L, null);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).category()).isEqualTo("Recovery");
        assertThat(entries.get(0).behavior()).isEqualTo("Cold exposure");
        assertThat(entries.get(0).treatedAsUntrusted()).isTrue();
    }

    @Test
    void listEntries_handlesLegacyEntriesWithoutCategoryEncoding() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant now = Instant.now();
        Map<String, Object> row = Map.of(
                "id", 6L, "account_id", 1L, "time", Timestamp.from(now),
                "behavior", "caffeine", "value", "", "note", "",
                "treated_as_untrusted", true, "created_at", Timestamp.from(now)
        );
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(row));
        JournalService service = new JournalService(jdbc);

        List<JournalEntry> entries = service.listEntries(1L, null);

        assertThat(entries.get(0).category()).isEqualTo("Other");
        assertThat(entries.get(0).behavior()).isEqualTo("caffeine");
    }

    @Test
    void getStreaks_consecutiveDaysEndingToday_countsCurrentStreak() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant today = Instant.now();
        List<Map<String, Object>> rows = List.of(
                row("Recovery::Cold exposure", today.minus(2, java.time.temporal.ChronoUnit.DAYS)),
                row("Recovery::Cold exposure", today.minus(1, java.time.temporal.ChronoUnit.DAYS)),
                row("Recovery::Cold exposure", today)
        );
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(rows);
        JournalService service = new JournalService(jdbc);

        List<HabitStreak> streaks = service.getStreaks(1L);

        assertThat(streaks).hasSize(1);
        assertThat(streaks.get(0).category()).isEqualTo("Recovery");
        assertThat(streaks.get(0).behavior()).isEqualTo("Cold exposure");
        assertThat(streaks.get(0).currentStreak()).isEqualTo(3);
        assertThat(streaks.get(0).longestStreak()).isEqualTo(3);
    }

    @Test
    void getStreaks_gapBeforeLastEntry_resetsCurrentButKeepsLongest() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant today = Instant.now();
        List<Map<String, Object>> rows = List.of(
                row("Sleep::Consistent bedtime", today.minus(10, java.time.temporal.ChronoUnit.DAYS)),
                row("Sleep::Consistent bedtime", today.minus(9, java.time.temporal.ChronoUnit.DAYS)),
                row("Sleep::Consistent bedtime", today.minus(8, java.time.temporal.ChronoUnit.DAYS)),
                row("Sleep::Consistent bedtime", today.minus(5, java.time.temporal.ChronoUnit.DAYS))
        );
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(rows);
        JournalService service = new JournalService(jdbc);

        List<HabitStreak> streaks = service.getStreaks(1L);

        assertThat(streaks).hasSize(1);
        assertThat(streaks.get(0).longestStreak()).isEqualTo(3);
        assertThat(streaks.get(0).currentStreak()).isEqualTo(0);
    }

    @Test
    void getStreaks_sortedByCurrentStreakDescending() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant today = Instant.now();
        List<Map<String, Object>> rows = List.of(
                row("Recovery::Full rest day", today),
                row("Nutrition::Alcohol", today.minus(30, java.time.temporal.ChronoUnit.DAYS)),
                row("Sleep::Nap taken", today),
                row("Sleep::Nap taken", today.minus(1, java.time.temporal.ChronoUnit.DAYS))
        );
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(rows);
        JournalService service = new JournalService(jdbc);

        List<HabitStreak> streaks = service.getStreaks(1L);

        assertThat(streaks).hasSize(3);
        assertThat(streaks.get(0).behavior()).isEqualTo("Nap taken");
        assertThat(streaks.get(0).currentStreak()).isEqualTo(2);
    }

    private Map<String, Object> row(String behavior, Instant time) {
        return Map.of("behavior", behavior, "time", Timestamp.from(time));
    }
}
