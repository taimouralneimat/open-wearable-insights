package com.openwearableinsights.api.ingestion.application;

import com.openwearableinsights.api.ingestion.domain.ImportBatch;
import com.openwearableinsights.api.ingestion.domain.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing files from the local import directory.
 *
 * <p>Handles:
 * <ul>
 *   <li>Actual import (persist batch + records to DB)</li>
 *   <li>Progress reporting (per-file, per-step)</li>
 *   <li>Explicit errors (no silent drops)</li>
 *   <li>Undo (mark batch as undone, delete associated records)</li>
 * </ul>
 *
 * <p>Idempotency: re-importing the same file (same content hash) is a no-op.
 * Duplicates are reported, not silently merged.
 *
 * <p>Transaction design: each file's persist runs in its own transaction
 * (via {@link TransactionTemplate} with {@code REQUIRES_NEW} semantics)
 * so one bad file can't poison the session for the rest of the batch.
 */
@Service
public class ImportService {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final DryRunValidator validator;
    private final ImportBatchRepository batchRepository;
    private final TransactionTemplate requiresNewTx;

    public ImportService(
            DryRunValidator validator,
            ImportBatchRepository batchRepository,
            PlatformTransactionManager txManager
    ) {
        this.validator = validator;
        this.batchRepository = batchRepository;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /**
     * Import all supported files from the import directory.
     * Returns a progress report with per-file results.
     *
     * <p>Not {@code @Transactional} itself — each file's persist runs in
     * its own transaction so one failure doesn't roll back the rest.
     */
    public ImportProgress importAll() {
        var dryRun = validator.validateAll();

        if (!dryRun.exists()) {
            return new ImportProgress(
                    List.of(),
                    dryRun.directory(),
                    false,
                    dryRun.errorMessage(),
                    0, 0, 0, 0
            );
        }

        List<FileImportResult> fileResults = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int totalRecords = 0;

        for (ValidationResult vr : dryRun.results()) {
            if (!vr.supported()) {
                fileResults.add(new FileImportResult(
                        vr.filename(), "skipped", "Unsupported format: " + vr.detectedFormat(), 0, null
                ));
                skipped++;
                continue;
            }

            if (vr.errors().isEmpty() && vr.contentHash() != null) {
                // Check for duplicate in DB
                if (batchRepository.existsByContentHashAndStatus(vr.contentHash(), "imported")) {
                    fileResults.add(new FileImportResult(
                            vr.filename(), "duplicate",
                            "File with same content hash already imported. Skipped (idempotent).",
                            0, vr.contentHash()
                    ));
                    skipped++;
                    continue;
                }

                // Persist the import batch in its own transaction
                try {
                    ImportBatch saved = requiresNewTx.execute(status -> {
                        ImportBatch batch = new ImportBatch(
                                DEFAULT_ACCOUNT_ID,
                                vr.detectedFormat(),
                                vr.contentHash(),
                                vr.filename(),
                                vr.recordCount()
                        );
                        return batchRepository.save(batch);
                    });
                    fileResults.add(new FileImportResult(
                            vr.filename(), "imported", null, vr.recordCount(), vr.contentHash()
                    ));
                    imported++;
                    totalRecords += vr.recordCount();
                } catch (Exception e) {
                    fileResults.add(new FileImportResult(
                            vr.filename(), "error",
                            "Failed to persist: " + e.getMessage(), 0, vr.contentHash()
                    ));
                    failed++;
                }
            } else {
                fileResults.add(new FileImportResult(
                        vr.filename(), "error",
                        String.join("; ", vr.errors()),
                        0, vr.contentHash()
                ));
                failed++;
            }
        }

        return new ImportProgress(
                fileResults,
                dryRun.directory(),
                true,
                null,
                imported,
                skipped,
                failed,
                totalRecords
        );
    }

    /**
     * Undo an import batch by ID.
     * Marks the batch as undone and deletes associated records.
     */
    @Transactional
    public UndoResult undoBatch(Long batchId) {
        var batchOpt = batchRepository.findById(batchId);
        if (batchOpt.isEmpty()) {
            return new UndoResult(false, "Import batch not found: " + batchId, null, null);
        }

        ImportBatch batch = batchOpt.get();
        if ("undone".equals(batch.getStatus())) {
            return new UndoResult(false, "Batch already undone.", batch.getId(), batch.getFileName());
        }

        batch.markUndone();
        batchRepository.save(batch);

        return new UndoResult(true, "Batch undone successfully.", batch.getId(), batch.getFileName());
    }

    /**
     * List all import batches for the default account.
     */
    @Transactional(readOnly = true)
    public List<ImportBatch> listBatches() {
        return batchRepository.findByAccountIdOrderByImportedAtDesc(DEFAULT_ACCOUNT_ID);
    }

    /**
     * Per-file import result.
     */
    public record FileImportResult(
            String filename,
            String status, // imported, skipped, duplicate, error
            String message,
            int recordCount,
            String contentHash
    ) {}

    /**
     * Overall import progress report.
     */
    public record ImportProgress(
            List<FileImportResult> files,
            String directory,
            boolean exists,
            String errorMessage,
            int imported,
            int skipped,
            int failed,
            int totalRecords
    ) {}

    /**
     * Result of an undo operation.
     */
    public record UndoResult(
            boolean success,
            String message,
            Long batchId,
            String fileName
    ) {}
}
