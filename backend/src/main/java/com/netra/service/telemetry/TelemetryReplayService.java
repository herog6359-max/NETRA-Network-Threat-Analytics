package com.netra.service.telemetry;

import com.netra.controller.dto.TelemetryReplayStatusDto;
import com.netra.domain.entity.TelemetryReplaySession;
import com.netra.domain.entity.TrafficFlow;
import com.netra.repository.TelemetryReplaySessionRepository;
import com.netra.repository.TrafficFlowRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import java.util.*;

/** Replays persisted events in event-time order. Never inserts copies or synthetic fallback events. */
@Service
public class TelemetryReplayService {
    private final TrafficFlowRepository flows;
    private final TelemetryReplaySessionRepository sessions;
    private List<TrafficFlow> source = List.of();
    private TelemetryReplaySession session;
    private int cursor;
    private long wallAnchor;
    private Instant eventAnchor;

    public TelemetryReplayService(TrafficFlowRepository flows, TelemetryReplaySessionRepository sessions) {
        this.flows = flows; this.sessions = sessions;
    }
    public synchronized TelemetryReplayStatusDto startReplay(Long jobId, Integer speed) {
        List<TrafficFlow> selected = flows.findAllByOrderByFlowTimestampAsc().stream()
            .filter(f -> jobId == null || jobId.equals(f.getIngestionJobId()))
            .sorted(Comparator.comparing(TrafficFlow::getFlowTimestamp).thenComparing(TrafficFlow::getId))
            .toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("No persisted flows for the selected ingestion job");
        if (selected.size() > 50000) throw new IllegalArgumentException("Select an ingestion job with at most 50000 rows");
        TelemetryReplaySession created = new TelemetryReplaySession();
        created.setJobId(jobId); created.setSourceFilename(jobId == null ? "All stored flows (mixed sources)" : "Ingestion job #" + jobId);
        created.setPlaybackSpeed(clamp(speed)); created.setStatus("PLAYING");
        created.setTotalFlows((long) selected.size()); created.setEmittedFlows(0L);
        created.setWindowStart(selected.getFirst().getFlowTimestamp()); created.setWindowEnd(selected.getLast().getFlowTimestamp());
        created.setCurrentCursorTimestamp(selected.getFirst().getFlowTimestamp()); created.setStartedAt(Instant.now());
        session = sessions.save(created); source = selected; cursor = 0; anchor();
        return getStatus();
    }
    private int clamp(Integer speed) { return Math.max(1, Math.min(speed == null ? 1 : speed, 50)); }
    private void requireSession() { if (session == null) throw new IllegalArgumentException("Start a replay first"); }
    private void anchor() { wallAnchor = System.nanoTime(); eventAnchor = cursor > 0 ? source.get(cursor-1).getFlowTimestamp() : source.getFirst().getFlowTimestamp(); }
    private void persist() {
        session.setEmittedFlows((long) cursor);
        session.setCurrentCursorTimestamp(cursor > 0 ? source.get(cursor-1).getFlowTimestamp() : source.getFirst().getFlowTimestamp());
        if (cursor == source.size()) { session.setStatus("COMPLETED"); session.setCompletedAt(Instant.now()); }
        session = sessions.save(session);
    }
    @Scheduled(fixedDelay = 500)
    public synchronized void tick() {
        if (session == null || !"PLAYING".equals(session.getStatus())) return;
        Instant target = eventAnchor.plusNanos((System.nanoTime() - wallAnchor) * session.getPlaybackSpeed());
        int previous = cursor;
        while (cursor < source.size() && !source.get(cursor).getFlowTimestamp().isAfter(target)) cursor++;
        if (cursor != previous) {
            try { persist(); }
            catch (RuntimeException e) { cursor = previous; session.setStatus("ERROR"); }
        }
    }
    public synchronized TelemetryReplayStatusDto pauseReplay() { requireSession(); session.setStatus("PAUSED"); persist(); return getStatus(); }
    public synchronized TelemetryReplayStatusDto resumeReplay(Integer speed) {
        requireSession();
        if (cursor >= source.size()) throw new IllegalArgumentException("Replay complete; seek backward or start again");
        session.setPlaybackSpeed(clamp(speed)); session.setStatus("PLAYING"); anchor(); persist(); return getStatus();
    }
    public synchronized TelemetryReplayStatusDto stopReplay() {
        requireSession(); session.setStatus("STOPPED"); sessions.save(session); return getStatus();
    }
    public synchronized TelemetryReplayStatusDto seek(int count) {
        requireSession(); cursor = Math.max(0, Math.min(count, source.size()));
        session.setStatus("PAUSED"); anchor(); persist(); return getStatus();
    }
    public synchronized TelemetryReplayStatusDto stepForward() { return seek(cursor + 1); }
    public synchronized List<TrafficFlow> getActiveReplayedFlows() { return List.copyOf(source.subList(0, cursor)); }
    public synchronized TelemetryReplayStatusDto getStatus() {
        TelemetryReplayStatusDto dto = new TelemetryReplayStatusDto();
        dto.setStatus(session == null ? "IDLE" : session.getStatus());
        dto.setPlaybackSpeed(session == null ? 1 : session.getPlaybackSpeed());
        dto.setTotalFlows((long) source.size()); dto.setEmittedFlows((long) cursor);
        dto.setProgressPercentage(source.isEmpty() ? 0.0 : cursor * 100.0 / source.size());
        if (session != null) {
            dto.setSessionId(session.getId()); dto.setSourceFilename(session.getSourceFilename());
            dto.setWindowStart(session.getWindowStart()); dto.setWindowEnd(session.getWindowEnd());
            dto.setCurrentCursorTimestamp(session.getCurrentCursorTimestamp());
        }
        return dto;
    }
}
