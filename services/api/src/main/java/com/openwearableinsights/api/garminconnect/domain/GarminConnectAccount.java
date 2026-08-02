package com.openwearableinsights.api.garminconnect.domain;

import java.time.Instant;

/**
 * Persisted connection state for the local account's Garmin Connect login.
 * Only OAuth tokens are stored — never the raw password (see
 * GarminConnectAuthClient).
 */
public record GarminConnectAccount(
        Long accountId,
        String status, // disconnected/mfa_required/connected/error
        String email,
        String lastError,
        Instant connectedAt,
        Instant lastSyncAt
) {
    public static GarminConnectAccount disconnected(Long accountId) {
        return new GarminConnectAccount(accountId, "disconnected", null, null, null, null);
    }
}
