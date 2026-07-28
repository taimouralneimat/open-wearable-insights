package com.openwearableinsights.api.journal.domain;

import java.time.Instant;

/**
 * A single journal entry — a user-logged behavior or observation.
 *
 * <p>Per the journal_entries schema (V01), entries are always treated as
 * untrusted input: they're stored verbatim with provenance, never used to
 * override a computed score, and any correlation drawn from them must
 * carry sample-size and uncertainty caveats (see EC7).
 *
 * @param category  the behavior's category (see BehaviorTaxonomy) — stored
 *                  for grouping/filtering, not enforced as an enum at the
 *                  DB level so custom behaviors outside the curated list
 *                  remain possible
 * @param behavior  the specific behavior logged (e.g. "Alcohol", "Cold exposure")
 * @param value     optional value (e.g. "2 units", "10 min", "yes"/"no")
 * @param note      optional free-text note
 */
public record JournalEntry(
        Long id,
        Long accountId,
        Instant time,
        String category,
        String behavior,
        String value,
        String note,
        boolean treatedAsUntrusted,
        Instant createdAt
) {}
