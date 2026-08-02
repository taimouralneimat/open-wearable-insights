package com.openwearableinsights.api.garminconnect.application;

/**
 * The native "DI" (device identity) OAuth2 bearer token pair Garmin Connect's
 * mobile-app API issues after a successful login. Persisted in place of the
 * password — see GarminConnectAuthClient.
 */
public record GarminTokens(String diToken, String diRefreshToken, String diClientId) {}
