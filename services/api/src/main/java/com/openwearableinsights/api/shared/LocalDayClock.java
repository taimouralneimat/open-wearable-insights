package com.openwearableinsights.api.shared;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The single source of truth for "what day is it" and "which day did this
 * instant happen on" — from the user's own local perspective, not the
 * storage timezone.
 *
 * <p>Measurements are stored as UTC instants throughout this schema
 * (correct, unchanged — see V01 migration). But this app runs entirely on
 * the user's own machine (ADR-0008: single-user, local-first), so
 * {@link ZoneId#systemDefault()} <em>is</em> the user's real timezone, with
 * no configuration needed. Using {@code Instant.now().truncatedTo(DAYS)} or
 * {@code LocalDate.now(ZoneOffset.UTC)} instead — as several call sites did
 * before this class existed — silently uses UTC midnight as the day
 * boundary, which disagrees with the user's real local midnight by however
 * many hours their timezone is offset. For someone at UTC+3, "today's HRV
 * reading" computed via UTC-midnight truncation can include up to ~21 hours
 * of what they'd call yesterday, and exclude the first ~3 hours of what
 * they'd call today.
 *
 * <p>Wraps a {@link Clock} (not just a {@link ZoneId}) specifically so
 * "now" itself is fixable in tests — a boundary bug like the one this class
 * fixes can only be caught by asserting behavior at an exact instant near
 * local midnight, which requires controlling "now", not just the zone.
 *
 * <p>Found 2026-08-04 while live-verifying the sleep-consistency feature
 * (see SleepInsightService's own zone handling, fixed the same night) —
 * this class is the shared foundation for applying the same fix to
 * {@code CurrentMetricsService} (what counts as "today's" reading for the
 * live readiness score), {@code ReadinessController}/{@code CoachController}
 * (which calendar day a computed score is stored/diffed against), and
 * {@code JournalService} (habit-streak continuity).
 *
 * <p><strong>Deliberately NOT applied everywhere</strong> a day boundary is
 * computed in this codebase — only at the "what counts as right now"
 * boundary described above. Historical trend/rollup queries that bucket by
 * {@code DATE(time)} in SQL (baselines, correlations, multi-day trends) are
 * untouched: for a rolling multi-day average, a few readings landing in the
 * adjacent bucket has far less impact than it does on "today's" live score,
 * and converting 40+ SQL bucketing call sites to local-day boundaries is a
 * separate, larger, real piece of work (see the note this class's
 * introduction left in ReadinessController) — not bundled into this fix.
 */
@Component
public class LocalDayClock {

    private final Clock clock;

    @Autowired
    public LocalDayClock() {
        this(Clock.systemDefaultZone());
    }

    /** Explicit-clock seam — mainly so tests can fix "now" to an exact instant near a zone boundary. */
    public LocalDayClock(Clock clock) {
        this.clock = clock;
    }

    /** Convenience for a fixed real-time zone (uses the real current instant, just a chosen zone). */
    public LocalDayClock(ZoneId zone) {
        this(Clock.system(zone));
    }

    /** Today, in the user's real local timezone. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The instant local midnight starts today — the correct lower bound for "since today began" queries. */
    public Instant startOfToday() {
        return startOfLocalDay(today());
    }

    /** The instant local midnight starts on the given date. */
    public Instant startOfLocalDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }

    /** Which local calendar day an instant falls on. */
    public LocalDate localDateOf(Instant instant) {
        return instant.atZone(clock.getZone()).toLocalDate();
    }
}
