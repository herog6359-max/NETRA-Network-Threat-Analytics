package com.netra.service.forecast;

import com.netra.domain.entity.FeatureAttribution;
import com.netra.domain.entity.ForecastHorizon;
import com.netra.domain.entity.ForecastRun;
import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.Severity;
import com.netra.repository.FeatureAttributionRepository;
import com.netra.repository.ForecastHorizonRepository;
import com.netra.repository.ForecastRunRepository;
import com.netra.repository.TrafficFlowRepository;
import com.netra.service.NetworkForecastEngine;
import com.netra.service.TrafficFeatureExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Model A — Deterministic Baseline Forecast Engine.
 * 
 * Uses transparent, configurable features and historical windows.
 * Deterministic for fixed input and configuration.
 * 
 * Demonstrates:
 * - Interface implementation (NetworkForecastEngine)
 * - Dependency injection (constructor injection)
 * - Transaction management
 * - Collections and Streams API
 * - Encapsulation of forecasting logic
 */
@Service
public class DeterministicForecastEngine implements NetworkForecastEngine {

    private final TrafficFlowRepository flowRepository;
    private final ForecastRunRepository forecastRunRepository;
    private final ForecastHorizonRepository horizonRepository;
    private final FeatureAttributionRepository attributionRepository;
    private final TrafficFeatureExtractor featureExtractor;

    public DeterministicForecastEngine(
            TrafficFlowRepository flowRepository,
            ForecastRunRepository forecastRunRepository,
            ForecastHorizonRepository horizonRepository,
            FeatureAttributionRepository attributionRepository,
            TrafficFeatureExtractor featureExtractor) {
        this.flowRepository = flowRepository;
        this.forecastRunRepository = forecastRunRepository;
        this.horizonRepository = horizonRepository;
        this.attributionRepository = attributionRepository;
        this.featureExtractor = featureExtractor;
    }

    @Override
    public ForecastRun forecast(Instant windowStart, Instant windowEnd, int horizonSteps, Map<String, Object> config) {
        long startTime = System.currentTimeMillis();

        // 1. Retrieve flows in window
        List<TrafficFlow> flows;
        try {
            flows = flowRepository.findByTimeWindow(windowStart, windowEnd);
            if (flows.isEmpty() && flowRepository.count() > 0) {
                flows = flowRepository.findAll(org.springframework.data.domain.PageRequest.of(
                    0, 500, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "flowTimestamp")
                )).getContent();
            }
        } catch (Exception e) {
            flows = Collections.emptyList();
        }

        flows = flows.stream().filter(f -> !"LIVE_HTTP_PROBE".equals(f.getLabel()))
            .sorted(Comparator.comparing(TrafficFlow::getFlowTimestamp)).toList();
        if (flows.isEmpty()) throw new IllegalArgumentException("No flow-feature data. Upload a CSV; HTTP request observations are not packet captures.");
        windowStart = flows.getFirst().getFlowTimestamp();
        windowEnd = flows.getLast().getFlowTimestamp();
        // 2. Extract features
        Map<String, Double> features = featureExtractor.extractFeatures(flows);

        // 3. Compute deterministic risk score
        double riskScore = computeRiskScore(features, flows);

        // 4. Generate multi-step forecast calculations
        double maxForecastedRisk = riskScore;
        List<Double> stepRisks = new ArrayList<>();
        for (int step = 1; step <= horizonSteps; step++) {
            double stepRisk = projectRisk(riskScore, step, features);
            stepRisks.add(stepRisk);
            maxForecastedRisk = Math.max(maxForecastedRisk, stepRisk);
        }

        // 5. Determine severity and stage
        Severity severity = riskToSeverity(maxForecastedRisk);
        String stage = estimateAttackStage(features, flows);

        // 6. Compute data quality
        double dataQuality = computeDataQuality(flows, features);

        // 7. Collect unique hosts
        Set<String> uniqueHosts = new HashSet<>();
        flows.forEach(f -> { uniqueHosts.add(f.getSrcIp()); uniqueHosts.add(f.getDstIp()); });

        // 8. Build and persist forecast run
        ForecastRun run = new ForecastRun();
        run.setModelName(getEngineName());
        run.setModelVersion(getEngineVersion());
        run.setWindowStart(windowStart);
        run.setWindowEnd(windowEnd);
        run.setHorizonSteps(horizonSteps);
        run.setCurrentRiskScore(riskScore);
        run.setMaxForecastedRisk(maxForecastedRisk);
        run.setRiskLevel(severity);
        run.setPredictedStage(stage);
        run.setConfidence(computeConfidence(flows.size(), dataQuality));
        run.setDataQualityScore(dataQuality);
        run.setFlowCountInWindow((long) flows.size());
        run.setHostCountInWindow(uniqueHosts.size());
        run.setComputationTimeMs(System.currentTimeMillis() - startTime);
        run.setDemo(flows.isEmpty());

        ForecastRun savedRun;
        boolean dbAvailable = true;
        try {
            savedRun = forecastRunRepository.save(run);
        } catch (Exception e) {
            throw new IllegalStateException("Forecast could not be saved", e);
        }

