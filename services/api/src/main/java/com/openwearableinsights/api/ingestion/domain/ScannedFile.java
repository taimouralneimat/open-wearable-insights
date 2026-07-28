package com.openwearableinsights.api.ingestion.domain;

import java.time.Instant;

/**
 * A file discovered by scanning the local import directory.
 *
 * @param filename       file name (not full path)
 * @param sizeBytes      file size in bytes
 * @param lastModified   last-modified timestamp
 * @param detectedFormat detected format (json/csv/fit/tcx/gpx/unknown)
 * @param supported      whether this format is currently supported for import
 */
public record ScannedFile(
        String filename,
        long sizeBytes,
        Instant lastModified,
        String detectedFormat,
        boolean supported
) {}
