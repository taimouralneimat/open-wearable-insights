package com.openwearableinsights.api.connections.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A pluggable data-source connector: knows how to recognize and parse one
 * vendor's export format into canonical {@link ParsedMeasurement}s.
 *
 * <p>Garmin (FIT files) is the first implementation. Adding a new wearable
 * later (Apple Health, Oura, etc.) means adding a new class implementing
 * this interface — Spring picks up every WearableConnector bean
 * automatically via the connector registry, no changes needed to the
 * ingestion module itself.
 */
public interface WearableConnector {

    /** Short, stable identifier — e.g. "garmin". Used for logging/attribution. */
    String getConnectorId();

    /** Whether this connector can parse the given file, based on its name/extension. */
    boolean supports(String filename);

    /** Parse the file into canonical measurements. */
    List<ParsedMeasurement> parse(Path file) throws IOException;

    /**
     * Parse the file into activity/workout sessions, if the format has that
     * concept. Default empty — not every connector's source format encodes
     * bounded sessions (e.g. a sleep-only export).
     */
    default List<ParsedActivity> parseActivities(Path file) throws IOException {
        return List.of();
    }
}
