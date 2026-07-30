package com.openwearableinsights.api.journal.domain;

import java.util.List;

/**
 * Identity-based habit framing (Atomic Habits): every logged behavior in a
 * category relevant to the account's stated primary goal counts as a "vote"
 * for becoming that kind of person, distinct from just a raw entry count.
 *
 * @param goal               the profile's primary goal; null if not set —
 *                            votes are meaningless without a stated identity
 * @param relevantCategories the journal categories counted toward this goal
 */
public record IdentityVotes(
        String goal,
        int votes,
        int totalEntries,
        int windowDays,
        List<String> relevantCategories
) {}
