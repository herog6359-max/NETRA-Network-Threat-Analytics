package com.netra.controller;

import com.netra.domain.entity.*;
import com.netra.domain.enums.*;
import com.netra.repository.*;
import com.netra.service.telemetry.LiveNetworkService;
import com.netra.service.digitaltwin.DigitalTwinService;
import com.netra.service.forecast.PythonMLForecastEngine;
import com.netra.domain.entity.ForecastRun;
import com.netra.domain.entity.ForecastHorizon;
import com.netra.repository.ForecastRunRepository;
import com.netra.repository.ForecastHorizonRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Live Network Monitor Controller.
 * Provides real-time visibility into actual system network sockets, active local interfaces,
 * genuine port checking, and in-browser interactive traffic probing.
 */
@RestController
@RequestMapping("/api/live")
public class LiveMonitorController {

    private final TrafficFlowRepository flows;
    private final IncidentRepository incidents;
    private final IncidentStatusHistoryRepository history;
    private final LiveNetworkService networkService;
    private final TransactionTemplate transaction;
    private final Deque<Map<String, Object>> events = new ArrayDeque<>();
    private final Map<String, Deque<Instant>> windows = new HashMap<>();
    private final Set<String> alerted = new HashSet<>();
    private boolean active = false;
    private String session = "";
    private long count;
    private Instant expires;

    @Autowired(required = false)
    private PythonMLForecastEngine pythonMlEngine;

    @Autowired(required = false)
    private DigitalTwinService digitalTwinService;

    @Autowired(required = false)
    private ForecastRunRepository forecastRunRepository;

    @Autowired(required = false)
    private ForecastHorizonRepository horizonRepository;

    @Autowired(required = false)
    private com.netra.service.telemetry.TelegramAlertService telegramAlertService;

    public LiveMonitorController(TrafficFlowRepository flows, IncidentRepository incidents,
            IncidentStatusHistoryRepository history, PlatformTransactionManager manager) {
        this(flows, incidents, history, manager, new LiveNetworkService());
    }

    @Autowired
    public LiveMonitorController(TrafficFlowRepository flows, IncidentRepository incidents,
            IncidentStatusHistoryRepository history, PlatformTransactionManager manager,
            LiveNetworkService networkService) {
        this.flows = flows;
        this.incidents = incidents;
        this.history = history;
        this.networkService = networkService != null ? networkService : new LiveNetworkService();
        this.transaction = new TransactionTemplate(manager);
    }

