package com.openwearableinsights.api.ingestion.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;

/**
 * JPA entity for import_batches.
 * Records each import batch with source, content hash, status, and undo support.
 */
@Entity
@Table(name = "import_batches")
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String source;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(nullable = false)
    private String status = "imported";

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "undo_at")
    private Instant undoAt;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "record_count")
    private int recordCount;

    protected ImportBatch() {}

    public ImportBatch(Long accountId, String source, String contentHash, String fileName, int recordCount) {
        this.accountId = accountId;
        this.source = source;
        this.contentHash = contentHash;
        this.fileName = fileName;
        this.recordCount = recordCount;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public String getSource() { return source; }
    public String getContentHash() { return contentHash; }
    public String getStatus() { return status; }
    public Instant getImportedAt() { return importedAt; }
    public Instant getUndoAt() { return undoAt; }
    public String getFileName() { return fileName; }
    public int getRecordCount() { return recordCount; }

    public void markUndone() {
        this.status = "undone";
        this.undoAt = Instant.now();
    }
}
