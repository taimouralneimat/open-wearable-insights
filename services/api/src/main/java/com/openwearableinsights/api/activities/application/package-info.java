/**
 * Named interface: activities application services.
 *
 * <p>Exposed so other modules (e.g. milestones — see {@code
 * milestones.application.MilestoneService}, which composes the real step
 * trend computed here into its own personal-record detection) may invoke
 * {@code ActivityInsightService} directly rather than reaching into this
 * module's internal package structure. Mirrors the pattern used by {@code
 * readiness.application.package-info} and {@code
 * vo2max.application.package-info}.
 */
@org.springframework.modulith.NamedInterface("application")
package com.openwearableinsights.api.activities.application;
