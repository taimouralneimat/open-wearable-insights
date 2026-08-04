package com.openwearableinsights.api.biomarkers.domain;

import java.util.List;

/**
 * Result of parsing a biomarker CSV: valid rows plus per-row problems,
 * matching the same "explicit errors, no silent drops" discipline {@code
 * ingestion.domain.ValidationResult} uses for other import formats.
 *
 * @param rows       successfully parsed rows
 * @param rowErrors  one entry per row that couldn't be parsed at all
 *                    (missing required field, bad date/number) — that row is
 *                    excluded from {@code rows}, not silently dropped
 * @param warnings   non-fatal issues (e.g. an unparseable optional
 *                    reference-range cell) — the row is still included in
 *                    {@code rows}, just without that optional field
 */
public record BiomarkerCsvParseResult(
        List<ParsedBiomarkerRow> rows,
        List<String> rowErrors,
        List<String> warnings
) {}
