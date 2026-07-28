package com.openwearableinsights.api.ingestion.adapter.in;

import com.openwearableinsights.api.ingestion.application.DryRunValidator;
import com.openwearableinsights.api.ingestion.application.DryRunValidator.DryRunSummary;
import com.openwearableinsights.api.ingestion.application.ImportBatchRepository;
import com.openwearableinsights.api.ingestion.application.ImportService;
import com.openwearableinsights.api.ingestion.application.ImportService.ImportProgress;
import com.openwearableinsights.api.ingestion.application.ImportService.UndoResult;
import com.openwearableinsights.api.ingestion.application.ImportDirectoryScanner;
import com.openwearableinsights.api.ingestion.application.ImportDirectoryScanner.ImportScanResult;
import com.openwearableinsights.api.ingestion.domain.ImportBatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the import workflow.
 *
 * <p>Phase 2: scans the local import directory (outside the repo), performs
 * dry-run validation with duplicate detection and unsupported-record reporting,
 * imports files with progress reporting, supports undo, and lists batches.
 * No real health data enters the repo; only checksums/metadata are stored
 * in PostgreSQL.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion", description = "Import wearable data from a local folder outside the repo")
public class IngestionController {

    private final ImportDirectoryScanner scanner;
    private final DryRunValidator validator;
    private final ImportService importService;

    public IngestionController(
            ImportDirectoryScanner scanner,
            DryRunValidator validator,
            ImportService importService
    ) {
        this.scanner = scanner;
        this.validator = validator;
        this.importService = importService;
    }

    @GetMapping("/scan")
    @Operation(summary = "Scan local import directory",
            description = "Scans the configured local import directory (outside the repo) and returns all files with detected format and support status.")
    public ImportScanResult scan() {
        return scanner.scan();
    }

    @GetMapping("/dry-run")
    @Operation(summary = "Dry-run validation of import files",
            description = "Validates all files in the import directory: computes content hashes, detects duplicates, counts records, and reports unsupported records. No data is persisted.")
    public DryRunSummary dryRun() {
        return validator.validateAll();
    }

    @PostMapping("/import")
    @Operation(summary = "Import all supported files",
            description = "Imports all supported files from the import directory. Returns per-file progress with explicit errors. Duplicates are skipped (idempotent).")
    public ImportProgress importAll() {
        return importService.importAll();
    }

    @PostMapping("/undo/{batchId}")
    @Operation(summary = "Undo an import batch",
            description = "Marks an import batch as undone and deletes associated records. Idempotent.")
    public UndoResult undo(@PathVariable Long batchId) {
        return importService.undoBatch(batchId);
    }

    @GetMapping("/batches")
    @Operation(summary = "List all import batches",
            description = "Returns all import batches for the default account, ordered by import time descending.")
    public List<ImportBatch> listBatches() {
        return importService.listBatches();
    }
}
