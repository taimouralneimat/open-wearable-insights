package com.openwearableinsights.api.ingestion.domain;

import java.util.List;

/**
 * A Garmin device found registered with the local Garmin Express desktop
 * app, with real wellness/activity files staged locally before Express
 * uploads them to Garmin Connect's cloud — the only real path to sleep/
 * steps/stress/HRV data without official Garmin API access (which is
 * currently on hold for new applicants; see ADR-0006).
 *
 * <p>Read-only discovery: this never modifies or deletes anything inside
 * Express's own folders, only lists what's there.
 */
public record GarminExpressDevice(
        String deviceId,
        List<GarminExpressCategory> categories,
        int totalFiles
) {
    public record GarminExpressCategory(String name, int fileCount) {}
}
