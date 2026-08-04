package com.openwearableinsights.api.ingestion;

import com.openwearableinsights.api.connections.adapter.garmin.GarminFitConnector;
import com.openwearableinsights.api.connections.adapter.garmin.SyntheticFitFixtureGenerator;
import com.openwearableinsights.api.connections.application.ConnectorRegistry;
import com.openwearableinsights.api.ingestion.application.DryRunValidator;
import com.openwearableinsights.api.ingestion.application.DryRunValidator.DryRunSummary;
import com.openwearableinsights.api.ingestion.application.ImportDirectoryScanner;
import com.openwearableinsights.api.ingestion.domain.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DryRunValidator}.
 *
 * <p>Tests hash correctness, duplicate detection, unsupported-record reporting,
 * and record counting for JSON and CSV files. Uses a temp directory — no real
 * health data.
 */
class DryRunValidatorTest {

    @TempDir
    Path tempDir;

    private DryRunValidator validator;

    private void setupValidator() {
        var scanner = new ImportDirectoryScanner(tempDir.toString());
        validator = new DryRunValidator(scanner, new ConnectorRegistry(List.of(new GarminFitConnector())));
    }

    @Test
    void jsonArrayFile_countsRecordsAndComputesHash() throws IOException {
        setupValidator();
        String json = """
            [
              {"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0,"unit":"bpm"},
              {"time":"2026-07-27T09:00:00Z","metric_type":"hrv","value":45.0,"unit":"ms"},
              {"time":"2026-07-27T10:00:00Z","metric_type":"rhr","value":52.0,"unit":"bpm"}
            ]
            """;
        Files.writeString(tempDir.resolve("measurements.json"), json);

        DryRunSummary summary = validator.validateAll();

        assertThat(summary.exists()).isTrue();
        assertThat(summary.results()).hasSize(1);
        ValidationResult vr = summary.results().get(0);
        assertThat(vr.supported()).isTrue();
        assertThat(vr.detectedFormat()).isEqualTo("json");
        assertThat(vr.recordCount()).isEqualTo(3);
        assertThat(vr.contentHash()).isNotBlank();
        assertThat(vr.errors()).isEmpty();
        assertThat(vr.duplicate()).isFalse();
    }

    @Test
    void csvFile_countsRecordsAndSkipsHeader() throws IOException {
        setupValidator();
        String csv = """
            time,metric_type,value,unit
            2026-07-27T08:00:00Z,hr,60.0,bpm
            2026-07-27T09:00:00Z,hrv,45.0,ms
            2026-07-27T10:00:00Z,rhr,52.0,bpm
            """;
        Files.writeString(tempDir.resolve("data.csv"), csv);

        DryRunSummary summary = validator.validateAll();

        assertThat(summary.results()).hasSize(1);
        ValidationResult vr = summary.results().get(0);
        assertThat(vr.supported()).isTrue();
        assertThat(vr.detectedFormat()).isEqualTo("csv");
        assertThat(vr.recordCount()).isEqualTo(3);
        assertThat(vr.contentHash()).isNotBlank();
    }

    @Test
    void unsupportedFormatFile_reportedAsUnsupported() throws IOException {
        setupValidator();
        Files.writeString(tempDir.resolve("readme.txt"), "This is not health data.");

        DryRunSummary summary = validator.validateAll();

        assertThat(summary.results()).hasSize(1);
        ValidationResult vr = summary.results().get(0);
        assertThat(vr.supported()).isFalse();
        assertThat(vr.detectedFormat()).isEqualTo("unknown");
        assertThat(vr.recordCount()).isZero();
        assertThat(vr.contentHash()).isNull();
        assertThat(vr.errors()).isNotEmpty();
        assertThat(summary.unsupportedFiles()).isEqualTo(1);
    }

