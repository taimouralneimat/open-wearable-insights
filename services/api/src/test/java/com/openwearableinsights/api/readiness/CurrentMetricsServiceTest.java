package com.openwearableinsights.api.readiness;

import com.openwearableinsights.api.readiness.application.CurrentMetricsService;
import com.openwearableinsights.api.readiness.domain.CurrentMetrics;
import com.openwearableinsights.api.shared.LocalDayClock;
import com.openwearableinsights.api.trainingload.application.TrainingLoadService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CurrentMetricsService}, focused on the local-day
 * boundary bug found 2026-08-04: "today's" HRV/RHR/stress readings were
 * fetched using UTC-midnight truncation instead of the account's real local
 * midnight (see LocalDayClock's javadoc and ReadinessController's note).
 * For an account not in UTC, this could feed a reading from what the user
 * considers "yesterday" into today's live readiness score, or exclude a
 * genuinely-today reading taken in the first few hours after local midnight.
 */
class CurrentMetricsServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final ZoneOffset UTC_PLUS_3 = ZoneOffset.ofHours(3);

    @Test
    void fetchCurrent_usesLocalMidnightNotUtcMidnight_asTheTodayBoundary() {
        // 2026-08-03T22:00:00Z is 2026-08-04T01:00 local at UTC+3 — already a
        // new local day, three hours before UTC would agree.
        Instant now = Instant.parse("2026-08-03T22:00:00Z");
        Instant realLocalMidnight = Instant.parse("2026-08-03T21:00:00Z");
        Instant utcMidnight = Instant.parse("2026-08-04T00:00:00Z"); // wrong boundary, 3h later

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TrainingLoadService trainingLoadService = mock(TrainingLoadService.class);
        when(trainingLoadService.fetchAcuteLoad(ACCOUNT_ID)).thenReturn(0.0);
        when(trainingLoadService.fetchChronicLoad(ACCOUNT_ID)).thenReturn(0.0);

        // A real reading taken at 2026-08-03T21:30Z: after true local
        // midnight (21:00Z) but before the wrong UTC-midnight boundary
        // (00:00Z next day) — a UTC-truncated query would incorrectly miss it.
        Instant readingJustAfterLocalMidnight = Instant.parse("2026-08-03T21:30:00Z");
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), eq("hrv"), any(Timestamp.class)))
                .thenAnswer(invocation -> {
                    Timestamp since = invocation.getArgument(3);
                    // Only return the reading if queried with the CORRECT (local-midnight) boundary.
                    return since.toInstant().equals(realLocalMidnight)
                            ? List.<Map<String, Object>>of(Map.of("value", 55.0))
                            : List.<Map<String, Object>>of();
                });
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), eq("rhr"), any(Timestamp.class))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), eq("stress"), any(Timestamp.class))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), eq("body_battery_low"), any(Timestamp.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ACCOUNT_ID), eq("sleep_stage"), any(Timestamp.class)))
                .thenReturn(0);

        LocalDayClock localDayClock = new LocalDayClock(Clock.fixed(now, UTC_PLUS_3));
        CurrentMetricsService service = new CurrentMetricsService(jdbc, trainingLoadService, localDayClock);

        CurrentMetrics current = service.fetchCurrent(ACCOUNT_ID);

        // Sanity check the boundary really is what the test claims before
        // asserting the outcome that depends on it.
        assertThat(localDayClock.startOfToday()).isEqualTo(realLocalMidnight).isNotEqualTo(utcMidnight);
        assertThat(current.hrvMs()).contains(55.0);
        assertThat(readingJustAfterLocalMidnight).isAfter(realLocalMidnight).isBefore(utcMidnight);
    }
}
