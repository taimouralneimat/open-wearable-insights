package com.openwearableinsights.api.ingestion;

import com.openwearableinsights.api.biomarkers.application.BiomarkerReadingRepository;
import com.openwearableinsights.api.connections.adapter.garmin.GarminFitConnector;
import com.openwearableinsights.api.connections.application.ConnectorRegistry;
import com.openwearableinsights.api.ingestion.application.ActivityRepository;
import com.openwearableinsights.api.ingestion.application.DryRunValidator;
import com.openwearableinsights.api.ingestion.application.DryRunValidator.DryRunSummary;
import com.openwearableinsights.api.ingestion.application.ImportBatchRepository;
import com.openwearableinsights.api.ingestion.application.ImportService;
import com.openwearableinsights.api.ingestion.application.ImportService.FileImportResult;
import com.openwearableinsights.api.ingestion.application.ImportService.ImportProgress;
import com.openwearableinsights.api.ingestion.application.ImportService.UndoResult;
import com.openwearableinsights.api.ingestion.application.MeasurementRepository;
import com.openwearableinsights.api.ingestion.domain.ImportBatch;
import com.openwearableinsights.api.ingestion.domain.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ImportService} using Mockito.
 *
 * <p>Tests the import workflow logic: successful import, duplicate detection
 * against DB-persisted hashes, undo, and per-file error handling.
 * Uses a fake TransactionTemplate that executes callbacks synchronously.
 * Uses synthetic data only — no real health data.
 */
class ImportServiceTest {

    @TempDir
    Path tempDir;

    private ImportBatchRepository batchRepository;
    private BiomarkerReadingRepository biomarkerReadingRepository;
    private ImportService importService;

    @BeforeEach
    void setUp() throws IOException {
        batchRepository = mock(ImportBatchRepository.class);
        biomarkerReadingRepository = mock(BiomarkerReadingRepository.class);

        // Create a real DryRunValidator pointing at the temp dir
        var scanner = new com.openwearableinsights.api.ingestion.application.ImportDirectoryScanner(tempDir.toString());
        var connectorRegistry = new ConnectorRegistry(List.of(new GarminFitConnector()));
        var validator = new DryRunValidator(scanner, connectorRegistry);

        // Fake PlatformTransactionManager that executes callbacks synchronously
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            @Override
            public void commit(org.springframework.transaction.TransactionStatus status) {}
            @Override
            public void rollback(org.springframework.transaction.TransactionStatus status) {}
        };

