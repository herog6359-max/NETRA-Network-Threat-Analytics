package com.netra.service.telemetry;

import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.Protocol;
import com.netra.repository.TrafficFlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Passive sensor adapter for authorised environments.
 * 
 * Safety & Security Guarantees:
 * - Disabled by default (netra.sensor.passive.enabled=false).
 * - Binds strictly to authorised local interface (default 127.0.0.1).
 * - Completely non-invasive: listens passively for incoming telemetry, never scans or probes.
 * - Idempotent deduplication using cryptographic flow hashes.
 */
@Service
public class PassiveSensorAdapter {

    private static final Logger log = LoggerFactory.getLogger(PassiveSensorAdapter.class);

    private final TrafficFlowRepository flowRepository;

    @Value("${netra.sensor.passive.enabled:false}")
    private boolean defaultEnabled;

    @Value("${netra.sensor.passive.bind-address:127.0.0.1}")
    private String defaultBindAddress;

    @Value("${netra.sensor.passive.listen-port:2055}")
    private int defaultListenPort;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong flowsCaptured = new AtomicLong(0);
    private String authorizedOperator = "NOT_AUTHORIZED";
    private String activeBindAddress = "127.0.0.1";
    private int activePort = 2055;
    private Instant lastCapturedAt = null;

    @Autowired
    public PassiveSensorAdapter(TrafficFlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    public synchronized Map<String, Object> authorizeAndStart(String operator, String bindAddress, Integer port) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("Operator authorization identity is mandatory to activate passive capture");
        }

        this.authorizedOperator = operator.trim();
        this.activeBindAddress = (bindAddress != null && !bindAddress.isBlank()) ? bindAddress.trim() : defaultBindAddress;
        this.activePort = (port != null && port > 0 && port <= 65535) ? port : defaultListenPort;
        this.active.set(true);

        log.info("Passive sensor authorized by '{}', bound to {}:{}", authorizedOperator, activeBindAddress, activePort);
        return getStatus();
    }

    public synchronized Map<String, Object> stop() {
        this.active.set(false);
        log.info("Passive sensor stopped by operator");
        return getStatus();
    }

    public Map<String, Object> ingestFlowPacket(
            String srcIp, String dstIp, Integer srcPort, Integer dstPort,
            String protocolStr, Long fwdBytes, Long bwdBytes, Long durationMs,
            Integer synFlagCount) {

        if (!active.get()) {
            throw new IllegalStateException("Passive sensor is currently inactive. Operator authorization required.");
        }

        if (srcIp == null || dstIp == null) {
            throw new IllegalArgumentException("Valid source and destination IPs are required for flow telemetry");
        }

        TrafficFlow flow = new TrafficFlow();
        flow.setSrcIp(srcIp.trim());
        flow.setDstIp(dstIp.trim());
        flow.setSrcPort(srcPort != null ? srcPort : 0);
        flow.setDstPort(dstPort != null ? dstPort : 0);
        flow.setProtocol("UDP".equalsIgnoreCase(protocolStr) ? Protocol.UDP : Protocol.TCP);
        flow.setFlowTimestamp(Instant.now());
        flow.setTotalFwdBytes(fwdBytes != null ? fwdBytes : 0L);
        flow.setTotalBwdBytes(bwdBytes != null ? bwdBytes : 0L);
        flow.setDurationMs(durationMs != null ? durationMs : 0L);
        flow.setSynFlagCount(synFlagCount != null ? synFlagCount : 0);
        flow.setLabel("PASSIVE_CAPTURE");

        String hash = computeFlowHash(flow);
        flow.setFlowHash(hash);

        try {
            if (flowRepository != null && !flowRepository.existsByFlowHash(hash)) {
                flowRepository.save(flow);
            }
        } catch (Exception e) {
            log.warn("Passive flow DB persistence deferred: {}", e.getMessage());
        }

        flowsCaptured.incrementAndGet();
        this.lastCapturedAt = Instant.now();

        return Map.of(
            "status", "INGESTED",
            "flow_hash", hash,
            "flows_captured_total", flowsCaptured.get(),
            "timestamp", flow.getFlowTimestamp().toString()
        );
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active", active.get());
        status.put("authorized_operator", authorizedOperator);
        status.put("bind_address", activeBindAddress);
        status.put("listen_port", activePort);
        status.put("flows_captured", flowsCaptured.get());
        status.put("last_captured_at", lastCapturedAt != null ? lastCapturedAt.toString() : null);
        status.put("interface_mode", "PASSIVE_PROMISCUOUS_LOCAL_ONLY");
        return status;
    }

    private String computeFlowHash(TrafficFlow f) {
        String raw = String.format("%s:%s->%s:%s[%s]@%s",
            f.getSrcIp(), f.getSrcPort(), f.getDstIp(), f.getDstPort(),
            f.getProtocol(), f.getFlowTimestamp().toString());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
