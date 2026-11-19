package com.netra.controller.dto;

import java.time.Instant;

public class TelemetryReplayStatusDto {
    private Long sessionId;
    private String status; // IDLE, PLAYING, PAUSED, COMPLETED, STOPPED
    private Integer playbackSpeed;
    private Long totalFlows;
    private Long emittedFlows;
    private Instant currentCursorTimestamp;
    private Instant windowStart;
    private Instant windowEnd;
    private String sourceFilename;
    private Double progressPercentage;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
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
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public Double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Double progressPercentage) { this.progressPercentage = progressPercentage; }
}
