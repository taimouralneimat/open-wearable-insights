package com.openwearableinsights.api.connections.adapter.garmin;

import com.openwearableinsights.api.connections.domain.ParsedActivity;
import com.openwearableinsights.api.connections.domain.ParsedMeasurement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GarminFitConnector} against a real FIT binary built
 * by {@link SyntheticFitFixtureGenerator} (synthetic values only — no real
 * device data, per ADR-0006).
 */
class GarminFitConnectorTest {

    @TempDir
    Path tempDir;

    private final GarminFitConnector connector = new GarminFitConnector();

    @Test
    void supports_onlyDotFitFiles() {
        assertThat(connector.supports("activity.fit")).isTrue();
        assertThat(connector.supports("activity.FIT")).isTrue();
        assertThat(connector.supports("activity.json")).isFalse();
        assertThat(connector.supports(null)).isFalse();
    }

    @Test
    void parse_extractsRealValuesForEveryMetricType() throws IOException {
        Path fitFile = tempDir.resolve("synthetic.fit");
        SyntheticFitFixtureGenerator.generate(fitFile);

        List<ParsedMeasurement> measurements = connector.parse(fitFile);

        Map<String, Long> countsByType = measurements.stream()
                .collect(java.util.stream.Collectors.groupingBy(ParsedMeasurement::metricType, java.util.stream.Collectors.counting()));
        assertThat(countsByType).containsEntry("hr", 5L);
        assertThat(countsByType).containsEntry("steps", 3L);
        assertThat(countsByType).containsEntry("stress", 2L);
        assertThat(countsByType).containsEntry("sleep_stage", 4L);
        assertThat(countsByType).containsEntry("hrv", 1L);

        // Real values, not just counts.
        ParsedMeasurement firstHr = measurements.stream().filter(m -> m.metricType().equals("hr")).findFirst().orElseThrow();
        assertThat(firstHr.value()).isEqualTo(58.0);
        assertThat(firstHr.unit()).isEqualTo("bpm");

        ParsedMeasurement firstSteps = measurements.stream().filter(m -> m.metricType().equals("steps")).findFirst().orElseThrow();
        assertThat(firstSteps.value()).isEqualTo(120.0);

        ParsedMeasurement hrv = measurements.stream().filter(m -> m.metricType().equals("hrv")).findFirst().orElseThrow();
        assertThat(hrv.value()).isGreaterThan(0.0);

        // sleep_stage values must match packages/test-data/synthetic_generator.py's
        // SLEEP_STAGES index encoding: ["deep", "rem", "light", "awake"].
        List<Double> sleepStageValues = measurements.stream()
                .filter(m -> m.metricType().equals("sleep_stage"))
                .map(ParsedMeasurement::value)
                .sorted()
                .toList();
        assertThat(sleepStageValues).containsExactly(0.0, 1.0, 2.0, 3.0);
    }

    @Test
    void parseActivities_extractsRealSessionSummary() throws IOException {
        Path fitFile = tempDir.resolve("synthetic.fit");
        SyntheticFitFixtureGenerator.generate(fitFile);

        List<ParsedActivity> activities = connector.parseActivities(fitFile);

        assertThat(activities).hasSize(1);
        ParsedActivity session = activities.get(0);
        assertThat(session.sport()).isEqualTo("running");
        assertThat(session.durationSeconds()).isEqualTo(1800.0);
        assertThat(session.distanceMeters()).isEqualTo(5000.0);
        assertThat(session.avgHeartRate()).isEqualTo(148);
        assertThat(session.maxHeartRate()).isEqualTo(172);
        assertThat(session.calories()).isEqualTo(320);
        assertThat(session.hrZoneSeconds()).containsExactly(60.0, 300.0, 900.0, 480.0, 60.0);
        assertThat(session.endTime()).isEqualTo(session.startTime().plusSeconds(1800));
    }

    @Test
    void parse_corruptFile_throwsRatherThanSilentlyReturningEmpty() throws IOException {
        Path badFile = tempDir.resolve("bad.fit");
        Files.write(badFile, "NOT-A-REAL-FIT-FILE".getBytes());

        assertThatThrownBy(() -> connector.parse(badFile)).isInstanceOf(RuntimeException.class);
    }
}
