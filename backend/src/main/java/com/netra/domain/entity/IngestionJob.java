package com.netra.domain.entity;

import com.netra.domain.enums.IngestionStatus;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks a data ingestion operation — CSV upload, PCAP parse, or remote sensor pull.
 * Every ingestion has a persisted status, source, timestamps, row counts, and error summary.
 */
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType; // CSV, PCAP, NETFLOW, REMOTE_SENSOR, SYNTHETIC

    @Column(name = "source_filename", length = 500)
    private String sourceFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngestionStatus status = IngestionStatus.PENDING;

    @Column(name = "total_rows")
    private Long totalRows;

    @Column(name = "valid_rows")
    private Long validRows;

    @Column(name = "rejected_rows")
    private Long rejectedRows;

    @Column(name = "duplicate_rows")
    private Long duplicateRows = 0L;

    @Column(name = "error_summary", length = 2000)
    private String errorSummary;

    @Column(name = "dataset_id")
    private Long datasetId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version; // Optimistic locking demonstration

    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }

    // === Accessors ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public IngestionStatus getStatus() { return status; }
    public void setStatus(IngestionStatus status) { this.status = status; }
    public Long getTotalRows() { return totalRows; }
    public void setTotalRows(Long totalRows) { this.totalRows = totalRows; }
    public Long getValidRows() { return validRows; }
    public void setValidRows(Long validRows) { this.validRows = validRows; }
    public Long getRejectedRows() { return rejectedRows; }
    public void setRejectedRows(Long rejectedRows) { this.rejectedRows = rejectedRows; }
    public Long getDuplicateRows() { return duplicateRows != null ? duplicateRows : 0L; }
    public void setDuplicateRows(Long duplicateRows) { this.duplicateRows = duplicateRows; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Long getVersion() { return version; }
}
