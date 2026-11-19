package com.netra.controller;

import com.netra.controller.dto.DigitalTwinStateDto;
import com.netra.controller.dto.WhatIfScenarioRequest;
import com.netra.controller.dto.WhatIfScenarioResponse;
import com.netra.service.digitaltwin.DigitalTwinService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Controller for NETRA's Flagship Feature: Temporal Network Digital Twin
 * - Real topology inspection from stored traffic flows
 * - Temporal timeline replay through historical windows
 * - Counterfactual What-If scenario simulations (host isolation, traffic throttling)
 */
@RestController
@RequestMapping("/api/twin")
public class DigitalTwinController {

    private final DigitalTwinService digitalTwinService;

    public DigitalTwinController(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;
    }

    @GetMapping("/topology")
    public DigitalTwinStateDto getTopology(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return digitalTwinService.getCurrentTopology(start, end);
    }

    @GetMapping("/replay")
    public List<Map<String, Object>> getReplayTimeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "12") int steps) {
        return digitalTwinService.getReplayTimeline(start, end, steps);
    }

    @PostMapping("/what-if")
    public ResponseEntity<WhatIfScenarioResponse> simulateWhatIf(@RequestBody WhatIfScenarioRequest request) {
        WhatIfScenarioResponse response = digitalTwinService.simulateWhatIfScenario(request);
        return ResponseEntity.ok(response);
    }
}
