package com.openwearableinsights.api.activities.domain;

import java.util.List;

/**
 * A day's activity summary.
 *
 * <p>Only steps are currently computed from real measurement data — the
 * project's data model doesn't yet have calories/activeMinutes/
 * activeZoneMinutes measurement types, so those fields are honestly
 * disclosed as unavailable rather than fabricated from steps.
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
