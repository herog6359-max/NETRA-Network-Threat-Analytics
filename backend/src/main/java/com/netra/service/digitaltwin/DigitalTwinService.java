package com.netra.service.digitaltwin;

import com.netra.controller.dto.*;
import com.netra.domain.entity.Host;
import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.Severity;
import com.netra.repository.HostRepository;
import com.netra.repository.TrafficFlowRepository;
import com.netra.service.TrafficFeatureExtractor;
import com.netra.domain.entity.WhatIfScenario;
import com.netra.repository.WhatIfScenarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for the Temporal Network Digital Twin:
 * - Real topology extraction from stored traffic flows
 * - Temporal slice replay & state reconstruction
 * - Counterfactual What-If scenario simulation & defensive risk comparison
 */
@Service
public class DigitalTwinService {

    private static final Logger log = LoggerFactory.getLogger(DigitalTwinService.class);

    private final TrafficFlowRepository flowRepository;
    private final HostRepository hostRepository;
    private final TrafficFeatureExtractor featureExtractor;
    private final WhatIfScenarioRepository whatIfScenarioRepository;

    public DigitalTwinService(
            TrafficFlowRepository flowRepository,
            HostRepository hostRepository,
            TrafficFeatureExtractor featureExtractor) {
        this(flowRepository, hostRepository, featureExtractor, null);
    }

    @Autowired
    public DigitalTwinService(
            TrafficFlowRepository flowRepository,
            HostRepository hostRepository,
            TrafficFeatureExtractor featureExtractor,
            WhatIfScenarioRepository whatIfScenarioRepository) {
        this.flowRepository = flowRepository;
        this.hostRepository = hostRepository;
        this.featureExtractor = featureExtractor;
        this.whatIfScenarioRepository = whatIfScenarioRepository;
    }

    public DigitalTwinStateDto getCurrentTopology(Instant start, Instant end) {
        if (end == null) end = Instant.now();
        if (start == null) start = end.minus(24, ChronoUnit.HOURS);

        List<TrafficFlow> flows = flowRepository.findByTimeWindow(start, end);
        if (flows.isEmpty() && flowRepository.count() > 0) {
            flows = flowRepository.findAll(PageRequest.of(
                0, 500, Sort.by(Sort.Direction.DESC, "flowTimestamp")
            )).getContent();
        }
        return buildTopology(flows, start, end);
    }

    public DigitalTwinStateDto buildTopology(List<TrafficFlow> flows, Instant start, Instant end) {
        DigitalTwinStateDto state = new DigitalTwinStateDto();
        state.setWindowStart(start);
        state.setWindowEnd(end);

        Map<String, TopologyNodeDto> nodeMap = new LinkedHashMap<>();
        Map<String, TopologyEdgeDto> edgeMap = new LinkedHashMap<>();

        long totalBytes = 0;
        long totalSyn = 0;
        int anomalyFlows = 0;

        for (TrafficFlow flow : flows) {
            String src = flow.getSrcIp();
            String dst = flow.getDstIp();
            if (src == null || dst == null) continue;

            totalBytes += (flow.getTotalFwdBytes() != null ? flow.getTotalFwdBytes() : 0) +
                          (flow.getTotalBwdBytes() != null ? flow.getTotalBwdBytes() : 0);
            if (flow.getSynFlagCount() != null && flow.getSynFlagCount() > 0) totalSyn++;
            boolean isKali = flow.getLabel() != null && flow.getLabel().startsWith("KALI_");
            boolean isAnomaly = isKali || (flow.getSynFlagCount() != null && flow.getSynFlagCount() > 10
                && (flow.getAckFlagCount() == null || flow.getAckFlagCount() < flow.getSynFlagCount() / 2));
            if (isAnomaly) anomalyFlows++;

            // Ensure source node
            nodeMap.computeIfAbsent(src, ip -> createNode(ip));
            TopologyNodeDto srcNode = nodeMap.get(src);
            srcNode.setFlowCount(srcNode.getFlowCount() + 1);
            if (isKali) {
                srcNode.setRole("EXTERNAL");
                srcNode.setLabel("Kali Linux (" + src + ")");
                srcNode.setRiskScore(0.95);
                srcNode.setRiskLevel("CRITICAL");
            }

            // Ensure destination node
            nodeMap.computeIfAbsent(dst, ip -> createNode(ip));
            TopologyNodeDto dstNode = nodeMap.get(dst);
            dstNode.setFlowCount(dstNode.getFlowCount() + 1);
            if (isKali) {
                dstNode.setRole("SERVER");
                dstNode.setLabel("Target Host (" + dst + ")");
                dstNode.setRiskScore(Math.max(dstNode.getRiskScore(), 0.85));
                dstNode.setRiskLevel("CRITICAL");
            } else if (isAnomaly) {
                dstNode.setRiskScore(Math.min(1.0, dstNode.getRiskScore() + 0.1));
                dstNode.setRiskLevel(dstNode.getRiskScore() > 0.7 ? "CRITICAL" : "HIGH");
            }

            // Create or update edge
            String edgeKey = src + "->" + dst;
            TopologyEdgeDto edge = edgeMap.computeIfAbsent(edgeKey, k -> {
                String proto = flow.getProtocol() != null ? flow.getProtocol().name() : "TCP";
                String label = isKali ? "Kali " + flow.getLabel().replace("KALI_", "") + " (Port " + flow.getDstPort() + ")" : (flow.getDstPort() != null ? "Port " + flow.getDstPort() : proto);
                return new TopologyEdgeDto(k, src, dst, proto, 0L, 0L, isAnomaly, label);
            });
            edge.setFlowCount(edge.getFlowCount() + 1);
            edge.setByteCount(edge.getByteCount() + (flow.getTotalFwdBytes() != null ? flow.getTotalFwdBytes() : 0));
            if (isAnomaly) edge.setAnomaly(true);
        }

        state.setNodes(new ArrayList<>(nodeMap.values()));
        state.setEdges(new ArrayList<>(edgeMap.values()));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_flows", flows.size());
        metrics.put("total_bytes", totalBytes);
        metrics.put("active_nodes", nodeMap.size());
        metrics.put("active_edges", edgeMap.size());
        metrics.put("anomaly_flows", anomalyFlows);
        metrics.put("syn_ratio", flows.isEmpty() ? 0.0 : (double) totalSyn / flows.size());
        state.setMetrics(metrics);
        state.setActiveAttackStage(anomalyFlows > 0 ? "Elevated SYN activity (heuristic)" : "No SYN rule match");

        return state;
    }

