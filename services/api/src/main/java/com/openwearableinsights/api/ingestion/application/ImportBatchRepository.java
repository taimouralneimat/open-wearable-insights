package com.openwearableinsights.api.ingestion.application;

import com.openwearableinsights.api.ingestion.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for import_batches.
 */
@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    /**
     * Find all batches for an account, ordered by import time descending.
     */
    List<ImportBatch> findByAccountIdOrderByImportedAtDesc(Long accountId);

    /**
     * Check if a content hash already exists with status 'imported' (duplicate detection).
     * Undone batches do NOT block re-import — undo restores re-importability.
     */
    boolean existsByContentHashAndStatus(String contentHash, String status);

    /**
     * Find a batch by content hash (for duplicate detection).
     */
    Optional<ImportBatch> findByContentHash(String contentHash);
}
