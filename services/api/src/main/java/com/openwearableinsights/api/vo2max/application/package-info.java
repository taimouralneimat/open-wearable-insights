/**
 * Named interface: VO2max application services.
 *
 * <p>Exposed so other modules (e.g. healthspan — see {@code
 * healthspan.application.HealthspanService}, which composes the real VO2max
 * trend into its own composite score) may invoke {@code Vo2MaxService}
 * directly rather than reaching into this module's internal package
 * structure. Mirrors the pattern used by {@code
 * readiness.application.package-info} and {@code
 * trainingload.application.package-info}.
 */
@org.springframework.modulith.NamedInterface("application")
package com.openwearableinsights.api.vo2max.application;