    public List<Map<String, Object>> getReplayTimeline(Instant start, Instant end, int stepCount) {
        if (end == null) end = Instant.now();
        if (start == null) start = end.minus(24, ChronoUnit.HOURS);

        long durationSeconds = Math.max(1, end.getEpochSecond() - start.getEpochSecond());
        long stepSeconds = Math.max(60, durationSeconds / Math.max(1, stepCount));

        List<Map<String, Object>> timeline = new ArrayList<>();
        Instant cursor = start;
        boolean dbAvailable = true;

        while (cursor.isBefore(end)) {
            Instant stepEnd = cursor.plusSeconds(stepSeconds);
            long flowCount;
            flowCount = flowRepository.countByTimeWindow(cursor, stepEnd);
            timeline.add(Map.of(
                "timestamp", cursor.toString(),
                "time_label", cursor.toString().substring(11, 16),
                "flow_count", flowCount,
                "projected_risk", flowCount > 100 ? 0.75 : flowCount > 20 ? 0.45 : 0.15
            ));
            cursor = stepEnd;
        }

        return timeline;
    }

    public WhatIfScenarioResponse simulateWhatIfScenario(WhatIfScenarioRequest request) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(1, ChronoUnit.HOURS);

        List<TrafficFlow> baselineFlows;
        try {
            baselineFlows = flowRepository.findByTimeWindow(windowStart, now);
            if (baselineFlows.isEmpty() && flowRepository.count() > 0) {
                baselineFlows = flowRepository.findAll(org.springframework.data.domain.PageRequest.of(
                    0, 500, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "flowTimestamp")
                )).getContent();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read scenario traffic", e);
        }
        if (baselineFlows.isEmpty()) {
            throw new IllegalArgumentException("No stored traffic for this what-if scenario");
        }
        Map<String, Double> baselineFeatures = featureExtractor.extractFeatures(baselineFlows);

        // Apply defensive intervention deterministically
        List<TrafficFlow> scenarioFlows = new ArrayList<>();
        String intervention = request.getInterventionType() != null ? request.getInterventionType().toUpperCase() : "ISOLATE_HOST";
        String targetHost = request.getTargetHost();
        Integer targetPort = request.getTargetPort();
        double throttlePct = request.getThrottlePercentage() != null ? Math.min(0.95, Math.max(0.05, request.getThrottlePercentage())) : 0.50;

