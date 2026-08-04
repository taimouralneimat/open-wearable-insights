/**
 * Named interface: biomarkers application services.
 *
 * <p>Exposed so the {@code ingestion} module can call {@link
 * com.openwearableinsights.api.biomarkers.application.BiomarkerCsvParser}
 * (stateless CSV detection/parsing) and persist through {@link
 * com.openwearableinsights.api.biomarkers.application.BiomarkerReadingRepository}
 * when a scanned import file is recognized as a biomarker CSV — mirroring
 * how {@code ingestion} already depends on {@code connections.application}
 * (via {@code ConnectorRegistry}) for vendor-device files.
 */
@org.springframework.modulith.NamedInterface("application")
package com.openwearableinsights.api.biomarkers.application;
