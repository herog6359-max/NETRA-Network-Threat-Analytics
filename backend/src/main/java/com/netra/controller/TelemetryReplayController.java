package com.netra.controller;

import com.netra.controller.dto.TelemetryReplayStatusDto;
import com.netra.service.telemetry.TelemetryReplayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/telemetry/replay")
public class TelemetryReplayController {

    private final TelemetryReplayService replayService;
    private final com.netra.service.digitaltwin.DigitalTwinService twin;

    public TelemetryReplayController(TelemetryReplayService replayService, com.netra.service.digitaltwin.DigitalTwinService twin) {
        this.replayService = replayService;
        this.twin = twin;
    }

    @PostMapping("/seek")
    public TelemetryReplayStatusDto seek(@RequestParam int count) { return replayService.seek(count); }

    @GetMapping("/topology")
    public com.netra.controller.dto.DigitalTwinStateDto topology() {
        var state = replayService.getStatus();
        return twin.buildTopology(replayService.getActiveReplayedFlows(), state.getWindowStart(), state.getCurrentCursorTimestamp());
    }

    @PostMapping("/start")
    public ResponseEntity<TelemetryReplayStatusDto> startReplay(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false, defaultValue = "1") Integer speed) {
        return ResponseEntity.ok(replayService.startReplay(jobId, speed));
    }

    @PostMapping("/pause")
    public ResponseEntity<TelemetryReplayStatusDto> pauseReplay() {
        return ResponseEntity.ok(replayService.pauseReplay());
    }

    @PostMapping("/resume")
    public ResponseEntity<TelemetryReplayStatusDto> resumeReplay(
            @RequestParam(required = false) Integer speed) {
        return ResponseEntity.ok(replayService.resumeReplay(speed));
    }

    @PostMapping("/stop")
    public ResponseEntity<TelemetryReplayStatusDto> stopReplay() {
        return ResponseEntity.ok(replayService.stopReplay());
    }

    @PostMapping("/step")
    public ResponseEntity<TelemetryReplayStatusDto> stepReplay() {
        return ResponseEntity.ok(replayService.stepForward());
    }

    @GetMapping("/status")
    public ResponseEntity<TelemetryReplayStatusDto> getStatus() {
        return ResponseEntity.ok(replayService.getStatus());
    }
}