        for (TrafficFlow flow : baselineFlows) {
            boolean keep = true;
            switch (intervention) {
                case "ISOLATE_HOST" -> {
                    if (targetHost != null && (targetHost.equals(flow.getSrcIp()) || targetHost.equals(flow.getDstIp()))) {
                        keep = false;
                    }
                }
                case "BLOCK_PORT" -> {
                    if (targetPort != null && flow.getDstPort() != null && flow.getDstPort().equals(targetPort)) {
                        keep = false;
                    }
                }
                case "SUPPRESS_SYN" -> {
                    TrafficFlow modified = copyFlow(flow);
                    if (modified.getSynFlagCount() != null && modified.getSynFlagCount() > 0) {
                        modified.setSynFlagCount(0);
                    }
                    scenarioFlows.add(modified);
                    keep = false;
                }
                case "THROTTLE_RATE" -> {
                    TrafficFlow modified = copyFlow(flow);
                    double keepRatio = 1.0 - throttlePct;
                    if (modified.getTotalFwdPackets() != null) {
                        modified.setTotalFwdPackets((long) Math.max(1, Math.round(modified.getTotalFwdPackets() * keepRatio)));
                    }
                    if (modified.getTotalBwdPackets() != null) {
                        modified.setTotalBwdPackets((long) Math.max(0, Math.round(modified.getTotalBwdPackets() * keepRatio)));
                    }
                    if (modified.getTotalFwdBytes() != null) {
                        modified.setTotalFwdBytes((long) Math.max(64, Math.round(modified.getTotalFwdBytes() * keepRatio)));
                    }
                    if (modified.getTotalBwdBytes() != null) {
                        modified.setTotalBwdBytes((long) Math.max(0, Math.round(modified.getTotalBwdBytes() * keepRatio)));
                    }
                    scenarioFlows.add(modified);
                    keep = false;
                }
            }
            if (keep) scenarioFlows.add(flow);
        }

        Map<String, Double> scenarioFeatures = featureExtractor.extractFeatures(scenarioFlows);

        // Calculate baseline vs scenario risk
        double baselineRisk = computeRisk(baselineFeatures);
        double scenarioRisk = computeRisk(scenarioFeatures);
        double riskDelta = Math.max(0.0, baselineRisk - scenarioRisk);
        double riskReduction = baselineRisk > 0.0001 ? (riskDelta / baselineRisk) * 100.0 : 0.0;

        String targetDesc = targetHost != null && !targetHost.isBlank() ? targetHost : (targetPort != null ? "Port " + targetPort : "Global Traffic");

        WhatIfScenarioResponse response = new WhatIfScenarioResponse();
        response.setInterventionType(intervention);
        response.setTarget(targetDesc);
        response.setBaselineRisk(Math.round(baselineRisk * 100.0) / 100.0);
        response.setScenarioRisk(Math.round(scenarioRisk * 100.0) / 100.0);
        response.setRiskDelta(Math.round(riskDelta * 100.0) / 100.0);
        response.setRiskReductionPercent(Math.max(0.0, Math.round(riskReduction * 10.0) / 10.0));
        response.setBaselineStage(baselineRisk > 0.6 ? "High-Risk Attack Trajectory" : "Elevated Activity");
        response.setScenarioStage(scenarioRisk < 0.4 ? "Normal Baseline Contained" : "Reduced Risk Profile");

        // Comparison curves over horizon
        int steps = Math.max(1, request.getHorizonSteps());
        List<Map<String, Object>> curves = new ArrayList<>();
        for (int step = 1; step <= steps; step++) {
            double baseProj = Math.min(1.0, baselineRisk + (0.015 * step));
            double scenProj = Math.min(1.0, scenarioRisk + (0.005 * step));
            curves.add(Map.of(
                "step", step,
                "time_offset", "+" + (step * 5) + "m",
                "baseline_risk", Math.round(baseProj * 100.0) / 100.0,
                "scenario_risk", Math.round(scenProj * 100.0) / 100.0,
                "risk_delta", Math.round((baseProj - scenProj) * 100.0) / 100.0
            ));
        }
        response.setComparisonCurve(curves);

        // Feature changes
        Map<String, Object> diff = new HashMap<>();
        diff.put("flow_count_delta", scenarioFlows.size() - baselineFlows.size());
        diff.put("syn_ratio_before", baselineFeatures.getOrDefault("syn_ratio", 0.0));
        diff.put("syn_ratio_after", scenarioFeatures.getOrDefault("syn_ratio", 0.0));
        diff.put("flow_rate_before", baselineFeatures.getOrDefault("flow_rate", 0.0));
        diff.put("flow_rate_after", scenarioFeatures.getOrDefault("flow_rate", 0.0));
        response.setFeatureChanges(diff);

