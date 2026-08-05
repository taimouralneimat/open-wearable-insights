/**
 * Named interface: training-load domain types.
 *
 * <p>Exposed so other modules (e.g. healthspan) may depend on {@code
 * TrainingLoadSummary} without reaching into internal application/adapter
 * packages. Mirrors the pattern used by {@code readiness.domain.package-info}.
 * {@code trainingload.application} was already exposed (see its own
 * package-info) but its domain types were not yet needed cross-module until
 * now.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.openwearableinsights.api.trainingload.domain;