    private void requireLocal(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(ip))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Start and stop from this computer only");
    }

    @PostMapping("/start")
    public synchronized Map<String, Object> start(HttpServletRequest request) {
        requireLocal(request);
        flows.count();
        session = UUID.randomUUID().toString();
        count = 0;
        events.clear();
        windows.clear();
        alerted.clear();
        active = true;
        expires = Instant.now().plusSeconds(86400);
        return status();
    }

    @PostMapping("/stop")
    public synchronized Map<String, Object> stop(HttpServletRequest request) {
        requireLocal(request);
        active = false;
        return status();
    }

    @PostMapping("/clear")
    public synchronized Map<String, Object> clearEvents() {
        events.clear();
        alerted.clear();
        windows.clear();
        count = 0;
        return status();
    }

    @GetMapping("/status")
    public synchronized Map<String, Object> status() {
        if (expires != null && Instant.now().isAfter(expires)) active = false;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("active", active);
        res.put("session", session);
        res.put("requestCount", count);
        res.put("events", List.copyOf(events));
        res.put("alertCount", alerted.size());
        res.put("scope", "Real-time Live Local Traffic Monitor & System Socket Sensor");
        res.put("rule", "10 requests from one source IP within 10 seconds triggers an incident alert");
        res.put("host_profile", networkService.getLocalHostProfile());

        // Attach Real-Time Digital Twin Topology
        if (digitalTwinService != null) {
            try {
                res.put("digital_twin", digitalTwinService.getCurrentTopology(null, null));
            } catch (Exception ignored) {}
        }

        // Attach Real-Time Latest PyTorch ML Forecast
        if (forecastRunRepository != null) {
            try {
                List<ForecastRun> runs = forecastRunRepository.findAll(
                    PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).getContent();
                if (!runs.isEmpty()) {
                    ForecastRun latestRun = runs.get(0);
                    Map<String, Object> fcMap = new LinkedHashMap<>();
                    fcMap.put("id", latestRun.getId());
                    fcMap.put("model", latestRun.getModelName());
                    fcMap.put("modelVersion", latestRun.getModelVersion());
                    fcMap.put("predictedStage", latestRun.getPredictedStage());
                    double maxR = latestRun.getMaxForecastedRisk() != null ? latestRun.getMaxForecastedRisk() : 0.0;
                    double curR = latestRun.getCurrentRiskScore() != null ? latestRun.getCurrentRiskScore() : 0.0;
                    fcMap.put("riskDelta", maxR - curR);
                    fcMap.put("createdAt", latestRun.getCreatedAt() != null ? latestRun.getCreatedAt().toString() : null);

                    if (horizonRepository != null) {
                        List<ForecastHorizon> horizons = horizonRepository.findByForecastRunIdOrderByStepNumberAsc(latestRun.getId());
                        List<Map<String, Object>> horList = new ArrayList<>();
                        for (ForecastHorizon h : horizons) {
                            double r = h.getPredictedRiskScore() != null ? h.getPredictedRiskScore() : 0.0;
                            String stage = r > 0.7 ? "Exploitation" : (r > 0.4 ? "Initial Access" : "Reconnaissance");
                            double conf = h.getConfidenceUpper() != null ? h.getConfidenceUpper() : 0.85;
                            horList.add(Map.of(
                                "step", h.getStepNumber(),
                                "offset", "+" + (h.getStepNumber() * 15) + "m",
                                "projectedRisk", r,
                                "predictedStage", stage,
                                "confidence", conf
                            ));
                        }
                        fcMap.put("horizons", horList);
                    }
                    res.put("latest_forecast", fcMap);
                }
            } catch (Exception ignored) {}
        }

        return res;
    }

    /**
     * Inspects actual system TCP connections currently open on this computer.
     */
    @GetMapping("/connections")
    public Map<String, Object> getLiveConnections(@RequestParam(defaultValue = "40") int limit) {
        int max = Math.min(Math.max(limit, 5), 150);
        List<Map<String, Object>> conns = networkService.getActiveSystemConnections(max);
        Map<String, Object> hostProfile = networkService.getLocalHostProfile();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("host_profile", hostProfile);
        response.put("total_connections", conns.size());
        response.put("connections", conns);
        return response;
    }

    /**
     * Real TCP socket port check against target host with millisecond response latency.
     */
    @RequestMapping(value = "/check-ports", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> checkPorts(
            @RequestParam(required = false, defaultValue = "127.0.0.1") String host,
            @RequestParam(required = false) List<Integer> ports,
            @RequestParam(required = false, defaultValue = "500") int timeout) {
        return networkService.checkPorts(host, ports, timeout);
    }

    /**
     * Interactive in-browser probe execution.
     */
    @PostMapping("/send-probe")
    public synchronized Map<String, Object> sendProbe(HttpServletRequest request) {
        if (!active) {
            active = true;
            if (session.isBlank()) session = UUID.randomUUID().toString();
            expires = Instant.now().plusSeconds(86400);
        }
        return probe(request);
    }

    /**
     * In-browser traffic burst simulation (generates 12 flows to trigger real alert).
     */
    @PostMapping("/generate-burst")
    public synchronized Map<String, Object> generateBurst(HttpServletRequest request) {
        if (!active) {
            active = true;
            if (session.isBlank()) session = UUID.randomUUID().toString();
            expires = Instant.now().plusSeconds(86400);
        }
        List<Map<String, Object>> burstEvents = new ArrayList<>();
        long lastIncidentId = 0;
        for (int i = 0; i < 12; i++) {
            Map<String, Object> ev = probe(request);
            burstEvents.add(ev);
            Object incId = ev.get("incidentId");
            if (incId instanceof Number && ((Number) incId).longValue() > 0) {
                lastIncidentId = ((Number) incId).longValue();
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", "SUCCESS");
        res.put("requests_generated", 12);
        res.put("triggered_incident_id", lastIncidentId);
        res.put("total_session_requests", count);
        res.put("latest_events", burstEvents);
        return res;
    }

    @GetMapping("/probe")
    public synchronized Map<String, Object> probe(HttpServletRequest request) {
        status();
        if (!active) throw new ResponseStatusException(HttpStatus.CONFLICT, "Start a live session first");
        Instant now = Instant.now();
        String source = request.getRemoteAddr();
        if (!windows.containsKey(source) && windows.size() >= 256)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Session source limit reached");
        Deque<Instant> window = windows.computeIfAbsent(source, key -> new ArrayDeque<>());
        window.removeIf(t -> t.isBefore(now.minusSeconds(10)));
        int recent = window.size() + 1;
        boolean trigger = recent >= 10 && !alerted.contains(source);
        Map<String, Object> event = transaction.execute(tx -> {
            TrafficFlow flow = new TrafficFlow();
            flow.setSrcIp(source);
            flow.setDstIp(request.getLocalAddr());
            flow.setSrcPort(request.getRemotePort());
            flow.setDstPort(request.getLocalPort());
            flow.setProtocol(Protocol.TCP);
            flow.setFlowTimestamp(now);
            flow.setLabel("LIVE_HTTP_PROBE");
            flow.setFlowHash(UUID.randomUUID().toString());
            TrafficFlow saved = flows.save(flow);
            long incidentId = 0;
            if (trigger) {
                Incident incident = new Incident();
                incident.setTitle("Live traffic burst detected from " + source);
                incident.setDescription(recent + " observed HTTP probe flows in 10 seconds from " + source
                        + " to " + request.getLocalAddr() + ":" + request.getLocalPort()
                        + ". Trigger rule: >= 10 requests / 10s. Session: " + session);
                incident.setStatus(IncidentStatus.NEW);
                incident.setSeverity(Severity.MEDIUM);
                incident.setAttackStage("Observed request burst");
                incident.setDemo(true);
                incident.setCreatedBy("NETRA-Live-Monitor");
                Incident savedIncident = incidents.save(incident);
                incidentId = savedIncident.getId();
                history.save(new IncidentStatusHistory(savedIncident, "NONE", "NEW",
                        "NETRA-Live-Monitor", incident.getDescription()));
            }
            return Map.<String, Object>of(
                    "flowId", saved.getId(),
                    "timestamp", now.toString(),
                    "sourceIp", source,
                    "sourcePort", request.getRemotePort(),
                    "destinationIp", request.getLocalAddr(),
                    "destinationPort", request.getLocalPort(),
                    "requestsInWindow", recent,
                    "incidentId", incidentId);
        });
        window.addLast(now);
        while (window.size() > 1000) window.removeFirst();
        if (trigger) alerted.add(source);
        events.addFirst(Objects.requireNonNull(event));
        while (events.size() > 100) events.removeLast();
        count++;
        return event;
    }

    @PostMapping("/threat-event")
    public synchronized Map<String, Object> recordThreatEvent(@RequestBody Map<String, Object> payload) {
        String sourceIp = (String) payload.getOrDefault("sourceIp", "Unknown");
        String destinationIp = (String) payload.getOrDefault("destinationIp", "127.0.0.1");
        int sourcePort = payload.get("sourcePort") instanceof Number ? ((Number) payload.get("sourcePort")).intValue() : 0;
        int destinationPort = payload.get("destinationPort") instanceof Number ? ((Number) payload.get("destinationPort")).intValue() : 0;
        String protoStr = (String) payload.getOrDefault("protocol", "TCP");
        String threatType = (String) payload.getOrDefault("threatType", "SUSPICIOUS_PROBE");
        String threatSummary = (String) payload.getOrDefault("threatSummary", "Network anomaly detected");
        String severityStr = (String) payload.getOrDefault("severity", "HIGH");
        String mitreTactic = (String) payload.getOrDefault("mitreTactic", "TA0043 Reconnaissance");
        List<?> portsScanned = payload.get("portsScanned") instanceof List ? (List<?>) payload.get("portsScanned") : List.of();
        int packetCount = payload.get("packetCount") instanceof Number ? ((Number) payload.get("packetCount")).intValue() : 1;
        long durationMs = payload.get("durationMs") instanceof Number ? ((Number) payload.get("durationMs")).longValue() : 50L;
        Instant now = Instant.now();

        Severity parsedSeverity;
        try {
            parsedSeverity = Severity.valueOf(severityStr.toUpperCase());
        } catch (Exception e) {
            parsedSeverity = Severity.HIGH;
        }
        final Severity severity = parsedSeverity;

        return transaction.execute(tx -> {
            TrafficFlow flow = new TrafficFlow();
            flow.setSrcIp(sourceIp);
            flow.setDstIp(destinationIp);
            flow.setSrcPort(sourcePort);
            flow.setDstPort(destinationPort);
            flow.setProtocol("ICMP".equalsIgnoreCase(protoStr) ? Protocol.ICMP : ("UDP".equalsIgnoreCase(protoStr) ? Protocol.UDP : Protocol.TCP));
            flow.setFlowTimestamp(now);
            flow.setTotalFwdPackets((long) packetCount);
            flow.setTotalFwdBytes((long) packetCount * 64L);
            flow.setDurationMs(durationMs);
            flow.setSynFlagCount("TCP".equalsIgnoreCase(protoStr) ? packetCount : 0);
            flow.setAckFlagCount(0);
            flow.setLabel("KALI_" + threatType);
            flow.setFlowHash(UUID.randomUUID().toString());
            TrafficFlow savedFlow = flows.save(flow);

            Incident incident = new Incident();
            incident.setTitle("[" + threatType + "] " + threatSummary);
            incident.setDescription(String.format("Live Kali attack detected from %s to %s:%d. Protocol: %s. Packet count: %d. Scanned ports: %s. MITRE: %s.",
                    sourceIp, destinationIp, destinationPort, protoStr, packetCount, portsScanned.toString(), mitreTactic));
            incident.setStatus(IncidentStatus.NEW);
            incident.setSeverity(severity);
            incident.setAttackStage("PORT_SCAN".equals(threatType) || "ICMP_PING".equals(threatType) ? "Reconnaissance" : "Active Infiltration");
            incident.setMitreTactic(mitreTactic);
            incident.setMitreTechnique("PORT_SCAN".equals(threatType) ? "T1595.001" : ("ICMP_PING".equals(threatType) ? "T1595" : "T1498"));
            incident.setCreatedBy("NETRA-Live-Sensor");
            incident.setDemo(false);
            Incident savedIncident = incidents.save(incident);
            history.save(new IncidentStatusHistory(savedIncident, "NONE", "NEW", "NETRA-Live-Sensor", incident.getDescription()));

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("flowId", savedFlow.getId());
            event.put("timestamp", now.toString());
            event.put("sourceIp", sourceIp);
            event.put("sourcePort", sourcePort);
            event.put("destinationIp", destinationIp);
            event.put("destinationPort", destinationPort);
            event.put("protocol", protoStr);
            event.put("threatType", threatType);
            event.put("threatSummary", threatSummary);
            event.put("severity", severity.name());
            event.put("portsScanned", portsScanned);
            event.put("packetCount", packetCount);
            event.put("incidentId", savedIncident.getId());
            event.put("isKaliAttack", true);

            events.addFirst(event);
            while (events.size() > 100) events.removeLast();
            count++;
            alerted.add(sourceIp);

            // Autonomous Telegram Threat Alert Dispatch
            if (telegramAlertService != null) {
                telegramAlertService.sendThreatAlert(
                    threatType,
                    threatSummary,
                    severity.name(),
                    sourceIp,
                    destinationIp,
                    portsScanned,
                    packetCount,
                    mitreTactic
                );
            }

            // Autonomous Real-Time PyTorch TCN Forecast Trigger (Background)
            if (pythonMlEngine != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Instant end = Instant.now();
                        Instant start = end.minus(24, ChronoUnit.HOURS);
                        pythonMlEngine.forecast(start, end, 6, Map.of(
                            "model", "pytorch-tcn",
                            "trigger", "AUTONOMOUS_KALI_ATTACK",
                            "attacker_ip", sourceIp,
                            "threat_type", threatType
                        ));
                    } catch (Exception ignored) {}
                });
            }

            return Map.of("status", "RECORDED", "incidentId", savedIncident.getId(), "flowId", savedFlow.getId(), "event", event);
        });
    }

    private static Process snifferProcess = null;
    private static String snifferTargetIp = "ALL";
    private static Instant snifferStartedAt = null;

    @PostMapping("/sniffer/start")
    public synchronized Map<String, Object> startSniffer(@RequestParam(required = false, defaultValue = "ALL") String targetIp) {
        if (snifferProcess != null && snifferProcess.isAlive()) {
            return getSnifferStatus();
        }

        try {
            String projectRoot = System.getProperty("user.dir");
            java.io.File rootDir = new java.io.File(projectRoot);
            if (rootDir.getName().equalsIgnoreCase("backend")) {
                rootDir = rootDir.getParentFile();
            }

            java.io.File pythonExe = new java.io.File(rootDir, "ml-service/.venv/Scripts/python.exe");
            java.io.File snifferScript = new java.io.File(rootDir, "sensor-agent/netra_sniffer.py");

            if (!pythonExe.exists() || !snifferScript.exists()) {
                throw new IllegalStateException("Sniffer script or python executable not found: " + pythonExe.getAbsolutePath());
            }

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe.getAbsolutePath(),
                    snifferScript.getAbsolutePath(),
                    "--backend", "http://127.0.0.1:8080",
                    "--target-ip", targetIp
            );
            pb.directory(rootDir);
            java.io.File logFile = new java.io.File(rootDir, "sniffer.log");
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

            snifferProcess = pb.start();
            snifferTargetIp = targetIp;
            snifferStartedAt = Instant.now();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to launch sniffer: " + e.getMessage());
        }

        return getSnifferStatus();
    }

    @PostMapping("/sniffer/stop")
    public synchronized Map<String, Object> stopSniffer() {
        if (snifferProcess != null && snifferProcess.isAlive()) {
            snifferProcess.destroy();
            try {
                snifferProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            if (snifferProcess.isAlive()) {
                snifferProcess.destroyForcibly();
            }
        }
        snifferProcess = null;
        snifferStartedAt = null;
        return getSnifferStatus();
    }

    @GetMapping("/sniffer/status")
    public synchronized Map<String, Object> getSnifferStatus() {
        boolean running = snifferProcess != null && snifferProcess.isAlive();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running);
        status.put("target_ip", snifferTargetIp);
        status.put("pid", running ? snifferProcess.pid() : null);
        status.put("started_at", snifferStartedAt != null ? snifferStartedAt.toString() : null);
        status.put("driver", "Npcap / Scapy Promiscuous Sniffer");
        return status;
    }
}
