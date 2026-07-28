package com.openwearableinsights.api.ingestion.domain;

import java.util.List;

/**
 * Result of dry-run validation of a file in the import directory.
 *
 * @param filename       file name validated
 * @param detectedFormat detected format
 * @param supported      whether the format is supported
 * @param contentHash    SHA-256 content hash (null if not computed)
 * @param recordCount    number of valid records found (0 if unsupported/empty)
 * @param duplicate      whether this file's content hash already exists in the DB
 * @param errors         list of validation errors (empty if valid)
 * @param warnings       list of validation warnings
 * @param unsupportedRecords list of unsupported record descriptions
 */
public record ValidationResult(
        String filename,
        String detectedFormat,
        boolean supported,
        String contentHash,
        int recordCount,
        boolean duplicate,
        List<String> errors,
        List<String> warnings,
        List<String> unsupportedRecords
) {}