    @Test
    void duplicateFiles_detectedByContentHash() throws IOException {
        setupValidator();
        String json = """
            [{"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0,"unit":"bpm"}]
            """;
        Files.writeString(tempDir.resolve("file1.json"), json);
        Files.writeString(tempDir.resolve("file2.json"), json);

        DryRunSummary summary = validator.validateAll();

        assertThat(summary.results()).hasSize(2);
        ValidationResult first = summary.results().get(0);
        ValidationResult second = summary.results().get(1);
        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.contentHash()).isEqualTo(first.contentHash());
        assertThat(summary.duplicates()).isEqualTo(1);
    }

    @Test
    void csvWithMalformedLines_reportsUnsupportedRecords() throws IOException {
        setupValidator();
        String csv = """
            time,metric_type,value,unit
            2026-07-27T08:00:00Z,hr,60.0,bpm
            this_line_has_no_commas
            2026-07-27T09:00:00Z,hrv,45.0,ms
            """;
        Files.writeString(tempDir.resolve("bad.csv"), csv);

        DryRunSummary summary = validator.validateAll();

        ValidationResult vr = summary.results().get(0);
        assertThat(vr.recordCount()).isEqualTo(2); // 2 valid lines (header skipped, 1 bad)
        assertThat(vr.unsupportedRecords()).isNotEmpty();
        assertThat(vr.unsupportedRecords().get(0)).contains("fewer than 2 fields");
    }

    @Test
    void emptyDirectory_returnsEmptyResults() {
        setupValidator();
        DryRunSummary summary = validator.validateAll();
        assertThat(summary.exists()).isTrue();
        assertThat(summary.results()).isEmpty();
        assertThat(summary.totalRecords()).isZero();
    }

    @Test
    void nonexistentDirectory_returnsError() {
        var scanner = new ImportDirectoryScanner("/nonexistent/path/that/does/not/exist");
        var validator = new DryRunValidator(scanner, new ConnectorRegistry(List.of(new GarminFitConnector())));
        DryRunSummary summary = validator.validateAll();
        assertThat(summary.exists()).isFalse();
        assertThat(summary.errorMessage()).isNotNull();
    }

    @Test
    void jsonWithMeasurementsArray_countsNestedRecords() throws IOException {
        setupValidator();
        String json = """
            {
              "source": "garmin",
              "measurements": [
                {"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0},
                {"time":"2026-07-27T09:00:00Z","metric_type":"hr","value":62.0}
              ]
            }
            """;
        Files.writeString(tempDir.resolve("nested.json"), json);

        DryRunSummary summary = validator.validateAll();

        ValidationResult vr = summary.results().get(0);
        assertThat(vr.recordCount()).isEqualTo(2);
    }

    @Test
    void fitFile_parsedViaGarminConnector() throws IOException {
        setupValidator();
        Path fitFile = tempDir.resolve("activity.fit");
        SyntheticFitFixtureGenerator.generate(fitFile);

        DryRunSummary summary = validator.validateAll();

        ValidationResult vr = summary.results().get(0);
        assertThat(vr.supported()).isTrue();
        assertThat(vr.detectedFormat()).isEqualTo("fit");
        assertThat(vr.contentHash()).isNotBlank();
        // 5 hr + 3 steps + 2 stress + 4 sleep_stage + 1 hrv + 1 activity session (see SyntheticFitFixtureGenerator)
        assertThat(vr.recordCount()).isEqualTo(16);
    }

    @Test
    void corruptFitFile_reportsErrorNotSilentZero() throws IOException {
        setupValidator();
        Files.write(tempDir.resolve("bad.fit"), "NOT-A-REAL-FIT-FILE".getBytes());

        DryRunSummary summary = validator.validateAll();

        ValidationResult vr = summary.results().get(0);
        assertThat(vr.supported()).isTrue();
        assertThat(vr.detectedFormat()).isEqualTo("fit");
        assertThat(vr.errors()).isNotEmpty();
    }

    @Test
    void biomarkerCsvFile_detectedAndCountedViaRealParser() throws IOException {
        // Distinct header shape (date,biomarker_name,value,unit) from the
        // generic vendor CSV (time,metric_type,value,unit) tested above —
        // this routes to BiomarkerCsvParser instead of the generic counter.
        setupValidator();
        String csv = """
            date,biomarker_name,value,unit,reference_low,reference_high
            2026-07-01,LDL Cholesterol,95,mg/dL,0,100
            2026-07-01,Glucose,88,mg/dL,70,99
            """;
        Files.writeString(tempDir.resolve("labs.csv"), csv);

        DryRunSummary summary = validator.validateAll();

        ValidationResult vr = summary.results().get(0);
        assertThat(vr.supported()).isTrue();
        assertThat(vr.detectedFormat()).isEqualTo("csv");
        assertThat(vr.recordCount()).isEqualTo(2);
        assertThat(vr.contentHash()).isNotBlank();
        assertThat(vr.errors()).isEmpty();
        assertThat(vr.unsupportedRecords()).isEmpty();
    }

    @Test
    void biomarkerCsvWithBadRow_reportsRowErrorButKeepsValidRows() throws IOException {
        setupValidator();
        String csv = """
            date,biomarker_name,value,unit
            2026-07-01,LDL Cholesterol,95,mg/dL
            not-a-date,Glucose,88,mg/dL
            2026-07-02,HDL Cholesterol,not-a-number,mg/dL
            """;
        Files.writeString(tempDir.resolve("labs_bad.csv"), csv);

        DryRunSummary summary = validator.validateAll();

        ValidationResult vr = summary.results().get(0);
        assertThat(vr.recordCount()).isEqualTo(1);
        assertThat(vr.unsupportedRecords()).hasSize(2);
        assertThat(vr.unsupportedRecords()).anyMatch(e -> e.contains("invalid date"));
        assertThat(vr.unsupportedRecords()).anyMatch(e -> e.contains("invalid numeric value"));
    }
}
