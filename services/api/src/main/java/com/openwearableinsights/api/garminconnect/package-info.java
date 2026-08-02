/**
 * Module: garminconnect
 *
 * <p>A from-scratch Java client for Garmin Connect's unofficial mobile-app
 * SSO/OAuth2 login flow, used to pull the account's real historical
 * wellness data (sleep, HRV, stress, resting HR, steps) directly from
 * Garmin's own servers with the user's own credentials. See
 * GarminConnectAuthClient for why this exists instead of Garmin's official
 * Health API (blocked to individual developers) or a USB/Garmin-Express
 * workflow (only surfaces a transient sync buffer, not history).
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module
 * strategy.
 */
package com.openwearableinsights.api.garminconnect;
