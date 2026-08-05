/**
 * Named interface: sleep domain types.
 *
 * <p>Exposed so other modules (e.g. healthspan) may depend on {@code
 * SleepConsistency} without reaching into internal application/adapter
 * packages. Mirrors the pattern used by {@code readiness.domain.package-info}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.openwearableinsights.api.sleep.domain;
