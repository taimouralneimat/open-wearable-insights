/**
 * Named interface: training-load application services.
 *
 * <p>Exposed so other modules (currently readiness — see
 * {@code readiness.application.CurrentMetricsService}) can obtain a real
 * acute/chronic training-load figure without depending on this module's
 * internal package structure. Mirrors the pattern used by
 * {@code readiness.application.package-info}.
 */
@org.springframework.modulith.NamedInterface("application")
package com.openwearableinsights.api.trainingload.application;
