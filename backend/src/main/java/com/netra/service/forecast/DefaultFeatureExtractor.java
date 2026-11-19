package com.netra.service.forecast;

import com.netra.domain.entity.TrafficFlow;
import com.netra.service.TrafficFeatureExtractor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default feature extractor — transforms raw flows into numerical feature vectors.
 * Produces features compatible with CIC-IDS datasets and the deterministic baseline.
 * 
 * Demonstrates: Streams API, Collections, Generics, Statistical computation.
 */
@Component
public class DefaultFeatureExtractor implements TrafficFeatureExtractor {

    private static final List<String> FEATURE_NAMES = List.of(
        "flow_count", "flow_rate", "unique_src_ips", "unique_dst_ips",
        "unique_dst_ports", "syn_ratio", "ack_ratio", "rst_ratio",
        "avg_packet_size_anomaly", "iat_variance", "fwd_bwd_imbalance",
        "avg_duration_ms", "total_bytes", "packet_rate", "risk_trend"
    );

    @Override
    public Map<String, Double> extractFeatures(List<TrafficFlow> flows) {
        Map<String, Double> features = new LinkedHashMap<>();

        if (flows.isEmpty()) {
            FEATURE_NAMES.forEach(name -> features.put(name, 0.0));
            return features;
        }

        long flowCount = flows.size();
        features.put("flow_count", (double) flowCount);

        // Time span
        var timestamps = flows.stream()
            .map(TrafficFlow::getFlowTimestamp)
            .filter(Objects::nonNull)
            .sorted()
            .toList();

        double timeSpanSeconds = timestamps.size() >= 2
            ? (timestamps.getLast().toEpochMilli() - timestamps.getFirst().toEpochMilli()) / 1000.0
            : 1.0;
        features.put("flow_rate", flowCount / Math.max(timeSpanSeconds, 1.0));

        // Unique IPs
        Set<String> srcIps = flows.stream().map(TrafficFlow::getSrcIp).collect(Collectors.toSet());
        Set<String> dstIps = flows.stream().map(TrafficFlow::getDstIp).collect(Collectors.toSet());
        features.put("unique_src_ips", normalize(srcIps.size(), flowCount));
        features.put("unique_dst_ips", normalize(dstIps.size(), flowCount));

        // Port diversity
        Set<Integer> dstPorts = flows.stream()
            .map(TrafficFlow::getDstPort)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        features.put("unique_dst_ports", normalize(dstPorts.size(), 1000));

        // TCP flag ratios
        long totalSyn = flows.stream().mapToLong(f -> safe(f.getSynFlagCount())).sum();
        long totalAck = flows.stream().mapToLong(f -> safe(f.getAckFlagCount())).sum();
        long totalRst = flows.stream().mapToLong(f -> safe(f.getRstFlagCount())).sum();
        long totalFlags = totalSyn + totalAck + totalRst + 1;
        features.put("syn_ratio", totalSyn / (double) totalFlags);
        features.put("ack_ratio", totalAck / (double) totalFlags);
        features.put("rst_ratio", totalRst / (double) totalFlags);

        // Packet size
        double avgPktSize = flows.stream()
            .map(TrafficFlow::getAvgPacketSize)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.0);
        features.put("avg_packet_size_anomaly", avgPktSize > 1400 || avgPktSize < 40 ? 1.0 : 0.0);

        // Inter-arrival time variance
        double iatVariance = flows.stream()
            .map(TrafficFlow::getFlowIatStd)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.0);
        features.put("iat_variance", normalize(iatVariance, 100000));

        // Forward/backward imbalance
        long totalFwd = flows.stream().mapToLong(f -> safe(f.getTotalFwdBytes())).sum();
        long totalBwd = flows.stream().mapToLong(f -> safe(f.getTotalBwdBytes())).sum();
        double total = totalFwd + totalBwd + 1;
        features.put("fwd_bwd_imbalance", Math.abs(totalFwd - totalBwd) / total);

        // Duration
        double avgDuration = flows.stream()
            .map(TrafficFlow::getDurationMs)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .average().orElse(0.0);
        features.put("avg_duration_ms", avgDuration);

        // Total bytes
        features.put("total_bytes", (double) (totalFwd + totalBwd));

        // Packet rate (packets per second) — label-free telemetry feature
        long totalPackets = flows.stream()
            .mapToLong(f -> safe(f.getTotalFwdPackets()) + safe(f.getTotalBwdPackets()))
            .sum();
        features.put("packet_rate", totalPackets / Math.max(timeSpanSeconds, 1.0));

        // Statistical trend — label-free rate of change in SYN traffic
        if (flows.size() >= 4) {
            int half = flows.size() / 2;
            long syn1 = flows.subList(0, half).stream().mapToLong(f -> safe(f.getSynFlagCount())).sum();
            long syn2 = flows.subList(half, flows.size()).stream().mapToLong(f -> safe(f.getSynFlagCount())).sum();
            features.put("risk_trend", (double) (syn2 - syn1) / Math.max(half, 1));
        } else {
            features.put("risk_trend", features.get("syn_ratio") > 0.3 ? 0.1 : -0.05);
        }

        return features;
    }

    @Override
    public List<String> getFeatureNames() { return FEATURE_NAMES; }

    @Override
    public String getName() { return "default-feature-extractor"; }

    private double normalize(double value, double max) {
        return Math.min(value / Math.max(max, 1.0), 1.0);
    }

    private long safe(Number n) { return n != null ? n.longValue() : 0L; }
}
