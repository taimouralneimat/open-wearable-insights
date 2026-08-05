/**
 * Named interface: VO2max domain types.
 *
 * <p>Exposed so other modules (e.g. healthspan) may depend on {@code
 * Vo2MaxEstimate}/{@code Vo2MaxTrendPoint} without reaching into internal
 * application/adapter packages. Mirrors the pattern used by {@code
 * readiness.domain.package-info}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.openwearableinsights.api.vo2max.domain;
