/**
 * Named interface: biomarkers domain types.
 *
 * <p>Exposed so the {@code ingestion} module can reference {@link
 * com.openwearableinsights.api.biomarkers.domain.ParsedBiomarkerRow} and
 * {@link com.openwearableinsights.api.biomarkers.domain.BiomarkerCsvParseResult}
 * when it dispatches a recognized biomarker CSV to this module's parser
 * (see {@code ingestion.application.DryRunValidator} and {@code
 * ingestion.application.ImportService}), the same cross-module pattern
 * {@code connections.domain} already uses for {@code ParsedMeasurement}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.openwearableinsights.api.biomarkers.domain;