        response.setExplanation(String.format(
            "Simulated counterfactual [%s] targeting [%s]: projected attack probability shifts from %.1f%% to %.1f%% (delta: -%.1f%%, relative risk reduction: %.1f%%).",
            intervention, targetDesc, baselineRisk * 100.0, scenarioRisk * 100.0, riskDelta * 100.0, Math.max(0.0, riskReduction)
        ));

        // Persist to PostgreSQL whatif_scenarios table if available
        if (whatIfScenarioRepository != null) {
            try {
                WhatIfScenario entity = new WhatIfScenario();
                entity.setInterventionType(intervention);
                entity.setTargetHost(targetHost);
                entity.setTargetPort(targetPort);
                entity.setBaselineRisk(baselineRisk);
                entity.setScenarioRisk(scenarioRisk);
                entity.setRiskReductionPercent(Math.max(0.0, riskReduction));
                entity.setExplanation(response.getExplanation());
                whatIfScenarioRepository.save(entity);
            } catch (Exception e) {
                log.warn("[NETRA STANDBY] Scenario persistence deferred: {}", e.getMessage());
            }
        }

        return response;
    }

    private double computeRisk(Map<String, Double> features) {
        double syn = features.getOrDefault("syn_ratio", 0.0);
        double ports = features.getOrDefault("unique_dst_ports", 0.0);
        double flowRate = Math.min(1.0, features.getOrDefault("flow_rate", 0.0) / 20.0);
        return Math.min(1.0, (syn * 0.4) + (ports * 0.3) + (flowRate * 0.3));
    }

    private TrafficFlow copyFlow(TrafficFlow orig) {
        TrafficFlow copy = new TrafficFlow();
        copy.setSrcIp(orig.getSrcIp());
        copy.setDstIp(orig.getDstIp());
        copy.setSrcPort(orig.getSrcPort());
        copy.setDstPort(orig.getDstPort());
        copy.setProtocol(orig.getProtocol());
        copy.setFlowTimestamp(orig.getFlowTimestamp());
        copy.setTotalFwdPackets(orig.getTotalFwdPackets());
        copy.setTotalBwdPackets(orig.getTotalBwdPackets());
        copy.setTotalFwdBytes(orig.getTotalFwdBytes());
        copy.setTotalBwdBytes(orig.getTotalBwdBytes());
        copy.setSynFlagCount(orig.getSynFlagCount());
        copy.setAckFlagCount(orig.getAckFlagCount());
        copy.setLabel(orig.getLabel());
        return copy;
    }

    private TopologyNodeDto createNode(String ip) {
        return new TopologyNodeDto(ip, ip, 0.1, "LOW", true, 0, classifyRole(ip));
    }

    private void addDemoNode(Map<String, TopologyNodeDto> map, String ip, String label, double risk, String level, String role) {
        map.put(ip, new TopologyNodeDto(ip, label, risk, level, true, 25, role));
    }

    private String classifyRole(String ip) {
        if (ip.endsWith(".1")) return "GATEWAY";
        if (ip.startsWith("10.0.0.") && (ip.endsWith(".5") || ip.endsWith(".15") || ip.endsWith(".22"))) return "SERVER";
        if (ip.startsWith("192.168.")) return "WORKSTATION";
        if (!ip.startsWith("10.") && !ip.startsWith("192.168.")) return "EXTERNAL";
        return "SERVER";
    }

    private List<TrafficFlow> generateStandbyBaselineFlows(Instant start, Instant end) {
        List<TrafficFlow> flows = new ArrayList<>();
        String attacker = "198.51.100.88";
        String victim = "10.0.0.15";
        for (int i = 0; i < 50; i++) {
            TrafficFlow f = new TrafficFlow();
            f.setSrcIp(attacker);
            f.setDstIp(victim);
            f.setSrcPort(35000 + i);
            f.setDstPort(80);
            f.setProtocol(com.netra.domain.enums.Protocol.TCP);
            f.setFlowTimestamp(start.plusSeconds(i * 30));
            f.setDurationMs(25L);
            f.setTotalFwdPackets(10L);
            f.setTotalBwdPackets(0L);
            f.setTotalFwdBytes(600L);
            f.setTotalBwdBytes(0L);
            f.setSynFlagCount(10);
            f.setAckFlagCount(0);
            f.setLabel("DoS-SynFlood");
            flows.add(f);
        }
        return flows;
    }
}
