package com.netra.controller;

import com.netra.service.telemetry.PassiveSensorAdapter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/telemetry/sensor")
public class PassiveSensorController {

    private final PassiveSensorAdapter sensorAdapter;

    public PassiveSensorController(PassiveSensorAdapter sensorAdapter) {
        this.sensorAdapter = sensorAdapter;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(sensorAdapter.getStatus());
    }

    @PostMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorizeSensor(
            @RequestParam String operator,
            @RequestParam(required = false, defaultValue = "127.0.0.1") String bindAddress,
            @RequestParam(required = false, defaultValue = "2055") Integer port) {
        return ResponseEntity.ok(sensorAdapter.authorizeAndStart(operator, bindAddress, port));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopSensor() {
        return ResponseEntity.ok(sensorAdapter.stop());
    }

    @PostMapping("/ingest-packet")
    public ResponseEntity<Map<String, Object>> ingestPacket(@RequestBody Map<String, Object> payload) {
        String srcIp = (String) payload.getOrDefault("src_ip", "127.0.0.1");
        String dstIp = (String) payload.getOrDefault("dst_ip", "127.0.0.1");
        Integer srcPort = payload.get("src_port") instanceof Number ? ((Number) payload.get("src_port")).intValue() : 0;
        Integer dstPort = payload.get("dst_port") instanceof Number ? ((Number) payload.get("dst_port")).intValue() : 80;
        String proto = (String) payload.getOrDefault("protocol", "TCP");
        Long fwdBytes = payload.get("fwd_bytes") instanceof Number ? ((Number) payload.get("fwd_bytes")).longValue() : 100L;
        Long bwdBytes = payload.get("bwd_bytes") instanceof Number ? ((Number) payload.get("bwd_bytes")).longValue() : 50L;
        Long durationMs = payload.get("duration_ms") instanceof Number ? ((Number) payload.get("duration_ms")).longValue() : 10L;
        Integer syn = payload.get("syn_flag_count") instanceof Number ? ((Number) payload.get("syn_flag_count")).intValue() : 0;

        return ResponseEntity.ok(sensorAdapter.ingestFlowPacket(
            srcIp, dstIp, srcPort, dstPort, proto, fwdBytes, bwdBytes, durationMs, syn
        ));
    }
}
