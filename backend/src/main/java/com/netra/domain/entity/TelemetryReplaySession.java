package com.netra.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks a deterministic chronological telemetry replay session over stored flows.
 */
@Entity
@Table(name = "telemetry_replay_sessions")
public class TelemetryReplaySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "source_filename", length = 500)
    private String sourceFilename;

    @Column(nullable = false, length = 50)
    private String status = "IDLE"; // IDLE, PLAYING, PAUSED, COMPLETED, STOPPED

    @Column(name = "playback_speed", nullable = false)
    private Integer playbackSpeed = 1;

    @Column(name = "total_flows", nullable = false)
    private Long totalFlows = 0L;

    @Column(name = "emitted_flows", nullable = false)
    private Long emittedFlows = 0L;

    @Column(name = "current_cursor_timestamp")
    private Instant currentCursorTimestamp;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPlaybackSpeed() { return playbackSpeed; }
    public void setPlaybackSpeed(Integer playbackSpeed) { this.playbackSpeed = playbackSpeed; }
    public Long getTotalFlows() { return totalFlows; }
    public void setTotalFlows(Long totalFlows) { this.totalFlows = totalFlows; }
    public Long getEmittedFlows() { return emittedFlows; }
    public void setEmittedFlows(Long emittedFlows) { this.emittedFlows = emittedFlows; }
    public Instant getCurrentCursorTimestamp() { return currentCursorTimestamp; }
    public void setCurrentCursorTimestamp(Instant currentCursorTimestamp) { this.currentCursorTimestamp = currentCursorTimestamp; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
