package com.netra.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks individual rejected/malformed rows from telemetry ingestion.
 */
@Entity
@Table(name = "ingestion_errors")
public class IngestionError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "row_number")
    private Long rowNumber;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "error_reason", length = 1000)
    private String errorReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Long getRowNumber() { return rowNumber; }
    public void setRowNumber(Long rowNumber) { this.rowNumber = rowNumber; }
    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
    public Instant getCreatedAt() { return createdAt; }
}