        importService = new ImportService(validator, batchRepository, connectorRegistry, mock(MeasurementRepository.class), mock(ActivityRepository.class), biomarkerReadingRepository, txManager);
    }

    @Test
    void successfulImport_persistsBatchToDb() throws IOException {
        String json = """
            [
              {"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0,"unit":"bpm"},
              {"time":"2026-07-27T09:00:00Z","metric_type":"hrv","value":45.0,"unit":"ms"}
            ]
            """;
        Files.writeString(tempDir.resolve("measurements.json"), json);

        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(false);
        when(batchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.findByAccountIdOrderByImportedAtDesc(any()))
                .thenReturn(List.of(new ImportBatch(1L, "json", "hash123", "measurements.json", 2)));

        ImportProgress progress = importService.importAll();

        assertThat(progress.exists()).isTrue();
        assertThat(progress.imported()).isEqualTo(1);
        assertThat(progress.failed()).isZero();
        assertThat(progress.totalRecords()).isEqualTo(2);

        // Verify the batch was saved with correct fields
        ArgumentCaptor<ImportBatch> captor = ArgumentCaptor.forClass(ImportBatch.class);
        verify(batchRepository).save(captor.capture());
        ImportBatch saved = captor.getValue();
        assertThat(saved.getFileName()).isEqualTo("measurements.json");
        assertThat(saved.getRecordCount()).isEqualTo(2);
        assertThat(saved.getAccountId()).isEqualTo(1L);
        assertThat(saved.getSource()).isEqualTo("json");
    }

    @Test
    void duplicateImport_isSkippedIdempotently() throws IOException {
        String json = """
            [{"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0,"unit":"bpm"}]
            """;
        Files.writeString(tempDir.resolve("data.json"), json);

        // Simulate that the hash already exists in DB
        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(true);

        ImportProgress progress = importService.importAll();

        assertThat(progress.imported()).isZero();
        assertThat(progress.skipped()).isEqualTo(1);
        assertThat(progress.files().get(0).status()).isEqualTo("duplicate");
        verify(batchRepository, never()).save(any());
    }

    @Test
    void undo_marksBatchAsUndone() throws IOException {
        String json = """
            [{"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0,"unit":"bpm"}]
            """;
        Files.writeString(tempDir.resolve("undo_test.json"), json);

        ImportBatch batch = new ImportBatch(1L, "json", "hash123", "undo_test.json", 1);
        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(false);
        // Simulate that save() sets the id
        when(batchRepository.save(any())).thenAnswer(invocation -> {
            ImportBatch saved = invocation.getArgument(0);
            // In a real DB, the id would be auto-generated. For the test,
            // we return the batch as-is (id stays null for new batches).
            return saved;
        });
        // For undo, findById returns a batch with id=1L
        ImportBatch persistedBatch = new ImportBatch(1L, "json", "hash123", "undo_test.json", 1);
        // Use reflection to set the id field
        try {
            var idField = ImportBatch.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(persistedBatch, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(batchRepository.findById(1L)).thenReturn(Optional.of(persistedBatch));

        // First import
        importService.importAll();

        // Undo
        UndoResult result = importService.undoBatch(1L);
        assertThat(result.success()).isTrue();
        assertThat(result.batchId()).isEqualTo(1L);

        // Verify batch was marked undone
        // undoBatch calls findById(1L) which returns our batch object,
        // then calls markUndone() on it, then save().
        assertThat(persistedBatch.getStatus()).isEqualTo("undone");
        assertThat(persistedBatch.getUndoAt()).isNotNull();
    }

    @Test
    void undo_nonexistentBatch_returnsFailure() {
        when(batchRepository.findById(99999L)).thenReturn(Optional.empty());

        UndoResult result = importService.undoBatch(99999L);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("not found");
    }

    @Test
    void undo_alreadyUndoneBatch_returnsFailure() {
        ImportBatch batch = mock(ImportBatch.class);
        when(batch.getStatus()).thenReturn("undone");
        when(batch.getId()).thenReturn(5L);
        when(batch.getFileName()).thenReturn("test.json");
        when(batchRepository.findById(5L)).thenReturn(Optional.of(batch));

        UndoResult result = importService.undoBatch(5L);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("already undone");
    }

    @Test
    void mixedFiles_importSupportedSkipUnsupported() throws IOException {
        String json = """
            [{"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0,"unit":"bpm"}]
            """;
        Files.writeString(tempDir.resolve("data.json"), json);
        Files.writeString(tempDir.resolve("readme.txt"), "not health data");
        Files.writeString(tempDir.resolve("notes.md"), "# Notes");

        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(false);
        when(batchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportProgress progress = importService.importAll();

        assertThat(progress.imported()).isEqualTo(1);
        assertThat(progress.skipped()).isEqualTo(2);
        assertThat(progress.failed()).isZero();

        List<FileImportResult> results = progress.files();
        assertThat(results).hasSize(3);
        assertThat(results).anyMatch(r -> r.status().equals("imported"));
        assertThat(results).filteredOn(r -> r.status().equals("skipped")).hasSize(2);
    }

    @Test
    void perFileError_doesntPoisonRest() throws IOException {
        // Two valid JSON files
        String json1 = """
            [{"time":"2026-07-27T08:00:00Z","metric_type":"hr","value":60.0,"unit":"bpm"}]
            """;
        String json2 = """
            [{"time":"2026-07-27T09:00:00Z","metric_type":"hrv","value":45.0,"unit":"ms"}]
            """;
        Files.writeString(tempDir.resolve("file1.json"), json1);
        Files.writeString(tempDir.resolve("file2.json"), json2);

        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(false);
        // First save succeeds, second throws
        when(batchRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new RuntimeException("DB connection lost"));

        ImportProgress progress = importService.importAll();

        // One should succeed, one should fail — not both
        assertThat(progress.imported()).isEqualTo(1);
        assertThat(progress.failed()).isEqualTo(1);
    }

    @Test
    void emptyDirectory_returnsEmptyProgress() {
        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(false);

        ImportProgress progress = importService.importAll();

        assertThat(progress.exists()).isTrue();
        assertThat(progress.imported()).isZero();
        assertThat(progress.skipped()).isZero();
        assertThat(progress.failed()).isZero();
        assertThat(progress.files()).isEmpty();
    }

    @Test
    void nonexistentDirectory_returnsError() {
        var scanner = new com.openwearableinsights.api.ingestion.application.ImportDirectoryScanner("/nonexistent/path");
        var connectorRegistry = new ConnectorRegistry(List.of(new GarminFitConnector()));
        var validator = new DryRunValidator(scanner, connectorRegistry);
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition d) { return new SimpleTransactionStatus(); }
            @Override public void commit(org.springframework.transaction.TransactionStatus s) {}
            @Override public void rollback(org.springframework.transaction.TransactionStatus s) {}
        };
        var service = new ImportService(validator, batchRepository, connectorRegistry, mock(MeasurementRepository.class), mock(ActivityRepository.class), mock(BiomarkerReadingRepository.class), txManager);

        ImportProgress progress = service.importAll();

        assertThat(progress.exists()).isFalse();
        assertThat(progress.errorMessage()).isNotNull();
    }

    @Test
    void undoThenReimport_succeeds() throws IOException {
        String json = "[{\"time\":\"2026-07-27T08:00:00Z\",\"metric_type\":\"hr\",\"value\":60.0,\"unit\":\"bpm\"}]\n";
        Files.writeString(tempDir.resolve("reimport_test.json"), json);

        ImportBatch persistedBatch = new ImportBatch(1L, "json", "hash123", "reimport_test.json", 1);
        try {
            var idField = ImportBatch.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(persistedBatch, 10L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // First import: hash not in DB -> import succeeds
        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(false);
        when(batchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.findById(10L)).thenReturn(Optional.of(persistedBatch));

        ImportProgress firstImport = importService.importAll();
        assertThat(firstImport.imported()).isEqualTo(1);

        // Undo the batch
        UndoResult undoResult = importService.undoBatch(10L);
        assertThat(undoResult.success()).isTrue();
        assertThat(persistedBatch.getStatus()).isEqualTo("undone");

        // Re-import: the hash exists in DB but with status 'undone',
        // so existsByContentHashAndStatus(hash, "imported") should return false.
        // The mock returns false (since the batch is undone, not "imported").
        ImportProgress secondImport = importService.importAll();
        assertThat(secondImport.imported()).isEqualTo(1);
        assertThat(secondImport.skipped()).isZero();
        assertThat(secondImport.failed()).isZero();
    }

    @Test
    void biomarkerCsv_isRecognizedAndPersistedThroughBiomarkerReadingRepository() throws IOException {
        // A biomarker CSV isn't handled by any WearableConnector (those are
        // vendor-device formats) — this exercises the dedicated branch added
        // to ImportService for it (see DryRunValidator/BiomarkerCsvParser).
        String csv = """
            date,biomarker_name,value,unit,reference_low,reference_high
            2026-07-01,LDL Cholesterol,95,mg/dL,0,100
            2026-07-01,HDL Cholesterol,55,mg/dL,40,
            """;
        Files.writeString(tempDir.resolve("labs.csv"), csv);

        when(batchRepository.existsByContentHashAndStatus(any(), any())).thenReturn(false);
        when(batchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(biomarkerReadingRepository.persist(eq(1L), any(), any())).thenReturn(2);

        ImportProgress progress = importService.importAll();

        assertThat(progress.imported()).isEqualTo(1);
        assertThat(progress.failed()).isZero();
        assertThat(progress.totalRecords()).isEqualTo(2);

        ArgumentCaptor<List<com.openwearableinsights.api.biomarkers.domain.ParsedBiomarkerRow>> rowsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(biomarkerReadingRepository).persist(eq(1L), any(), rowsCaptor.capture());
        List<com.openwearableinsights.api.biomarkers.domain.ParsedBiomarkerRow> rows = rowsCaptor.getValue();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).biomarkerName()).isEqualTo("LDL Cholesterol");
        assertThat(rows.get(0).value()).isEqualTo(95.0);
        assertThat(rows.get(0).referenceLow()).isEqualTo(0.0);
        assertThat(rows.get(0).referenceHigh()).isEqualTo(100.0);
        // Auto-filled from the reference catalog since the CSV row itself
        // has no category column.
        assertThat(rows.get(0).category()).isEqualTo("Lipid/Cardiovascular");
        assertThat(rows.get(1).referenceHigh()).isNull();
    }
}
