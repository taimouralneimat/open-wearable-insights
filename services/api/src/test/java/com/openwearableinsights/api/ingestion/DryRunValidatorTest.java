package com.openwearableinsights.api.ingestion;

import com.openwearableinsights.api.ingestion.application.DryRunValidator;
import com.openwearableinsights.api.ingestion.application.DryRunValidator.DryRunSummary;
import com.openwearableinsights.api.ingestion.application.ImportDirectoryScanner;
import com.openwearableinsights.api.ingestion.domain.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        validator = new DryRunValidator(scanner);
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
        var validator = new DryRunValidator(scanner);
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
    void fitFile_recognizedAndHashedButNotParsed() throws IOException {
        setupValidator();
        Files.write(tempDir.resolve("activity.fit"), "SYNTHETIC-FIT-PLACEHOLDER".getBytes());

        DryRunSummary summary = validator.validateAll();

        ValidationResult vr = summary.results().get(0);
        assertThat(vr.supported()).isTrue();
        assertThat(vr.detectedFormat()).isEqualTo("fit");
        assertThat(vr.contentHash()).isNotBlank();
        assertThat(vr.recordCount()).isZero(); // FIT parsing pending SDK
        assertThat(vr.warnings()).isNotEmpty();
    }
}
