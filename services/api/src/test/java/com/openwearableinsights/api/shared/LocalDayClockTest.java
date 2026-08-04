package com.openwearableinsights.api.shared;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LocalDayClock} — the boundary math this class
 * exists to get right. Fixes both "now" and the zone via {@link Clock#fixed}
 * specifically to catch the class of bug this exists for: UTC-midnight
 * truncation silently disagreeing with the user's real local midnight.
 */
class LocalDayClockTest {

    // UTC+3 — matches the real machine this bug was found on (see
    // ReadinessController's javadoc note and commit 7923b7f).
    private static final ZoneOffset UTC_PLUS_3 = ZoneOffset.ofHours(3);

    @Test
    void today_reflectsTheGivenZoneNotUtc() {
        // 2026-08-03T21:30:00Z is still 2026-08-03 in UTC, but already
        // 2026-08-04 at UTC+3 — the exact boundary case this class exists for.
        Instant justAfterUtcPlus3Midnight = Instant.parse("2026-08-03T21:30:00Z");
        LocalDayClock clock = new LocalDayClock(Clock.fixed(justAfterUtcPlus3Midnight, UTC_PLUS_3));

        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 8, 4));
    }

    @Test
    void today_atTheSameRealInstant_isStillYesterdayInUtc() {
        Instant justAfterUtcPlus3Midnight = Instant.parse("2026-08-03T21:30:00Z");
        LocalDayClock utcClock = new LocalDayClock(Clock.fixed(justAfterUtcPlus3Midnight, ZoneOffset.UTC));

        assertThat(utcClock.today()).isEqualTo(LocalDate.of(2026, 8, 3));
    }

    @Test
    void startOfToday_isLocalMidnightAsAnInstant_notUtcMidnight() {
        Instant now = Instant.parse("2026-08-03T22:00:00Z"); // 2026-08-04T01:00 at UTC+3
        LocalDayClock clock = new LocalDayClock(Clock.fixed(now, UTC_PLUS_3));

        // Local midnight for 2026-08-04 at UTC+3 is 2026-08-03T21:00:00Z —
        // three hours BEFORE UTC midnight, not at it.
        assertThat(clock.startOfToday()).isEqualTo(Instant.parse("2026-08-03T21:00:00Z"));
    }

    @Test
    void startOfLocalDay_forASpecificDate() {
        LocalDayClock clock = new LocalDayClock(UTC_PLUS_3);

        Instant start = clock.startOfLocalDay(LocalDate.of(2026, 8, 4));

        assertThat(start).isEqualTo(Instant.parse("2026-08-03T21:00:00Z"));
    }

    @Test
    void localDateOf_usesTheRealLocalDayNotUtcs() {
        LocalDayClock clock = new LocalDayClock(UTC_PLUS_3);
        // 2026-08-03T21:30:00Z is 2026-08-04T00:30 at UTC+3 — already the next day locally.
        Instant lateEveningUtcButNextDayLocally = Instant.parse("2026-08-03T21:30:00Z");

        assertThat(clock.localDateOf(lateEveningUtcButNextDayLocally)).isEqualTo(LocalDate.of(2026, 8, 4));
    }

    @Test
    void withUtcZone_matchesPlainUtcBehavior_forCallersThatWantThat() {
        LocalDayClock clock = new LocalDayClock(ZoneOffset.UTC);
        Instant now = Instant.parse("2026-08-03T21:30:00Z");

        assertThat(clock.localDateOf(now)).isEqualTo(LocalDate.of(2026, 8, 3));
    }
}
