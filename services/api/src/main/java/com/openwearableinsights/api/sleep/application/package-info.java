/**
 * Named interface: sleep application services.
 *
 * <p>Exposed so other modules (e.g. healthspan — see {@code
 * healthspan.application.HealthspanService}, which composes the real sleep
 * consistency score into its own composite score) may invoke {@code
 * SleepInsightService} directly rather than reaching into this module's
 * internal package structure. Mirrors the pattern used by {@code
 * readiness.application.package-info} and {@code
 * trainingload.application.package-info}.
 */
@org.springframework.modulith.NamedInterface("application")
package com.openwearableinsights.api.sleep.application;
