package com.openwearableinsights.api.identity.domain;

/**
 * Who this account belongs to. Deliberately minimal — a display name and a
 * primary goal, not a full user-profile system. {@code primaryGoal} is
 * display/context only for now (shown back in the UI, not yet wired into
 * scoring or coaching emphasis) — see identity/application/ProfileService.
 */
public record Profile(
        Long accountId,
        String email,
        String displayName,
        String primaryGoal
) {}