        // 9. Persist multi-step forecast horizons
        for (int step = 1; step <= horizonSteps; step++) {
            double sRisk = stepRisks.get(step - 1);
            ForecastHorizon horizon = new ForecastHorizon();
            horizon.setForecastRun(savedRun);
            horizon.setStepNumber(step);
            horizon.setProjectedTimestamp(windowEnd.plus((long) step * 5, ChronoUnit.MINUTES));
            horizon.setPredictedRiskScore(sRisk);
            horizon.setPredictedAttackProbability(Math.min(1.0, sRisk * 1.05));
            horizon.setPredictedTrafficVolume(Math.max(10.0, flows.size() * (1.0 + (step * 0.05))));
            horizon.setConfidenceLower(Math.max(0.0, sRisk - 0.12));
            horizon.setConfidenceUpper(Math.min(1.0, sRisk + 0.12));
            if (dbAvailable) {
                try {
                    horizonRepository.save(horizon);
                } catch (Exception e) { throw new IllegalStateException("Forecast details could not be saved", e); }
            }
        }

        // 10. Persist explainable AI feature attributions
        Map<String, String> featureDesc = Map.of(
            "syn_ratio", "Elevated TCP SYN without ACK indicating handshake exhaustion",
            "unique_dst_ports", "Dispersed port targeting indicating reconnaissance scanning",
            "flow_rate", "Abnormal flow creation velocity per time quantum",
            "avg_packet_size_anomaly", "Packet size distribution divergence from benign baseline",
            "iat_variance", "Inter-arrival time variance indicating automated packet generation",
            "fwd_bwd_imbalance", "Severe asymmetry between forward and backward payload transfer",
            "unique_dst_ips", "Fan-out across multiple network host destinations",
            "attack_label_ratio", "Historical or signature-matched threat telemetry ratio"
        );

        for (Map.Entry<String, Double> entry : features.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0.05) {
                String desc = featureDesc.getOrDefault(entry.getKey(), "Statistical anomaly in network telemetry feature");
                FeatureAttribution attr = new FeatureAttribution(savedRun, entry.getKey(), Math.min(1.0, Math.max(0.0, entry.getValue())), "Heuristic indicator (not model attribution): " + desc);
                if (dbAvailable) {
                    try {
                        attributionRepository.save(attr);
                    } catch (Exception ignored) {}
                }
            }
        }

        return savedRun;
    }

    /**
     * Computes a deterministic risk score from extracted features.
     * Uses weighted feature contributions — transparent and reproducible.
     */
    private double computeRiskScore(Map<String, Double> features, List<TrafficFlow> flows) {
        if (flows.isEmpty()) return 0.0;

        double score = 0.0;
        double totalWeight = 0.0;

        // Weighted feature contributions — 100% label-free telemetry features
        Map<String, Double> weights = Map.of(
            "syn_ratio", 0.20,
            "unique_dst_ports", 0.15,
            "flow_rate", 0.15,
            "avg_packet_size_anomaly", 0.10,
            "iat_variance", 0.10,
            "fwd_bwd_imbalance", 0.10,
            "unique_dst_ips", 0.10,
            "packet_rate", 0.10
        );

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            Double featureVal = features.getOrDefault(entry.getKey(), 0.0);
            score += entry.getValue() * Math.min(featureVal, 1.0);
            totalWeight += entry.getValue();
        }

        return totalWeight > 0 ? Math.min(score / totalWeight, 1.0) : 0.0;
    }

    /** Projects risk forward by decaying or amplifying based on trend features */
    private double projectRisk(double currentRisk, int step, Map<String, Double> features) {
        double trend = Math.max(-1.0, Math.min(1.0, features.getOrDefault("risk_trend", 0.0)));
        double projected = currentRisk + (trend * step * 0.1);
        return Math.max(0.0, Math.min(1.0, projected));
    }

    private Severity riskToSeverity(double risk) {
        if (risk >= 0.85) return Severity.CRITICAL;
        if (risk >= 0.65) return Severity.HIGH;
        if (risk >= 0.40) return Severity.MEDIUM;
        if (risk >= 0.20) return Severity.LOW;
        return Severity.INFO;
    }

    private String estimateAttackStage(Map<String, Double> features, List<TrafficFlow> flows) {
        double synRatio = features.getOrDefault("syn_ratio", 0.0);
        double portDiversity = features.getOrDefault("unique_dst_ports", 0.0);
        double pktRate = features.getOrDefault("packet_rate", 0.0);
        double flowRate = features.getOrDefault("flow_rate", 0.0);

        if (pktRate > 50.0 && synRatio > 0.5) return "Active Exploitation";
        if (portDiversity > 0.7) return "Reconnaissance";
        if (synRatio > 0.3) return "Initial Access Attempt";
        if (flowRate > 20.0) return "Suspicious Activity";
        return "Normal Operations";
    }

    private double computeDataQuality(List<TrafficFlow> flows, Map<String, Double> features) {
        if (flows.isEmpty()) return 0.0;
        double completeness = features.values().stream()
            .filter(v -> v != null && !v.isNaN())
            .count() / (double) Math.max(features.size(), 1);
        return Math.min(completeness, 1.0);
    }

    private double computeConfidence(int flowCount, double dataQuality) {
        double volumeConfidence = Math.min(flowCount / 1000.0, 1.0);
        return (volumeConfidence * 0.5 + dataQuality * 0.5);
    }

    @Override
    public String getEngineName() { return "deterministic-baseline"; }

    @Override
    public String getEngineVersion() { return "1.0.0"; }

    @Override
    public boolean isAvailable() { return true; } // Always available — no external deps
}
