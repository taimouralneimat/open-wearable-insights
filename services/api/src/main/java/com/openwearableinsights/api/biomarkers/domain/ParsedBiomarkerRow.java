package com.openwearableinsights.api.biomarkers.domain;

import java.time.LocalDate;

/**
 * One row parsed from a biomarker CSV, before persistence. In the same
 * canonical-shape spirit as {@code connections.domain.ParsedMeasurement},
 * but for lab readings rather than continuous wearable telemetry.
 *
 * @param category resolved category: taken from the CSV's own {@code
 *                 category} column when present, otherwise looked up in
 *                 {@link com.openwearableinsights.api.biomarkers.application.BiomarkerReferenceCatalog}
 *                 by name, otherwise {@code null} — never guessed
 * @param referenceLow  taken verbatim from the CSV's {@code reference_low}
 *                      column, or {@code null} if absent/blank/unparseable
 * @param referenceHigh taken verbatim from the CSV's {@code reference_high}
 *                      column, or {@code null} if absent/blank/unparseable
 */
public record ParsedBiomarkerRow(
        LocalDate readingDate,
        String biomarkerName,
        double value,
        String unit,
        Double referenceLow,
        Double referenceHigh,
        String category
) {}
