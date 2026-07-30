package com.openwearableinsights.api.journal.application;

import com.openwearableinsights.api.journal.domain.BehaviorCategory;
import com.openwearableinsights.api.journal.domain.JournalEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages journal entries and the curated behavior taxonomy.
 *
 * <p>The taxonomy is a suggested list for the UI — the journal_entries
 * table stores `behavior` as free text with no DB-level enum constraint,
 * so users can log behaviors outside this list too. Entries are always
 * stored with treated_as_untrusted = true: they're self-reported, never
 * used to override a computed score, and any correlation drawn from them
 * (EC7) must carry sample-size and uncertainty caveats.
 */
@Service
public class JournalService {

    private static final Logger log = LoggerFactory.getLogger(JournalService.class);

    /**
     * Curated taxonomy. Widened 2026-07-30 in direct response to user
     * feedback that the original 6-category/34-behavior list read as thin
     * next to competitor journal breadth (parity-matrix row #27). Every
     * entry here is a real, specific, loggable behavior — not padding to
     * hit a count. Deliberately does NOT add a menstrual-cycle/hormonal
     * category yet: parity row #21 flags that as needing its own
     * consent/privacy design pass, not a same-list bolt-on.
     */
    private static final List<BehaviorCategory> TAXONOMY = List.of(
            new BehaviorCategory("Sleep", List.of(
                    "Used sleep aid", "Screen time before bed", "Consistent bedtime",
                    "Nap taken", "Room temperature comfortable", "Caffeine after 2pm",
                    "Went to bed later than usual", "Woke during the night", "Used blue-light filter/glasses",
                    "Blackout curtains / dark room", "White noise or earplugs used", "Read before bed (no screen)",
                    "Shorter sleep than usual (<6h)", "Longer sleep than usual (>9h)"
            )),
            new BehaviorCategory("Nutrition", List.of(
                    "Alcohol", "Late meal (within 2h of bed)", "High-carb meal",
                    "Skipped a meal", "Hydration goal met", "Fasted >12h",
                    "Large meal", "High-sugar intake", "Mostly processed food today", "Mostly whole foods today",
                    "Under-ate today", "Overate / binge", "New or unfamiliar food", "Dined out",
                    "High-sodium meal", "High-protein day", "Low-fiber day"
            )),
            new BehaviorCategory("Recovery", List.of(
                    "Stretching/mobility work", "Cold exposure", "Sauna/heat exposure",
                    "Massage", "Active recovery session", "Full rest day",
                    "Foam rolling", "Compression garments used", "Contrast therapy (hot/cold)",
                    "Breathwork session", "Yoga / light movement", "Deload week"
            )),
            new BehaviorCategory("Mental wellbeing", List.of(
                    "High stress day", "Meditation/mindfulness", "Social connection",
                    "Work overload", "Travel/timezone change", "Illness/feeling unwell",
                    "Anxious mood", "Low mood / down day", "Felt calm and content", "Journaling done",
                    "Time in nature", "Heavy screen-time day", "Conflict or argument", "Major life event",
                    "Deep-focus / flow-state work"
            )),
            new BehaviorCategory("Training", List.of(
                    "RPE (rate of perceived exertion)", "Muscle soreness",
                    "New/unfamiliar exercise", "Injury/pain flag",
                    "Missed planned workout", "Exceeded planned intensity", "Felt strong during training",
                    "Felt flat / low energy during training", "Two-a-day session", "Taper / reduced-volume day",
                    "Trained in the heat", "Trained fasted"
            )),
            new BehaviorCategory("Supplements", List.of(
                    "Magnesium", "Melatonin", "Creatine", "Caffeine/pre-workout", "Other supplement",
                    "Vitamin D", "Omega-3 / fish oil", "Ashwagandha", "Electrolytes", "Protein supplement",
                    "Zinc", "Probiotic"
            )),
            new BehaviorCategory("Environment", List.of(
                    "Poor air quality day", "Altitude change", "Extreme heat exposure", "Extreme cold exposure",
                    "Noisy sleep environment", "Illness in household"
            ))
    );

    private final JdbcTemplate jdbcTemplate;

    public JournalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BehaviorCategory> getTaxonomy() {
        return TAXONOMY;
    }

    /**
     * Add a journal entry. Always stored as untrusted, self-reported input.
     */
    public JournalEntry addEntry(Long accountId, String category, String behavior,
                                  String value, String note) {
        Instant now = Instant.now();
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO journal_entries (account_id, time, behavior, value, note, treated_as_untrusted) " +
                "VALUES (?, ?, ?, ?, ?, true) RETURNING id",
                Long.class, accountId, Timestamp.from(now), formatBehavior(category, behavior), value, note
        );
        return new JournalEntry(id, accountId, now, category, behavior, value, note, true, now);
    }

    /**
     * List entries for an account, most recent first, optionally since a date.
     */
    public List<JournalEntry> listEntries(Long accountId, LocalDate since) {
        try {
            String sql = "SELECT id, account_id, time, behavior, value, note, " +
                    "treated_as_untrusted, created_at FROM journal_entries WHERE account_id = ?" +
                    (since != null ? " AND time >= ?" : "") +
                    " ORDER BY time DESC";
            List<Map<String, Object>> rows = since != null
                    ? jdbcTemplate.queryForList(sql, accountId, Timestamp.valueOf(since.atStartOfDay()))
                    : jdbcTemplate.queryForList(sql, accountId);

            List<JournalEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String stored = (String) row.get("behavior");
                String[] parts = parseBehavior(stored);
                entries.add(new JournalEntry(
                        ((Number) row.get("id")).longValue(),
                        ((Number) row.get("account_id")).longValue(),
                        ((Timestamp) row.get("time")).toInstant(),
                        parts[0],
                        parts[1],
                        (String) row.get("value"),
                        (String) row.get("note"),
                        (Boolean) row.get("treated_as_untrusted"),
                        ((Timestamp) row.get("created_at")).toInstant()
                ));
            }
            return entries;
        } catch (Exception e) {
            log.warn("Failed to list journal entries for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    // The schema stores a single `behavior` text column; encode
    // "category::behavior" so category survives round-tripping without a
    // migration. Falls back to ("Other", raw) for anything that predates
    // this encoding or was inserted without one.
    private String formatBehavior(String category, String behavior) {
        String cat = (category == null || category.isBlank()) ? "Other" : category;
        return cat + "::" + behavior;
    }

    private String[] parseBehavior(String stored) {
        if (stored != null && stored.contains("::")) {
            String[] split = stored.split("::", 2);
            return new String[]{split[0], split[1]};
        }
        return new String[]{"Other", stored};
    }
}
