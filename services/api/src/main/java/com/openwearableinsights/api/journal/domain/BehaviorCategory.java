package com.openwearableinsights.api.journal.domain;

import java.util.List;

/**
 * A category of trackable behaviors in the journal taxonomy — a curated
 * suggestion list for the UI, not an enforced constraint. Users may log
 * any free-text behavior; this is what's offered by default.
 */
public record BehaviorCategory(
        String name,
        List<String> behaviors
) {}
