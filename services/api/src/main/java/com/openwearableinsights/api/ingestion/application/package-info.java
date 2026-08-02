/**
 * Ingestion application services — import workflow orchestration.
 *
 * <p>Exposed as a named interface so other modules that persist real
 * measurements from their own connectors (e.g. garminconnect) can reuse
 * {@code MeasurementRepository}/{@code ImportBatchRepository} instead of
 * duplicating the provenance/measurements persistence logic.
 */
@org.springframework.modulith.NamedInterface("application")
package com.openwearableinsights.api.ingestion.application;
