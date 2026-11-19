package com.netra.domain.entity;

import com.netra.domain.enums.IncidentStatus;
import com.netra.domain.enums.Severity;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * An investigation incident — links forecasts, alerts, hosts, and evidence.
 * Supports full lifecycle: NEW → ACKNOWLEDGED → INVESTIGATING → RESOLVED → CLOSED.
 */
@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incidents_status", columnList = "status"),
    @Index(name = "idx_incidents_severity", columnList = "severity"),
    @Index(name = "idx_incidents_created", columnList = "created_at")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.NEW;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "forecast_run_id")
    private Long forecastRunId;

    @Column(name = "primary_host_id")
    private Long primaryHostId;

    @Column(name = "attack_stage", length = 100)
    private String attackStage;

    @Column(name = "mitre_tactic", length = 200)
    private String mitreTactic;

    @Column(name = "mitre_technique", length = 200)
    private String mitreTechnique;

    @Column(name = "resolution_notes", length = 5000)
    private String resolutionNotes;

    @Column(name = "is_demo", nullable = false)
    private boolean demo = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = Instant.now(); }

    /**
     * Transition to a new status with validation.
     * Demonstrates OOP encapsulation — status transition logic lives in the entity.
     */
    public void transitionTo(IncidentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition incident from " + this.status + " to " + newStatus
            );
        }
        this.status = newStatus;
        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) {
            this.resolvedAt = Instant.now();
        }
    }

    // === Accessors ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Long getForecastRunId() { return forecastRunId; }
    public void setForecastRunId(Long forecastRunId) { this.forecastRunId = forecastRunId; }
    public Long getPrimaryHostId() { return primaryHostId; }
    public void setPrimaryHostId(Long primaryHostId) { this.primaryHostId = primaryHostId; }
    public String getAttackStage() { return attackStage; }
    public void setAttackStage(String attackStage) { this.attackStage = attackStage; }
    public String getMitreTactic() { return mitreTactic; }
    public void setMitreTactic(String mitreTactic) { this.mitreTactic = mitreTactic; }
    public String getMitreTechnique() { return mitreTechnique; }
    public void setMitreTechnique(String mitreTechnique) { this.mitreTechnique = mitreTechnique; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
