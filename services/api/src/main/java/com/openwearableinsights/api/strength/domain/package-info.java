/**
 * Named interface: strength-training domain types.
 *
 * <p>Exposed so other modules (e.g. healthspan) may depend on {@code
 * StrengthActivityTrend}/{@code StrengthActivityTrendPoint} without
 * reaching into internal application/adapter packages. Mirrors the pattern
 * used by {@code readiness.domain.package-info}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.openwearableinsights.api.strength.domain;
