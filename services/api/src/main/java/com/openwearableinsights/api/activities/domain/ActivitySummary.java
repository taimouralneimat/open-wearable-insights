package com.openwearableinsights.api.activities.domain;

import java.util.List;

/**
 * A day's activity summary.
 *
 * <p>Steps are always real when any data exists. Calories and activeMinutes
 * are real once a Garmin Connect sync has run (see garminconnect module) —
 * null otherwise, not fabricated from steps. activeZoneMinutes (heart-rate-
 * zone-weighted) genuinely isn't tracked by either data source yet.
 *
 * @param confidence  overall confidence — "none" if steps itself has no data
 * @param limitations known caveats, including which fields aren't tracked yet
 */
public record ActivitySummary(
        int steps,
        Integer calories,
        Integer activeMinutes,
        Integer activeZoneMinutes,
        String timestamp,
        String confidence,
        List<String> limitations
) {}
